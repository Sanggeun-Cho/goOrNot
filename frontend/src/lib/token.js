/**
 * token.js
 * 토큰 저장소. 저장 위치를 이 파일 한 곳에만 두어,
 * 나중에 httpOnly 쿠키로 바꿀 때 다른 코드를 건드리지 않아도 되게 한다.
 *
 * 백엔드는 헤더 값을 "Bearer {token}" 형태로 내려주므로(ExternalProperties.withPrefix),
 * 받은 문자열을 가공하지 않고 그대로 저장했다가 그대로 되돌려준다.
 */

const KEY_ACCESS = 'goornot.accessToken';
const KEY_REFRESH = 'goornot.refreshToken';

// 탭을 닫으면 사라지도록 sessionStorage 사용 (localStorage 보다 노출 범위가 좁다)
const storage = window.sessionStorage;

export const tokenStore = {
    getAccess() {
        return storage.getItem(KEY_ACCESS);
    },

    setAccess(value) {
        if (value) storage.setItem(KEY_ACCESS, value);
    },

    getRefresh() {
        return storage.getItem(KEY_REFRESH);
    },

    setRefresh(value) {
        if (value) storage.setItem(KEY_REFRESH, value);
    },

    clear() {
        storage.removeItem(KEY_ACCESS);
        storage.removeItem(KEY_REFRESH);
    },

    /**
     * Refresh Token 보유 여부.
     * 유효성까지 보장하지는 않으므로(만료됐을 수 있음) 화면을 즉시 가리는 용도로만 쓴다.
     */
    hasSession() {
        return Boolean(storage.getItem(KEY_REFRESH));
    },
};
