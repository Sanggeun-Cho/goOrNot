import { Navigate, useLocation } from 'react-router-dom';

import { useAuth } from '../lib/AuthContext.jsx';

/**
 * 로그인이 필요한 화면 가드.
 *
 * 판정 전(PENDING)에 로그인 화면으로 보내면, 새로고침할 때마다 로그인 화면이 한 번 스쳤다가
 * 원래 화면으로 돌아오는 깜빡임이 생긴다. 그래서 판정이 끝날 때까지는 자리만 잡아둔다.
 *
 * 돌아올 경로를 state 로 넘겨 로그인 후 원래 가려던 곳으로 보낸다.
 */
export default function RequireAuth({ children }) {
    const { isAuthenticated, isPending } = useAuth();
    const location = useLocation();

    if (isPending) {
        return <p className="state">확인하는 중…</p>;
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace state={{ from: location.pathname }} />;
    }

    return children;
}
