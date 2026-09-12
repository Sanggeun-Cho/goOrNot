/**
 * api.js
 * 서버 통신 단일 진입점.
 *
 * 핵심 역할은 Access Token 재발급 처리다.
 * Access Token 은 30분이면 만료되는데, 만료될 때마다 화면이 로그인으로 튕기면 안 되므로
 * 401 을 받으면 Refresh Token 으로 /api/auth 를 호출해 새 Access Token 을 받고
 * 원래 요청을 한 번만 다시 시도한다. (React 전환 시 axios interceptor 로 옮겨갈 로직)
 */

import { tokenStore } from './token.js';

export const ACCESS_HEADER = 'Authorization';
export const REFRESH_HEADER = 'Authorization-Refresh';

/** 서버가 내려준 상태 코드와 메시지를 담는 에러 */
export class ApiError extends Error {
    constructor(status, message, fieldErrors = null) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.fieldErrors = fieldErrors; // { 필드명: 메시지 } — validation 실패일 때만 채워진다
    }
}

/* ── 재발급 ──────────────────────────────────────────── */

// 동시에 여러 요청이 401 을 받아도 재발급은 한 번만 하도록 진행 중인 Promise 를 공유한다
let reissuing = null;

/**
 * Refresh Token 으로 Access Token 을 재발급받는다.
 * @returns {Promise<boolean>} 성공 여부
 */
export function reissueAccessToken() {
    const refreshToken = tokenStore.getRefresh();
    if (!refreshToken) return Promise.resolve(false);

    if (!reissuing) {
        reissuing = fetch('/api/auth', {
            method: 'POST',
            headers: { [REFRESH_HEADER]: refreshToken },
        })
            .then((res) => {
                if (!res.ok) {
                    // Refresh Token 까지 만료됐다면 세션을 통째로 버린다
                    tokenStore.clear();
                    return false;
                }

                const accessToken = res.headers.get(ACCESS_HEADER);
                if (!accessToken) return false;

                tokenStore.setAccess(accessToken);
                return true;
            })
            .catch(() => false)
            .finally(() => {
                reissuing = null;
            });
    }

    return reissuing;
}

/* ── 요청 ────────────────────────────────────────────── */

function buildUrl(path, query) {
    if (!query) return path;

    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') params.append(key, value);
    });

    const qs = params.toString();
    return qs ? `${path}?${qs}` : path;
}

/**
 * 인증 헤더 부착 + 401 재시도까지 처리한 fetch. 응답 객체를 그대로 돌려준다.
 * 응답 헤더(토큰)를 직접 읽어야 하는 로그인 같은 요청에서 쓴다.
 */
export async function apiFetch(path, { method = 'GET', body, query, auth = true, retry = true } = {}) {
    const headers = {};

    if (body !== undefined) headers['Content-Type'] = 'application/json';

    if (auth) {
        const accessToken = tokenStore.getAccess();
        if (accessToken) headers[ACCESS_HEADER] = accessToken;
    }

    const response = await fetch(buildUrl(path, query), {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
    });

    // Access Token 만료로 보이면 재발급 후 딱 한 번만 재시도한다
    if (response.status === 401 && auth && retry && tokenStore.getRefresh()) {
        const reissued = await reissueAccessToken();
        if (reissued) {
            return apiFetch(path, { method, body, query, auth, retry: false });
        }
    }

    return response;
}

/**
 * 응답 본문을 파싱해 돌려주고, 실패하면 ApiError 를 던진다.
 * 일반적인 API 호출은 전부 이 함수를 쓴다.
 */
export async function request(path, options = {}) {
    const response = await apiFetch(path, options);
    const body = await readBody(response);

    if (!response.ok) throw toApiError(response.status, body);

    return body;
}

export const api = {
    get: (path, query) => request(path, { method: 'GET', query }),
    post: (path, body, options) => request(path, { method: 'POST', body, ...options }),
    put: (path, body) => request(path, { method: 'PUT', body }),
    del: (path, body) => request(path, { method: 'DELETE', body }),
};

/* ── 응답 해석 ───────────────────────────────────────── */

async function readBody(response) {
    if (response.status === 204) return null;

    const text = await response.text();
    if (!text) return null;

    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

const DEFAULT_MESSAGE = {
    400: '입력값을 다시 확인해 주세요.',
    401: '로그인이 필요합니다.',
    403: '권한이 없습니다.',
    404: '요청한 정보를 찾을 수 없습니다.',
    409: '이미 사용 중인 값입니다.',
    500: '서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.',
};

/**
 * GlobalExceptionHandler 응답은 두 가지 모양으로 온다.
 *   - 일반 예외      : { "error": "메시지" }
 *   - validation 실패 : { "필드명": "메시지", ... }
 */
function toApiError(status, body) {
    if (body && typeof body === 'object') {
        // Spring 기본 에러 응답({timestamp, status, error, path})은 error 값이 "Unauthorized" 같은
        // 영문 상태 문자열이라 그대로 노출하면 안 된다. 아래 기본 메시지로 대체한다.
        const isSpringDefault = 'timestamp' in body && 'status' in body && 'path' in body;

        if (!isSpringDefault) {
            if (typeof body.error === 'string') {
                return new ApiError(status, body.error);
            }

            const entries = Object.entries(body).filter(([, v]) => typeof v === 'string');
            if (entries.length > 0) {
                return new ApiError(status, entries[0][1], Object.fromEntries(entries));
            }
        }
    }

    if (typeof body === 'string' && body.trim()) {
        return new ApiError(status, body);
    }

    return new ApiError(status, DEFAULT_MESSAGE[status] ?? '요청을 처리하지 못했습니다.');
}
