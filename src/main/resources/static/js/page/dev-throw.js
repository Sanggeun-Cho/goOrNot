/**
 * page/dev-throw.js
 * 던지기 흐름 점검용 임시 화면. 제출 전에 삭제한다.
 *
 * 실제 서비스 화면이 아니므로 연출은 넣지 않는다.
 * API 호출 순서와 응답, 그리고 "거부되어야 할 요청이 실제로 거부되는지" 만 본다.
 */

import { initHeader } from '../auth.js';
import { apiFetch } from '../api.js';
import { deviceStore } from '../device.js';
import { toast, bindSubmit, setText } from '../ui.js';

const $ = (selector) => document.querySelector(selector);

const roundsBox = $('[data-list="rounds"]');
const regionsBox = $('[data-list="regions"]');
const bypassBox = $('[data-field="bypass"]');
const placesBox = $('[data-list="places"]');
const arrangeBox = $('[data-field="arrange"]');
const cardsBox = $('[data-list="cards"]');
const rerollBox = $('[data-field="reroll"]');

/** 화면이 들고 있는 상태. 서버가 진실이고 이건 표시용 사본이다 */
const state = {
    sessionId: null,
    drawn: null,      // 마지막 추첨 결과 { regionCode, regionName, lat, lng }
    category: null,   // 마지막으로 카드를 받은 카테고리
};

/* ── 호출 ────────────────────────────────────────────── */

/**
 * 상태 코드와 원본 응답까지 돌려주는 호출.
 * 우회 시도는 "실패하는 것" 이 정답이라 예외로 던지지 않고 결과를 그대로 본다.
 */
async function call(path, { method = 'GET', body, query } = {}) {
    const response = await apiFetch(path, { method, body, query });
    const raw = await response.text();

    let parsed = null;

    try {
        parsed = raw ? JSON.parse(raw) : null;
    } catch {
        parsed = null;
    }

    const result = { ok: response.ok, status: response.status, raw, body: parsed };

    renderRaw(`${method} ${path}`, result);

    return result;
}

/** 성공을 기대하는 호출. 실패하면 토스트를 띄우고 null 을 돌려준다 */
async function expectOk(path, options, failMessage) {
    const result = await call(path, options);

    if (!result.ok) {
        toast(`${failMessage} (${result.status}) ${messageOf(result)}`, 'error');

        return null;
    }

    return result.body;
}

function messageOf(result) {
    if (result.body && typeof result.body === 'object' && typeof result.body.error === 'string') {
        return result.body.error;
    }

    return result.raw || '';
}

/* ── 세션 ────────────────────────────────────────────── */

async function createRandomSession() {
    const created = await expectOk('/api/throw-session', {
        method: 'POST',
        body: { source: 'RANDOM', totalCount: 1 },
    }, '세션 생성 실패');

    if (!created) return;

    state.sessionId = created.id;
    state.drawn = null;

    setText(document, 'drawn', '아직 던지지 않았습니다');
    mark('create', true);
    toast('세션을 만들었습니다.', 'success');

    await reloadSession();
}

async function reloadSession() {
    if (!state.sessionId) {
        toast('먼저 세션을 만들어 주세요.', 'error');

        return;
    }

    const session = await expectOk('/api/throw-session', {
        query: { id: state.sessionId },
    }, '세션 조회 실패');

    if (!session) return;

    setText(document, 'sessionId', String(session.id));
    setText(document, 'status', session.status);
    setText(document, 'region', session.regionName
        ? `${session.regionName} (${session.regionCode}) ${session.lat}, ${session.lng}`
        : '미확정');

    mark('confirm', session.status === 'CONFIRMED');

    await reloadRounds();
}

