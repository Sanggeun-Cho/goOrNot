import { Link, useNavigate } from 'react-router-dom';

import { useAuth } from '../lib/AuthContext.jsx';

/**
 * 헤더 + 본문 + 푸터 껍데기.
 *
 * 로그인 여부 토글은 common.css 의 body[data-auth] 규칙 대신 조건부 렌더링으로 한다.
 * React 가 상태를 이미 들고 있는데 body 속성을 또 만지면 진실이 두 군데가 된다.
 *
 * 하단 고정 버튼(.cta)이 있는 화면의 본문 여백은 app.css 의 :has() 가 알아서 잡는다.
 * 셸에 플래그를 넘기지 않는 이유는 화면을 추가할 때 고칠 곳을 늘리지 않기 위해서다.
 */
export default function AppShell({ children }) {
    const { isAuthenticated, isPending, logout } = useAuth();
    const navigate = useNavigate();

    async function handleLogout() {
        await logout();
        navigate('/', { replace: true });
    }

    return (
        <div className="app">
            <header className="app-header">
                {/* 앱 이름은 "갈래 말래" — 띄어쓰기가 들어간다.
                    JSX 는 줄바꿈에 붙은 공백을 지우므로 {' '} 로 명시한다 */}
                <Link className="app-header__brand" to="/">
                    {/*
                      로고는 장식이 아니라 이름을 한 번 더 말하는 그림이라 alt 를 비운다.
                      옆에 글자로 "갈래 말래" 가 이미 있어서, alt 를 채우면 읽는 도구가 두 번 읽는다.

                      width/height 를 박아 두는 이유: 이미지가 늦게 와도 자리가 먼저 잡혀
                      헤더가 밀렸다 돌아오지 않는다. (파비콘과 같은 파일을 쓰지 않고
                      128px 짜리를 따로 둔 건 192·512 를 헤더에서 줄여 쓰기엔 무겁기 때문)
                    */}
                    <img
                        className="app-header__mark"
                        src="/images/logo-mark.png"
                        alt=""
                        width="28"
                        height="28"
                    />
                    <span className="app-header__name">
                        갈래{' '}
                        <span>말래</span>
                    </span>
                </Link>

                {/* 판정 전에는 비워둔다. 로그인/로그아웃이 번갈아 번쩍이는 걸 막는다 */}
                {!isPending && (
                    <nav className="app-header__nav">
                        {isAuthenticated ? (
                            <>
                                <Link to="/trips">내 여행</Link>
                                <button type="button" onClick={handleLogout}>
                                    로그아웃
                                </button>
                            </>
                        ) : (
                            <Link to="/login">로그인</Link>
                        )}
                    </nav>
                )}
            </header>

            <main className="app__main">{children}</main>

            {/* 처리방침은 어느 화면에서든 닿을 수 있어야 한다. 푸터가 그 자리다 */}
            <footer className="app-footer">
                한국관광공사 TourAPI 4.0 의 정보를 활용합니다.
                <br />
                <Link to="/privacy">개인정보처리방침</Link>
            </footer>
        </div>
    );
}
