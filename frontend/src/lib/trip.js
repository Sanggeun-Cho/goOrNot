/**
 * trip.js
 * 지역 · 던지기 세션 도메인 함수.
 *
 * 화면이 fetch 경로를 직접 알지 않게 한 겹 둔다.
 * 엔드포인트가 바뀌면 여기만 고치면 되고, 화면은 "무엇을 얻는가" 만 안다.
 */

import { api } from './api.js';

/* ── 지역 ────────────────────────────────────────────── */

/**
 * 지역(시군구) 검색.
 *
 * 서버 엔드포인트 이름이 /search 가 아니라 /list 인 이유:
 * 이 API 는 조건에 맞는 목록을 주는 일반 조회라 다른 도메인의 /list 와 규칙을 맞춘 것이다.
 * 화면에서는 "검색" 이라 부르므로 이름 차이는 여기서 흡수한다.
 *
 * @param {string} keyword 빈 문자열이면 서버가 400 을 준다. 호출 전에 걸러야 한다
 * @returns {Promise<Array<{code, name, sidoName, sigunguName, lat, lng}>>}
 */
export function searchRegions(keyword) {
    return api.get('/api/region/list', { keyword });
}

/* ── 던지기 세션 ─────────────────────────────────────── */

/**
 * 직접 검색으로 고른 지역을 세션으로 확정한다.
 *
 * source 가 SEARCH 면 서버가 생성 즉시 CONFIRMED 로 만든다(ThrowSessionDto.CreateReqDto.toEntity).
 * 던지기를 거치지 않았으니 totalCount 는 보내지 않는다.
 *
 * 로그인 여부와 무관하게 호출된다. 비로그인이면 X-Device-Id 로 소유권이 잡히고,
 * 나중에 로그인할 때 linkUser 로 계정에 옮겨 붙인다. (두 헤더 모두 api.js 가 알아서 붙인다)
 *
 * @returns {Promise<{id: number}>}
 */
export function createSearchSession({ code, name, lat, lng }) {
    return api.post('/api/throw-session', {
        source: 'SEARCH',
        regionCode: code,
        regionName: name,
        lat,
        lng,
    });
}

/**
 * 던져서 정할 세션을 연다.
 *
 * source 가 RANDOM 이면 서버는 지역 정보를 아예 받지 않는다(ThrowSessionServiceImpl.create).
 * 던지기 전부터 지역이 박힌 세션이 생기는 걸 막으려는 것이라, 여기서도 횟수만 보낸다.
 *
 * @param {number} totalCount 사용자가 고른 던질 횟수(1/3/5)
 * @returns {Promise<{id: number}>}
 */
export function createRandomSession(totalCount) {
    return api.post('/api/throw-session', { source: 'RANDOM', totalCount });
}

/**
 * 이번 회차에 던질 지역을 서버가 뽑는다.
 *
 * ⚠ 조회처럼 보이지만 POST 다. GET 으로 두면 브라우저·프록시가 캐시해
 *   던질 때마다 같은 지역이 나오는 버그가 된다. 서버도 이 응답을 티켓으로 기록해 둔다.
 *
 * 보내는 id 는 회차 ID 가 아니라 세션 ID 다(서버 DetailReqDto 를 재사용하는 탓에 이름이 id 다).
 *
 * @returns {Promise<{regionCode, regionName, lat, lng}>}
 */
export function drawRegion(sessionId) {
    return api.post('/api/throw-round/draw', { id: Number(sessionId) });
}

/**
 * 이번 회차에 무엇을 골랐는지 남긴다. 기록은 append-only 라 나중에 고칠 수 없다.
 *
 * 말래(AGAIN)도 반드시 기록한다. 다음 추첨에서 이미 본 지역을 빼는 근거가 이 기록이고,
 * 갈래(GO)는 세션 확정 단계에서 "던져서 뽑은 지역이 맞는지" 대조하는 근거가 된다.
 *
 * roundNo 는 보내지 않는다 — 클라이언트가 정하면 회차를 건너뛰거나 덮어쓸 수 있어 서버가 채운다.
 *
 * ⚠ choice 는 서버 ThrowChoice enum 문자열 그대로여야 한다('GO' | 'AGAIN').
 *   'NO' 처럼 보내면 역직렬화에서 400 이 난다.
 */
export function recordRound({ sessionId, regionCode, lat, lng, choice }) {
    return api.post('/api/throw-round', {
        throwSessionId: Number(sessionId),
        regionCode,
        lat,
        lng,
        choice,
    });
}

/**
 * 갈래를 고른 지역으로 세션을 확정한다.
 *
 * 지역 코드만 보낸다. 이름과 좌표는 서버가 카탈로그에서 다시 찾아 덮어쓰기 때문에
 * 여기서 실어 보내봐야 버려지고, 보내면 "클라이언트가 좌표를 정한다" 는 오해만 남는다.
 * 뽑지 않은 지역 코드를 넣으면 서버가 GO 기록이 없다며 400 을 준다.
 */
export function confirmSession({ sessionId, regionCode }) {
    return api.put('/api/throw-session', {
        id: Number(sessionId),
        status: 'CONFIRMED',
        regionCode,
    });
}

/** @returns {Promise<{id, source, status, regionCode, regionName, lat, lng, ...}>} */
export function fetchSession(id) {
    return api.get('/api/throw-session', { id });
}

/**
 * 비로그인으로 만든 세션을 방금 로그인한 계정에 옮겨 붙인다.
 *
 * 이게 없으면 지역을 정해놓고 로그인한 순간 그 여행이 사라진다.
 * 서버는 X-Device-Id 가 세션의 것과 같은지 확인한 뒤에만 소유자를 바꿔준다
 * (남의 세션 id 를 찍어 가져가지 못하게).
 */
export function linkSessionToUser(id) {
    return api.put('/api/throw-session/user', { id });
}

/* ── 카테고리 ────────────────────────────────────────── */

/**
 * 지역 확정 후 고르는 카드 카테고리.
 *
 * ⚠ 서버 ThrowCategory enum 과 짝이다. 값(name)은 서버가 검증하므로 틀리면 400 이 난다.
 *   라벨은 서버에도 같은 문자열이 있지만 목록을 내려주는 API 가 없어 여기 적어둔다.
 *   카테고리를 더하거나 뺄 때는 ThrowCategory.java 와 이 배열을 같이 고쳐야 한다.
 *
 * "공원" 은 [2026-09-17] 결정으로 빠져 있다. contentTypeId 가 없어 cat3 다섯 개로 흩어지는 탓이다.
 */
export const THROW_CATEGORIES = [
    { value: 'ATTRACTION', label: '관광지' },
    { value: 'FOOD', label: '음식' },
    { value: 'LEISURE', label: '레포츠' },
    { value: 'STAY', label: '숙박' },
    { value: 'EVENT', label: '행사' },
];