async function reloadRounds() {
    const rounds = await expectOk('/api/throw-round/list', {
        query: { throwSessionId: state.sessionId },
    }, '회차 조회 실패');

    if (!rounds) return;

    roundsBox.innerHTML = '';

    rounds.forEach((round) => {
        const item = document.createElement('div');
        item.className = 'item item--compact';
        item.innerHTML = `
            <div class="item__body">
                <div class="item__title">${round.roundNo}회차 · ${round.regionCode ?? '-'}</div>
                <div class="item__sub">${round.lat ?? '-'}, ${round.lng ?? '-'}</div>
            </div>
            <span class="badge ${round.choice === 'GO' ? 'badge--ok' : ''}">${round.choice}</span>
        `;
        roundsBox.appendChild(item);
    });
}

/* ── 던지기 ──────────────────────────────────────────── */

async function draw() {
    if (!state.sessionId) {
        toast('먼저 세션을 만들어 주세요.', 'error');

        return;
    }

    const previous = state.drawn?.regionCode;

    const drawn = await expectOk('/api/throw-round/draw', {
        method: 'POST',
        body: { id: state.sessionId },
    }, '던지기 실패');

    if (!drawn) return;

    state.drawn = drawn;

    setText(document, 'drawn', `${drawn.regionName} (${drawn.regionCode}) ${drawn.lat}, ${drawn.lng}`);
    mark('draw', true);

    // 같은 지역이 연속으로 나오면 제외 로직을 의심해봐야 한다.
    // 단, 아직 회차로 기록하지 않은 추첨은 제외 목록에 없으므로 같은 값이 나올 수도 있다
    if (previous && previous === drawn.regionCode) {
        toast('직전과 같은 지역이 나왔습니다. 기록 전이라면 정상입니다.', 'info');
    }
}

/** 갈래/말래 공통. 회차를 기록한다 */
async function record(choice) {
    if (!state.drawn) {
        toast('먼저 던져 주세요.', 'error');

        return null;
    }

    const created = await expectOk('/api/throw-round', {
        method: 'POST',
        body: {
            throwSessionId: state.sessionId,
            regionCode: state.drawn.regionCode,
            lat: state.drawn.lat,
            lng: state.drawn.lng,
            choice,
        },
    }, '회차 기록 실패');

    return created;
}

async function again() {
    const recorded = await record('AGAIN');
    if (!recorded) return;

    const excluded = state.drawn.regionCode;

    state.drawn = null;
    await draw();

    // 방금 "말래" 한 지역이 다시 나오면 제외가 동작하지 않은 것이다
    mark('again', Boolean(state.drawn) && state.drawn.regionCode !== excluded);
    await reloadRounds();
}

async function go() {
    const recorded = await record('GO');
    if (!recorded) return;

    const confirmed = await expectOk('/api/throw-session', {
        method: 'PUT',
        body: {
            id: state.sessionId,
            status: 'CONFIRMED',
            regionCode: state.drawn.regionCode,
        },
    }, '세션 확정 실패');

    if (confirmed === null) return;

    toast('세션을 확정했습니다.', 'success');
    await reloadSession();
}

/* ── 우회 시도 ───────────────────────────────────────── */

/**
 * 거부되어야 하는 요청을 보낸다.
 * 성공(2xx)하면 방어가 뚫린 것이라 실패로 표시한다.
 */
async function expectRejected(label, path, options) {
    if (!state.sessionId) {
        toast('먼저 세션을 만들어 주세요.', 'error');

        return;
    }

    const result = await call(path, options);

    const blocked = !result.ok;

    bypassBox.dataset.state = blocked ? 'pass' : 'fail';
    bypassBox.textContent = blocked
        ? `차단됨 ✓ ${label} → ${result.status} ${messageOf(result)}`
        : `뚫림 ✗ ${label} → ${result.status} 요청이 그대로 통과했습니다`;

    mark('bypass', blocked);
}

