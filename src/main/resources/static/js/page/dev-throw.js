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

/** 화면이 들고 있는 상태. 서버가 진실이고 이건 표시용 사본이다 */
const state = {
    sessionId: null,
    drawn: null,      // 마지막 추첨 결과 { regionCode, regionName, lat, lng }
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
 * 확정 지역 주변 장소 조회.
 *
 * 이 화면에서 유일하게 TourAPI 일일 한도를 쓰는 호출이라 버튼을 눌렀을 때만 나간다.
 * 좌표는 보내지 않는다. 지역 코드만 넘기고 서버가 카탈로그 좌표로 조회한다.
 */
async function places() {
    if (!state.sessionId) {
        toast('먼저 세션을 만들어 주세요.', 'error');

        return;
    }

    const session = await expectOk('/api/throw-session', {
        query: { id: state.sessionId },
    }, '세션 조회 실패');

    if (!session) return;

    if (!session.regionCode) {
        toast('지역이 확정된 세션이 아닙니다. "갈래" 로 확정하거나 검색으로 만들어 주세요.', 'error');

        return;
    }

    const result = await expectOk('/api/place/list', {
        query: { regionCode: session.regionCode },
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
        setText(document, 'deviceId', deviceStore.get());
        setText(document, 'sessionId', '-');
        setText(document, 'status', '-');
        setText(document, 'region', '-');
        roundsBox.innerHTML = '';
        placesBox.innerHTML = '';
        setText(document, 'placeRegion', '-');
        setText(document, 'placeMeta', '-');
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

bindSubmit($('[data-form="search"]'), search);

initHeader();
setText(document, 'deviceId', deviceStore.get());
