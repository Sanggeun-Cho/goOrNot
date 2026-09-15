/**
 * page/dev-tourapi.js
 * TourAPI 연동 점검용 임시 화면. 제출 전에 삭제한다.
 *
 * 일반 화면과 달리 api.get() 대신 apiFetch() 를 직접 쓴다.
 * 여기서는 "성공했는지" 뿐 아니라 HTTP 상태 코드·소요 시간·원본 응답까지 봐야
 * 502 매핑과 인증키 마스킹을 눈으로 확인할 수 있기 때문이다.
 */

import { initHeader, requireAuth } from '../auth.js';
import { apiFetch } from '../api.js';
import { toast, bindSubmit } from '../ui.js';

const QUOTA_KEY = 'dev:tourapi:calls';

const $ = (selector) => document.querySelector(selector);

const listForm = $('[data-form="list"]');
const detailForm = $('[data-form="detail"]');
const listBox = $('[data-list]');
const detailBox = $('[data-detail]');
const summaryBox = $('[data-summary="list"]');
const metaBox = $('[data-meta]');
const leakBox = $('[data-leak]');
const rawBox = $('[data-raw]');
const quotaBox = $('[data-quota]');

/* ── 호출 ────────────────────────────────────────────── */

/**
 * 응답을 해석하지 않고 상태 코드·원본 문자열까지 통째로 돌려준다.
 *
 * @param {string} path
 * @param {object} query
 * @param {{quota?:boolean, diagnostics?:boolean}} [options]
 *        quota:false 면 호출 횟수에 세지 않는다. 추첨·지역목록은 인메모리라 일일 한도를 쓰지 않는다.
 *        diagnostics:false 면 진단 패널을 건드리지 않는다. 페이지 로드 시 자동으로 부르는 호출이
 *        "마지막 응답" 을 덮어쓰거나 키 유출 검사를 미리 통과시키면 안 되기 때문이다.
 * @returns {Promise<{ok:boolean, status:number, ms:number, raw:string, body:*}>}
 */
async function call(path, query, options = {}) {
    const started = performance.now();

    const response = await apiFetch(path, { query });
    const raw = await response.text();
    const ms = Math.round(performance.now() - started);

    if (options.quota !== false) bumpQuota();

    let body = null;

    try {
        body = raw ? JSON.parse(raw) : null;
    } catch {
        body = null; // JSON 이 아니면 원본만 보여준다
    }

    const result = { ok: response.ok, status: response.status, ms, raw, body };

    if (options.diagnostics !== false) renderDiagnostics(path, result);

    return result;
}

/** 서버가 내려준 에러 메시지. GlobalExceptionHandler 는 { "error": "..." } 모양으로 준다 */
function errorMessage(result) {
    if (result.body && typeof result.body.error === 'string') return result.body.error;
    if (result.raw) return result.raw.slice(0, 200);

    return `요청을 처리하지 못했습니다. (HTTP ${result.status})`;
}

/* ── 진단 패널 ───────────────────────────────────────── */

function renderDiagnostics(path, result) {
    const badgeClass = result.ok ? 'badge--ok' : 'badge--fail';

    metaBox.innerHTML = '';

    const badge = document.createElement('span');
    badge.className = `badge ${badgeClass}`;
    badge.textContent = `HTTP ${result.status}`;

    const text = document.createElement('span');
    text.textContent = `${path} · ${result.ms}ms · ${result.raw.length.toLocaleString()} bytes`;

    metaBox.append(badge, text);

    rawBox.textContent = prettify(result.raw);

    renderLeakCheck(result.raw);
}

function prettify(raw) {
    if (!raw) return '(본문 없음)';

    try {
        return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
        return raw;
    }
}

/**
 * 시나리오 4 - 응답에 인증키 흔적이 있는지 본다.
 * 프론트는 진짜 키 값을 모르니 "serviceKey 가 마스킹되지 않은 채 들어있는지" 로 판정한다.
 * 서버 로그까지 보는 검사는 TourApiClientLiveTest 시나리오 4 가 담당한다.
 */