const bypass = {
    /** 한 번도 던지지 않고 원하는 지역으로 확정 */
    confirm: () => expectRejected('던지지 않고 강남 확정', '/api/throw-session', {
        method: 'PUT',
        body: { id: state.sessionId, status: 'CONFIRMED', regionCode: '11680' },
    }),

    /** 던지긴 했는데 다른 지역 코드를 보냄 */
    round: () => expectRejected('추첨 결과와 다른 지역 기록', '/api/throw-round', {
        method: 'POST',
        body: { throwSessionId: state.sessionId, regionCode: '11680', choice: 'GO' },
    }),

    /** 같은 추첨 티켓으로 두 번 기록 (1회용인지 확인) */
    replay: async () => {
        if (!state.drawn) {
            toast('먼저 던져 주세요.', 'error');

            return;
        }

        const body = {
            throwSessionId: state.sessionId,
            regionCode: state.drawn.regionCode,
            lat: state.drawn.lat,
            lng: state.drawn.lng,
            choice: 'AGAIN',
        };

        // 첫 번째는 통과가 정상이다. 두 번째가 막혀야 한다
        await call('/api/throw-round', { method: 'POST', body });
        await expectRejected('같은 추첨 결과 재사용', '/api/throw-round', { method: 'POST', body });

        state.drawn = null;
        setText(document, 'drawn', '아직 던지지 않았습니다');
        await reloadRounds();
    },

    /** 코드는 맞추고 좌표만 바꿔치기 — 거부가 아니라 "덮어쓰기" 로 막는다 */
    coords: async () => {
        if (!state.drawn) {
            toast('먼저 던져 주세요.', 'error');

            return;
        }

        const expected = state.drawn;

        const created = await expectOk('/api/throw-round', {
            method: 'POST',
            body: {
                throwSessionId: state.sessionId,
                regionCode: expected.regionCode,
                lat: 0,
                lng: 0,
                choice: 'AGAIN',
            },
        }, '회차 기록 실패');

        state.drawn = null;
        setText(document, 'drawn', '아직 던지지 않았습니다');

        if (!created) return;

        const round = await expectOk('/api/throw-round', { query: { id: created.id } }, '회차 조회 실패');
        if (!round) return;

        const overwritten = round.lat === expected.lat && round.lng === expected.lng;

        bypassBox.dataset.state = overwritten ? 'pass' : 'fail';
        bypassBox.textContent = overwritten
            ? `덮어씀 ✓ 좌표 바꿔치기 → 요청은 통과했지만 서버가 ${round.lat}, ${round.lng} 로 되돌렸습니다`
            : `뚫림 ✗ 좌표 바꿔치기 → ${round.lat}, ${round.lng} 가 그대로 저장됐습니다`;

        mark('bypass', overwritten);
        await reloadRounds();
    },
};

/* ── 지역 검색 ───────────────────────────────────────── */

async function search({ keyword }) {
    const regions = await expectOk('/api/region/list', { query: { keyword } }, '지역 검색 실패');

    regionsBox.innerHTML = '';

    if (!regions) return;

    mark('search', regions.length > 0);

    if (regions.length === 0) {
        toast('일치하는 지역이 없습니다.', 'info');

        return;
    }

    regions.forEach((region) => {
        const row = document.createElement('div');
        row.className = 'code';
        row.innerHTML = `
            <span class="code__value">${region.code}</span>
            <span class="code__name">${region.name}</span>
            <button class="btn btn--text" type="button">이 지역으로 시작</button>
        `;
        row.querySelector('button').addEventListener('click', () => createSearchSession(region));
        regionsBox.appendChild(row);
    });
}

async function createSearchSession(region) {
    const created = await expectOk('/api/throw-session', {
        method: 'POST',
        body: { source: 'SEARCH', regionCode: region.code },
    }, 'SEARCH 세션 생성 실패');

    if (!created) return;

    state.sessionId = created.id;
    state.drawn = null;

    setText(document, 'drawn', 'SEARCH 세션은 던지지 않습니다');
    toast(`${region.name} 로 세션을 만들었습니다.`, 'success');

    await reloadSession();
}

/* ── 주변 장소 ───────────────────────────────────────── */

