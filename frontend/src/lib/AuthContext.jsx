import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import * as auth from './auth.js';

/**
 * 로그인 상태를 화면 전체가 공유한다.
 *
 * 서버는 세션을 쓰지 않으므로(STATELESS + JWT) "로그인했는지" 를 물어볼 전용 엔드포인트가 없다.
 * 대신 토큰 보유 여부와 재발급 성공 여부로 판정한다.
 *
 * 토큰을 직접 만지는 코드는 전부 lib/auth.js 에 있다.
 * 여기서 헤더를 다시 읽지 않는 이유: 로그인은 Refresh 만 내려주고 Access 는 /api/auth 로 따로 받는
 * 2단 구조라, 두 곳에서 각자 구현하면 한쪽이 틀려도 화면에서는 401 로만 보여 원인을 찾기 어렵다.
 */
const AuthContext = createContext(null);

/** 아직 판별 전 / 로그인함 / 로그인 안 함 */
export const AUTH_STATUS = {
    PENDING: 'pending',
    IN: 'in',
    OUT: 'out',
};

export function AuthProvider({ children }) {
    const [status, setStatus] = useState(AUTH_STATUS.PENDING);
    const [user, setUser] = useState(null);

    /**
     * 새로고침 직후 상태 복구.
     *
     * Refresh 만 있고 Access 가 없는 경우가 정상적으로 존재한다(Access 는 30분이라 먼저 만료된다).
     * 그래서 토큰 유무만 보지 않고 재발급을 한 번 시도해 본 뒤 판정한다.
     */
    useEffect(() => {
        let alive = true;

        auth.verifySession()
            .then((ok) => {
                if (alive) setStatus(ok ? AUTH_STATUS.IN : AUTH_STATUS.OUT);
            })
            .catch(() => {
                if (alive) setStatus(AUTH_STATUS.OUT);
            });

        return () => {
            alive = false;
        };
    }, []);

    const login = useCallback(async (username, password) => {
        await auth.login({ username, password });
        setStatus(AUTH_STATUS.IN);
    }, []);

    const logout = useCallback(async () => {
        // auth.logout() 은 서버 호출이 실패해도 클라이언트 토큰은 반드시 버린다
        await auth.logout();
        setUser(null);
        setStatus(AUTH_STATUS.OUT);
    }, []);

    /** 401 등으로 화면 쪽에서 세션이 끊겼다고 판단했을 때 상태만 내린다. */
    const markSignedOut = useCallback(() => {
        auth.clearSession();
        setUser(null);
        setStatus(AUTH_STATUS.OUT);
    }, []);

    const value = useMemo(
        () => ({
            status,
            isAuthenticated: status === AUTH_STATUS.IN,
            isPending: status === AUTH_STATUS.PENDING,
            user,
            setUser,
            login,
            logout,
            markSignedOut,
        }),
        [status, user, login, logout, markSignedOut],
    );

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
    const context = useContext(AuthContext);

    if (!context) {
        throw new Error('useAuth 는 AuthProvider 안에서만 쓸 수 있습니다.');
    }

    return context;
}
