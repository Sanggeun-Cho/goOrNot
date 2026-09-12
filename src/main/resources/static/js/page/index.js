/**
 * page/index.js
 * 진입 화면. 서버는 로그인 여부를 알 수 없으므로(토큰이 헤더에만 실린다)
 * 여기서 세션을 확인해 보여줄 버튼을 정한다.
 */

import { initHeader, verifySession, markAuthState, clearSession } from '../auth.js';

initHeader();

verifySession()
    .then((alive) => {
        if (!alive) clearSession(); // 만료된 토큰 찌꺼기를 정리
        markAuthState(alive ? 'in' : 'out');
    })
    .catch(() => {
        clearSession();
        markAuthState('out');
    });