/**
 * 현재 세션의 확정 지역 코드. 확정 전이면 null.
 *
 * 화면에 보이는 값을 쓰지 않고 매번 서버에 다시 묻는다.
 * 확정은 다른 탭이나 앞선 우회 시도로도 바뀔 수 있어서, 표시용 사본을 믿으면
 * 엉뚱한 지역으로 TourAPI 를 호출하고 한도만 쓴다.
 */
async function confirmedRegionCode() {
    if (!state.sessionId) {
        toast('먼저 세션을 만들어 주세요.', 'error');

        return null;
    }

    const session = await expectOk('/api/throw-session', {
        query: { id: state.sessionId },
    }, '세션 조회 실패');

    if (!session) return null;

    if (!session.regionCode) {
        toast('지역이 확정된 세션이 아닙니다. "갈래" 로 확정하거나 검색으로 만들어 주세요.', 'error');

        return null;
    }

    return session.regionCode;
}

/**
 * 확정 지역 주변 장소 조회.
 *
 * 이 화면에서 유일하게 TourAPI 일일 한도를 쓰는 호출이라 버튼을 눌렀을 때만 나간다.
 * 좌표는 보내지 않는다. 지역 코드만 넘기고 서버가 카탈로그 좌표로 조회한다.
 */
async function places() {
    const regionCode = await confirmedRegionCode();

    if (!regionCode) return;

    const result = await expectOk('/api/place/list', {
        query: { regionCode },
    }, '장소 조회 실패 (로그인 상태를 확인하세요)');

    if (!result) {
        mark('places', false);

        return;
    }

    setText(document, 'placeRegion', `${result.regionName} (${result.regionCode}) ${result.lat}, ${result.lng}`);
    setText(document, 'placeMeta', `${result.radius}m / ${result.totalCount ?? 0}건`);

    renderPlaces(result.places ?? []);
    mark('places', (result.places ?? []).length > 0);
}

/**
 * 장소 카드 렌더링.
 *
 * innerHTML 을 쓰지 않는다. 장소명·주소는 TourAPI 가 준 외부 문자열이라
 * 따옴표나 태그가 섞여 들어오면 그대로 실행될 수 있다.
 */
function renderPlaces(list) {
    placesBox.innerHTML = '';

    list.forEach((place) => {
        const item = document.createElement('div');
        item.className = 'item item--compact';

        const body = document.createElement('div');
        body.className = 'item__body';

        const title = document.createElement('div');
        title.className = 'item__title';
        title.textContent = place.placeName ?? '-';

        const sub = document.createElement('div');
        sub.className = 'item__sub';
        sub.textContent = [place.address, place.distance == null ? null : `${place.distance}m`]
            .filter(Boolean).join(' · ') || '-';

        body.append(title, sub);

        const badge = document.createElement('span');
        badge.className = 'badge';
        badge.textContent = place.contentTypeId ?? '-';

        item.append(body, badge);
        placesBox.appendChild(item);
    });
}

/* ── 정렬값 확인 ─────────────────────────────────────── */

/**
 * arrange 값이 위치기반 조회에서 받아들여지는지 확인.
 *
 * 카드 후보는 S(거리순 + 대표이미지 보장)로 뽑는데, 이 코드가 위치기반 조회에서
 * 유효한지 매뉴얼만으로는 확정할 수 없었다. 카드 기능 전체를 돌려보고 실패하면
 * 정렬값 문제인지 카드 로직 문제인지 구분이 안 되므로 여기서 따로 떼어 본다.
 */
async function checkArrange(arrange) {
    const regionCode = await confirmedRegionCode();

    if (!regionCode) return;

    const result = await call('/api/dev/place/list', {
        query: { regionCode, arrange, numOfRows: 5 },
    });

    const ok = result.ok;

    arrangeBox.dataset.state = ok ? 'pass' : 'fail';
    arrangeBox.textContent = ok
        ? `arrange=${arrange} 통과 ✓ ${(result.body?.places ?? []).length}건`
        : `arrange=${arrange} 거부 ✗ ${result.status} ${messageOf(result)}`;

    if (arrange === 'S') mark('arrange', ok);

    if (!ok) {
        toast(`arrange=${arrange} 가 거부됐습니다. 응답 내용을 확인해 주세요.`, 'error');
    }
}