function renderLeakCheck(raw) {
    const leaked = /servicekey\s*[=:]\s*(?!\*{4})[^&\s"',}]+/i.test(raw);

    leakBox.hidden = false;
    leakBox.dataset.state = leaked ? 'fail' : 'pass';
    leakBox.textContent = leaked
        ? '응답 본문에 마스킹되지 않은 serviceKey 가 들어 있습니다. 원본 응답을 확인하세요.'
        : '응답 본문에 serviceKey 평문이 없습니다.';

    // 목록/상세/오류 어느 호출이든 한 번이라도 새면 실패로 못박는다
    if (leaked) {
        setCheck('mask', 'fail');
    } else if (checkState('mask') !== 'fail') {
        setCheck('mask', 'pass');
    }
}

/* ── 체크리스트 ──────────────────────────────────────── */

function setCheck(name, state) {
    const item = document.querySelector(`[data-check="${name}"]`);
    if (!item) return;

    item.dataset.state = state;
    item.querySelector('.check__mark').textContent = state === 'pass' ? '✓' : '✕';
}

function checkState(name) {
    const item = document.querySelector(`[data-check="${name}"]`);
    return item ? item.dataset.state : null;
}

/* ── 시나리오 1 : 목록 ───────────────────────────────── */

bindSubmit(listForm, async (values) => {
    const result = await call('/api/dev/tourapi/area-based-list', values);

    if (!result.ok) {
        setCheck('list', 'fail');
        summaryBox.hidden = true;
        listBox.innerHTML = '';
        showEmpty('list', errorMessage(result));
        toast(errorMessage(result), 'error');
        return;
    }

    renderList(result.body);
});

function renderList(data) {
    const items = data?.items ?? [];

    summaryBox.hidden = false;
    summaryBox.textContent =
        `totalCount ${data?.totalCount ?? '-'} · 이번 페이지 ${items.length}건 `
        + `· numOfRows ${data?.numOfRows ?? '-'} · pageNo ${data?.pageNo ?? '-'}`;

    listBox.innerHTML = '';

    if (items.length === 0) {
        setCheck('list', 'fail');
        showEmpty('list', '응답은 정상이지만 item 이 0건입니다. 조건을 바꿔 다시 조회해 보세요.');
        return;
    }

    items.forEach((item) => listBox.appendChild(itemRow(item)));

    hideEmpty('list');
    setCheck('list', 'pass');
}

function itemRow(item) {
    const row = document.createElement('li');
    row.className = 'item';

    row.appendChild(thumb(item));

    const body = document.createElement('div');
    body.className = 'item__body';

    const title = document.createElement('div');
    title.className = 'item__title';
    title.textContent = item.title ?? '';
    if (!item.title) title.innerHTML = '<em>title 없음</em>';

    const sub = document.createElement('div');
    sub.className = 'item__sub';
    sub.innerHTML = [
        field('contentId', item.contentId),
        field('addr1', item.addr1),
        field('좌표', item.mapX && item.mapY ? `${item.mapX}, ${item.mapY}` : null),
    ].join(' · ');

    body.append(title, sub);

    const button = document.createElement('button');
    button.className = 'btn btn--text';
    button.type = 'button';
    button.textContent = '상세';
    button.disabled = !item.contentId;
    button.addEventListener('click', () => {
        detailForm.elements.contentId.value = item.contentId;
        detailForm.requestSubmit();
        detailForm.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });

    row.append(body, button);

    return row;
}

/** 값이 비면 빨갛게 보여준다. 필드명이 우리가 가정한 것과 다르면 여기서 드러난다 */
function field(label, value) {
    return value ? `${label} ${escapeHtml(value)}` : `<em>${label} 없음</em>`;
}

function thumb(item) {
    if (item.firstImage) {
        const image = document.createElement('img');
        image.className = 'item__thumb';
        image.src = item.firstImage;
        image.alt = '';
        image.loading = 'lazy';
        return image;
    }

    const box = document.createElement('div');
    box.className = 'item__thumb item__thumb--empty';
    box.textContent = 'no img';
    return box;
}

/* ── 시나리오 2 : 상세 ───────────────────────────────── */

bindSubmit(detailForm, async (values) => {
    if (!values.contentId) {
        toast('contentId 를 입력해 주세요.', 'error');
        return;
    }

    const result = await call('/api/dev/tourapi/detail-common', { contentId: values.contentId });

    if (!result.ok) {
        setCheck('detail', 'fail');
        detailBox.hidden = true;
        showEmpty('detail', errorMessage(result));
        toast(errorMessage(result), 'error');
        return;
    }

    renderDetail(result.body);
});

const DETAIL_FIELDS = [
    ['contentId', 'contentId'],
    ['contentTypeId', 'contentTypeId'],
    ['title', 'title'],
    ['addr1', 'addr1'],
    ['addr2', 'addr2'],
    ['tel', 'tel'],
    ['mapX (경도)', 'mapX'],
    ['mapY (위도)', 'mapY'],
    ['firstImage', 'firstImage'],
    ['homepage', 'homepage'],
    ['overview', 'overview'],
];

function renderDetail(item) {
    detailBox.innerHTML = '';

    DETAIL_FIELDS.forEach(([label, key]) => {
        const dt = document.createElement('dt');
        dt.textContent = label;

        const dd = document.createElement('dd');
        const value = item?.[key];

        // overview·homepage 는 HTML 태그가 섞여 오므로 textContent 로만 넣는다 (XSS 방지)
        dd.textContent = value ? String(value) : '없음';
        dd.dataset.empty = value ? 'false' : 'true';

        detailBox.append(dt, dd);
    });

    detailBox.hidden = false;
    hideEmpty('detail');

    // 상세는 결과가 1건이라 item 이 배열이 아닌 객체로 오는 경로다.
    // 파싱이 깨지면 title 부터 비어버리므로 이 값으로 판정한다
    setCheck('detail', item?.title ? 'pass' : 'fail');
}

/* ── 시나리오 3 : 오류 ───────────────────────────────── */

$('[data-action="force-error"]').addEventListener('click', async (event) => {
    const button = event.currentTarget;
    button.disabled = true;

    try {
        // 존재할 수 없는 contentId → 결과 0건 → ExternalApiException → 502
        const result = await call('/api/dev/tourapi/detail-common', { contentId: '0' });

        if (result.status === 502) {
            setCheck('error', 'pass');
            toast(`502 로 내려왔습니다 : ${errorMessage(result)}`, 'success');
        } else {
            setCheck('error', 'fail');
            toast(`502 가 아니라 ${result.status} 로 내려왔습니다.`, 'error');
        }
    } catch {
        setCheck('error', 'fail');
        toast('서버에 연결하지 못했습니다.', 'error');
    } finally {
        button.disabled = false;
    }
});

/* ── 시나리오 5 : 카카오 좌표 변환 ───────────────────── */

const geocodeForm = $('[data-form="geocode"]');
const geocodeBox = $('[data-geocode-result]');

const GEOCODE_FIELDS = [
    ['addressName (표시용)', (c) => c.addressName],
    ['bCode (법정동 10자리)', (c) => c.bCode],
    ['시군구 코드 (앞 5자리)', (c) => sigunguCode(c.bCode)],
    ['lat (위도)', (c) => c.lat],
    ['lng (경도)', (c) => c.lng],
];

function sigunguCode(bCode) {
    return (typeof bCode === 'string' && bCode.length >= 5) ? bCode.slice(0, 5) : null;
}

bindSubmit(geocodeForm, async (values) => {
    if (!values.address) {
        toast('주소를 입력해 주세요.', 'error');
        return;
    }

    const result = await call('/api/dev/kakao/geocode', { address: values.address });

    // 204 = 매칭 0건. 실패가 아니라 "건너뛸 대상" 이라 성공 경로로 다룬다
    if (result.status === 204) {
        geocodeBox.hidden = true;
        showEmpty('geocode', '매칭 0건입니다. 시딩이라면 이 시군구는 건너뜁니다. (HTTP 204)');
        setCheck('geocode', 'pass');
        toast('매칭 0건 → 204 로 내려왔습니다.', 'success');
        return;
    }

    if (!result.ok) {
        setCheck('geocode', 'fail');
        geocodeBox.hidden = true;
        showEmpty('geocode', errorMessage(result));
        toast(errorMessage(result), 'error');
        return;
    }

    renderGeocode(result.body);
});

function renderGeocode(coordinate) {
    geocodeBox.innerHTML = '';

    GEOCODE_FIELDS.forEach(([label, pick]) => {
        const dt = document.createElement('dt');
        dt.textContent = label;

        const dd = document.createElement('dd');
        const value = coordinate ? pick(coordinate) : null;

        dd.textContent = (value === null || value === undefined) ? '없음' : String(value);
        dd.dataset.empty = (value === null || value === undefined) ? 'true' : 'false';

        geocodeBox.append(dt, dd);
    });

    geocodeBox.hidden = false;
    hideEmpty('geocode');

    // 좌표만 오고 bCode 가 없으면 시딩이 시군구를 판정할 수 없다. 그래서 bCode 기준으로 판정한다
    setCheck('geocode', sigunguCode(coordinate?.bCode) ? 'pass' : 'fail');
}

document.querySelectorAll('[data-geocode]').forEach((button) => {
    button.addEventListener('click', () => {
        geocodeForm.elements.address.value = button.dataset.geocode;
        geocodeForm.requestSubmit();
    });
});

/* ── 시나리오 6 : 법정동 코드 ────────────────────────── */

const ldongForm = $('[data-form="ldong"]');
const ldongBox = $('[data-ldong-list]');
const ldongSummary = $('[data-summary="ldong"]');

bindSubmit(ldongForm, async (values) => {
    const query = values.lDongRegnCd ? { lDongRegnCd: values.lDongRegnCd } : {};
    const result = await call('/api/dev/tourapi/ldong-code', query);

    if (!result.ok) {
        setCheck('ldong', 'fail');
        ldongSummary.hidden = true;
        ldongBox.innerHTML = '';
        showEmpty('ldong', errorMessage(result));
        toast(errorMessage(result), 'error');
        return;
    }

    renderLdong(result.body, values.lDongRegnCd);
});

function renderLdong(codes, parentCode) {
    const items = Array.isArray(codes) ? codes : [];

    ldongSummary.hidden = false;
    ldongSummary.textContent = parentCode
        ? `시도 ${parentCode} 아래 시군구 ${items.length}건`
        : `시도 ${items.length}건`;

    ldongBox.innerHTML = '';

    if (items.length === 0) {
        setCheck('ldong', 'fail');
        showEmpty('ldong', '응답은 정상이지만 코드가 0건입니다.');
        return;
    }

    items.forEach((item) => ldongBox.appendChild(codeRow(item, parentCode)));

    hideEmpty('ldong');
    setCheck('ldong', 'pass');
}

function codeRow(item, parentCode) {
    const row = document.createElement('li');
    row.className = 'code';

    const code = document.createElement('span');
    code.className = 'code__value';
    code.textContent = item.code ?? '-';

    const name = document.createElement('span');
    name.className = 'code__name';
    name.textContent = item.name ?? '';

    row.append(code, name);

    // 시도 목록이면 클릭으로 그 아래 시군구를 바로 펼쳐 본다
    if (!parentCode && item.code) {
        const button = document.createElement('button');
        button.className = 'btn btn--text';
        button.type = 'button';
        button.textContent = '시군구';
        button.addEventListener('click', () => {
            ldongForm.elements.lDongRegnCd.value = item.code;
            ldongForm.requestSubmit();
        });

        row.appendChild(button);
    }

    return row;
}

/* ── 시나리오 7 : 장소 목록 (PlaceService) ───────────── */

const placeForm = $('[data-form="place"]');
const placeBox = $('[data-place-list]');
const placeSummary = $('[data-summary="place"]');

bindSubmit(placeForm, async (values) => {
    if (!values.regionCode) {
        toast('지역을 선택해 주세요.', 'error');
        return;
    }

    // 빈 문자열을 그대로 보내면 서버에서 숫자 변환이 깨진다. 값이 있는 것만 추린다
    const query = { regionCode: values.regionCode };
    ['contentTypeId', 'radius', 'numOfRows'].forEach((key) => {
        if (values[key]) query[key] = values[key];
    });

    const result = await call('/api/dev/place/list', query);

    if (!result.ok) {
        setCheck('place', 'fail');
        placeSummary.hidden = true;
        placeBox.innerHTML = '';
        showEmpty('place', errorMessage(result));
        toast(errorMessage(result), 'error');
        return;
    }

    renderPlaces(result.body);
});

function renderPlaces(data) {
    const places = data?.places ?? [];

    placeSummary.hidden = false;
    placeSummary.textContent =
        `${data?.regionName ?? '-'} (${data?.regionCode ?? '-'}) `
        + `· 중심 ${data?.lat ?? '-'}, ${data?.lng ?? '-'} `
        + `· 사용 반경 ${data?.radius ?? '-'}m `
        + `· totalCount ${data?.totalCount ?? '-'} · 이번 페이지 ${places.length}건`;

    placeBox.innerHTML = '';

    if (places.length === 0) {
        setCheck('place', 'fail');
        showEmpty('place', '응답은 정상이지만 장소가 0건입니다. 반경을 넓히거나 타입을 바꿔 보세요.');
        return;
    }

    places.forEach((place) => placeBox.appendChild(placeRow(place)));

    hideEmpty('place');

    // 변환이 깨지면 placeName 부터 비어버린다. 이름이 있는 건이 하나라도 있으면 통과로 본다
    setCheck('place', places.some((place) => place.placeName) ? 'pass' : 'fail');
}

function placeRow(place) {
    const row = document.createElement('li');
    row.className = 'item';

    row.appendChild(placeThumb(place));

    const body = document.createElement('div');
    body.className = 'item__body';

    const title = document.createElement('div');
    title.className = 'item__title';
    title.textContent = place.placeName ?? '';
    if (!place.placeName) title.innerHTML = '<em>placeName 없음</em>';

    const sub = document.createElement('div');
    sub.className = 'item__sub';
    sub.innerHTML = [
        field('contentId', place.contentId),
        field('주소', place.address),
        field('거리', place.distance != null ? `${place.distance.toLocaleString()}m` : null),
        field('좌표', place.lat && place.lng ? `${place.lat}, ${place.lng}` : null),
    ].join(' · ');

    body.append(title, sub);
    row.appendChild(body);

    // Type3(제3유형)은 출처 표시 + 변경 금지라 화면 처리에 주의가 필요하다
    if (place.imageCopyrightCode) {
        const badge = document.createElement('span');
        badge.className = 'badge';
        badge.textContent = place.imageCopyrightCode;
        row.appendChild(badge);
    }

    return row;
}

function placeThumb(place) {
    const src = place.thumbnailUrl || place.imageUrl;

    if (src) {
        const image = document.createElement('img');
        image.className = 'item__thumb';
        image.src = src;
        image.alt = '';
        image.loading = 'lazy';
        return image;
    }

    const box = document.createElement('div');
    box.className = 'item__thumb item__thumb--empty';
    box.textContent = 'no img';
    return box;
}

/** 지역 선택 상자 채우기. 인메모리라 일일 한도를 쓰지 않는다 */
async function loadRegions() {
    const select = placeForm.elements.regionCode;

    try {
        const result = await call('/api/dev/region/list', {}, { quota: false, diagnostics: false });

        if (!result.ok || !Array.isArray(result.body)) {
            select.innerHTML = '<option value="">지역 목록을 불러오지 못했습니다</option>';
            return;
        }

        select.innerHTML = '';

        result.body.forEach((region) => {
            const option = document.createElement('option');
            option.value = region.code;
            option.textContent = `${region.name}${region.popular ? ' · 인기' : ''}`;
            select.appendChild(option);
        });

        // 기본값은 인기 지역이 아닌 곳으로 둔다. 소외 지역에서도 장소가 나오는지가 더 중요하다
        const plain = result.body.find((region) => !region.popular);
        if (plain) select.value = plain.code;
    } catch {
        select.innerHTML = '<option value="">지역 목록을 불러오지 못했습니다</option>';
    }
}

/* ── 시나리오 8 : 추첨 시뮬레이터 ────────────────────── */

const drawForm = $('[data-form="draw"]');
const drawBox = $('[data-draw-list]');
const drawSummary = $('[data-summary="draw"]');

bindSubmit(drawForm, async (values) => {
    const query = { count: values.count || 1 };
    if (values.excluded) query.excluded = values.excluded;

    const result = await call('/api/dev/region/draw', query, { quota: false });

    if (!result.ok) {
        setCheck('draw', 'fail');
        drawSummary.hidden = true;
        drawBox.innerHTML = '';
        showEmpty('draw', errorMessage(result));
        toast(errorMessage(result), 'error');
        return;
    }

    renderDraw(result.body, values.excluded);
});

function renderDraw(data, excludedRaw) {
    const samples = data?.samples ?? [];
    const observed = data?.popularRatio ?? 0;
    const expected = data?.expectedPopularRatio ?? 0;

    drawSummary.hidden = false;
    drawSummary.textContent =
        `후보 ${data?.catalogSize ?? '-'}곳 (제외 ${data?.excludedCount ?? 0}) `
        + `· ${data?.count ?? 0}회 추첨 · 서로 다른 지역 ${data?.distinctCount ?? 0}곳 `
        + `· 인기 지역 ${percent(observed)} (기대 ${percent(expected)})`;

    drawBox.innerHTML = '';
    samples.forEach((region) => drawBox.appendChild(drawRow(region)));

    if (samples.length === 0) {
        showEmpty('draw', '추첨 결과가 없습니다.');
        setCheck('draw', 'fail');
        return;
    }

    hideEmpty('draw');

    const excluded = splitCodes(excludedRaw);
    const leaked = samples.filter((region) => excluded.includes(region.code));

    if (leaked.length > 0) {
        setCheck('draw', 'fail');
        toast(`제외한 지역이 뽑혔습니다 : ${leaked[0].code}`, 'error');
        return;
    }

    // 표본이 적으면 분포를 판정할 수 없다. 제외 규칙만 확인된 상태로 통과시킨다
    if ((data?.count ?? 0) < 100) {
        setCheck('draw', 'pass');
        return;
    }

    // 인기 지역이 기대 확률의 2배를 넘게 나오면 가중치가 먹지 않는다는 뜻이다
    setCheck('draw', observed <= Math.max(expected * 2, 0.01) ? 'pass' : 'fail');
}

function drawRow(region) {
    const row = document.createElement('li');
    row.className = 'item item--compact';

    const body = document.createElement('div');
    body.className = 'item__body';

    const title = document.createElement('div');
    title.className = 'item__title';
    title.textContent = region.name ?? '';

    const sub = document.createElement('div');
    sub.className = 'item__sub';
    sub.innerHTML = [
        field('코드', region.code),
        field('weight', region.weight),
        field('좌표', region.lat && region.lng ? `${region.lat}, ${region.lng}` : null),
    ].join(' · ');

    body.append(title, sub);
    row.appendChild(body);

    if (region.popular) {
        const badge = document.createElement('span');
        badge.className = 'badge';
        badge.textContent = '인기';
        row.appendChild(badge);
    }

    return row;
}

function percent(value) {
    return `${(Number(value) * 100).toFixed(2)}%`;
}

function splitCodes(value) {
    if (!value) return [];

    return String(value).split(',').map((code) => code.trim()).filter(Boolean);
}

document.querySelectorAll('[data-draw-count]').forEach((button) => {
    button.addEventListener('click', () => {
        drawForm.elements.count.value = button.dataset.drawCount;
        drawForm.requestSubmit();
    });
});

/**
 * "말래" 10연속 재현.
 * 뽑을 때마다 그 지역을 제외 목록에 더해 다시 뽑는다.
 * 실제 재던지기와 같은 흐름이라, 10곳이 모두 달라야 정상이다.
 */
$('[data-action="draw-chain"]').addEventListener('click', async (event) => {
    const button = event.currentTarget;
    button.disabled = true;

    const excluded = [];
    const picked = [];

    try {
        for (let i = 0; i < 10; i++) {
            const query = { count: 1 };
            if (excluded.length > 0) query.excluded = excluded.join(',');

            const result = await call('/api/dev/region/draw', query, { quota: false });

            if (!result.ok) {
                setCheck('draw', 'fail');
                toast(errorMessage(result), 'error');
                return;
            }

            const region = result.body?.samples?.[0];
            if (!region) break;

            picked.push(region);
            excluded.push(region.code);
        }

        drawForm.elements.excluded.value = excluded.join(',');

        drawSummary.hidden = false;
        drawSummary.textContent =
            `말래 ${picked.length}연속 · 서로 다른 지역 ${new Set(excluded).size}곳`;

        drawBox.innerHTML = '';
        picked.forEach((region) => drawBox.appendChild(drawRow(region)));
        hideEmpty('draw');

        const unique = new Set(excluded).size === excluded.length;

        setCheck('draw', unique ? 'pass' : 'fail');
        toast(unique
            ? `${picked.length}연속 모두 다른 지역이 나왔습니다.`
            : '같은 지역이 두 번 나왔습니다. 제외 처리가 안 먹고 있습니다.',
            unique ? 'success' : 'error');
    } finally {
        button.disabled = false;
    }
});

/* ── 표시 유틸 ───────────────────────────────────────── */

function showEmpty(name, message) {
    const box = document.querySelector(`[data-empty="${name}"]`);
    if (!box) return;

    box.hidden = false;
    box.textContent = message;
}

function hideEmpty(name) {
    const box = document.querySelector(`[data-empty="${name}"]`);
    if (box) box.hidden = true;
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (char) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    })[char]);
}

/* ── 호출 횟수 ───────────────────────────────────────── */

function bumpQuota() {
    const count = Number(localStorage.getItem(QUOTA_KEY) ?? 0) + 1;
    localStorage.setItem(QUOTA_KEY, String(count));
    quotaBox.textContent = count.toLocaleString();
}

/* ── 진입 ────────────────────────────────────────────── */

initHeader();

quotaBox.textContent = Number(localStorage.getItem(QUOTA_KEY) ?? 0).toLocaleString();

// 점검 API 는 로그인 필수다. 열어두면 누구나 일일 한도를 소모시킬 수 있다.
// 인증이 끝난 뒤에 지역 목록을 채운다. 먼저 부르면 401 로 빈 목록이 된다
requireAuth().then((alive) => {
    if (alive) loadRegions();
});