/* ── 카테고리 카드 ───────────────────────────────────── */

/**
 * 카드 세트 받기.
 *
 * 같은 카테고리를 다시 눌러도 카드가 그대로여야 정상이다.
 * 매번 새로 깔리면 리롤 1회 제한이 "카테고리를 다시 누른다" 로 무력화된다.
 */
async function dealCards(category) {
    if (!state.sessionId) {
        toast('먼저 세션을 만들어 주세요.', 'error');

        return;
    }

    const set = await expectOk('/api/card/deal', {
        method: 'POST',
        body: { throwSessionId: state.sessionId, category },
    }, '카드 조회 실패');

    if (!set) {
        mark('cards', false);

        return;
    }

    state.category = category;
    renderCardSet(set);
    mark('cards', (set.cards ?? []).length > 0);
}

function renderCardSet(set) {
    setText(document, 'cardCategory', `${set.categoryLabel} (${set.category}) · ${set.regionName ?? '-'}`);
    setText(document, 'cardMeta', `${set.poolSize ?? 0}곳 / ${set.totalCount ?? 0}건 / ${set.expiresInSeconds ?? 0}초`);

    cardsBox.innerHTML = '';

    (set.cards ?? []).forEach((card) => {
        cardsBox.appendChild(renderCard(card));
    });
}

/**
 * 카드 한 장.
 * 장소명·주소는 TourAPI 가 준 외부 문자열이라 innerHTML 을 쓰지 않는다.
 */
function renderCard(card) {
    const place = card.place ?? {};

    const item = document.createElement('div');
    item.className = 'item item--compact';

    const body = document.createElement('div');
    body.className = 'item__body';

    const title = document.createElement('div');
    title.className = 'item__title';
    title.textContent = `[${card.slot}] ${place.placeName ?? '-'}`;

    const sub = document.createElement('div');
    sub.className = 'item__sub';
    sub.textContent = [place.address, place.distance == null ? null : `${place.distance}m`]
        .filter(Boolean).join(' · ') || '-';

    body.append(title, sub);

    const button = document.createElement('button');
    button.className = 'btn btn--text';
    button.type = 'button';
    button.textContent = card.rerollable ? '리롤' : '리롤 불가';
    button.disabled = !card.rerollable;
    button.addEventListener('click', () => rerollCard(card.slot));

    item.append(body, button);

    return item;
}

async function rerollCard(slot) {
    const set = await expectOk('/api/card/reroll', {
        method: 'POST',
        body: { throwSessionId: state.sessionId, category: state.category, slot },
    }, '리롤 실패');

    if (!set) return;

    renderCardSet(set);
}

/** 같은 슬롯을 연달아 두 번. 첫 번째는 통과, 두 번째는 거부가 정상이다 */
async function rerollTwice() {
    if (!state.category) {
        toast('먼저 카테고리를 골라 카드를 받아 주세요.', 'error');

        return;
    }

    const body = { throwSessionId: state.sessionId, category: state.category, slot: 0 };

    const first = await call('/api/card/reroll', { method: 'POST', body });

    if (!first.ok) {
        rerollBox.dataset.state = 'fail';
        rerollBox.textContent = `첫 리롤부터 거부됨 ✗ ${first.status} ${messageOf(first)}`;
        mark('reroll', false);

        return;
    }

    const second = await call('/api/card/reroll', { method: 'POST', body });

    const blocked = !second.ok;

    rerollBox.dataset.state = blocked ? 'pass' : 'fail';
    rerollBox.textContent = blocked
        ? `제한됨 ✓ 두 번째 리롤 → ${second.status} ${messageOf(second)}`
        : '뚫림 ✗ 같은 슬롯을 두 번 바꿀 수 있습니다';

    mark('reroll', blocked);

    renderCardSet(second.ok ? second.body : first.body);
}

/** 카드를 다시 받아도 리롤 횟수가 되살아나면 안 된다 */
async function redeal() {
    if (!state.category) {
        toast('먼저 카테고리를 골라 카드를 받아 주세요.', 'error');

        return;
    }

    const set = await expectOk('/api/card/deal', {
        method: 'POST',
        body: { throwSessionId: state.sessionId, category: state.category },
    }, '카드 조회 실패');

    if (!set) return;

    renderCardSet(set);

    const slotZero = (set.cards ?? []).find((card) => card.slot === 0);

    // 리롤을 쓰기 전이라면 true 가 정상이고, 쓴 뒤라면 false 여야 한다.
    // 어느 쪽인지는 화면이 알 수 없으므로 판정하지 않고 값만 보여준다
    rerollBox.dataset.state = 'info';
    rerollBox.textContent = `다시 받음 → 0번 슬롯 rerollable = ${slotZero ? slotZero.rerollable : '카드 없음'}`
        + ' (리롤을 쓴 뒤라면 false 여야 정상)';
}

/* ── 표시 ────────────────────────────────────────────── */

function mark(name, passed) {
    const item = document.querySelector(`[data-check="${name}"]`);

    if (item) item.dataset.state = passed ? 'pass' : 'fail';
}

function renderRaw(label, result) {
    setText(document, 'rawLabel', `${label} → ${result.status}`);

    const pretty = result.body ? JSON.stringify(result.body, null, 2) : (result.raw || '(본문 없음)');

    document.querySelectorAll('[data-field="raw"]').forEach((box) => {
        box.textContent = pretty;
    });
}

/* ── 초기화 ──────────────────────────────────────────── */

const actions = {
    'create-random': createRandomSession,
    reload: reloadSession,
    'reset-device': () => {
        deviceStore.reset();
        state.sessionId = null;
        state.drawn = null;
        state.category = null;
        setText(document, 'deviceId', deviceStore.get());
        setText(document, 'sessionId', '-');
        setText(document, 'status', '-');
        setText(document, 'region', '-');
        roundsBox.innerHTML = '';
        placesBox.innerHTML = '';
        cardsBox.innerHTML = '';
        setText(document, 'placeRegion', '-');
        setText(document, 'placeMeta', '-');
        setText(document, 'cardCategory', '-');
        setText(document, 'cardMeta', '-');
        toast('기기 ID 를 새로 발급했습니다.', 'info');
    },
    draw,
    again,
    go,
    'bypass-confirm': bypass.confirm,
    'bypass-round': bypass.round,
    'bypass-replay': bypass.replay,
    'bypass-coords': bypass.coords,
    places,
    'arrange-s': () => checkArrange('S'),
    'arrange-e': () => checkArrange('E'),
    'reroll-twice': rerollTwice,
    redeal,
};

document.querySelectorAll('[data-action]').forEach((button) => {
    const handler = actions[button.dataset.action];

    if (handler) {
        // 동기 핸들러(reset-device)도 섞여 있어서 Promise 로 감싼 뒤 catch 한다
        button.addEventListener('click', () => Promise.resolve()
            .then(handler)
            .catch((error) => {
                toast('요청 중 오류가 발생했습니다.', 'error');
                console.error(error);
            }));
    }
});

// 요청이 끝날 때까지 버튼을 잠근다.
// 연타하면 풀이 만들어지기 전에 두 번째 요청이 들어와 TourAPI 를 두 번 호출한다
document.querySelectorAll('[data-category]').forEach((button) => {
    button.addEventListener('click', async () => {
        button.disabled = true;

        try {
            await dealCards(button.dataset.category);
        } catch (error) {
            toast('카드 요청 중 오류가 발생했습니다.', 'error');
            console.error(error);
        } finally {
            button.disabled = false;
        }
    });
});

bindSubmit($('[data-form="search"]'), search);

initHeader();
setText(document, 'deviceId', deviceStore.get());
