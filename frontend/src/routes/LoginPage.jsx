import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '../lib/AuthContext.jsx';
import { linkSessionToUser } from '../lib/trip.js';

/**
 * 화면 5 · 로그인.
 *
 * 와이어프레임은 "이메일"로 그려져 있지만 서버는 username 으로 인증한다
 * (UserDto.LoginReqDto / JwtAuthenticationFilter). 화면 문구를 이메일로 두면
 * 가입할 때 쓴 아이디를 못 찾는 사람이 생기므로 여기서는 "아이디"로 적는다.
 *
 * 지역 확정 게이트로 넘어온 경우(state.linkSession)에는 로그인 직후
 * 비로그인으로 만들어 둔 여행을 계정에 연결한 뒤 원래 가던 곳으로 보낸다.
 */
export default function LoginPage() {
    const { login, isAuthenticated } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    /** 로그인 후 돌아갈 곳. RequireAuth 가 넘겨준 경로가 있으면 그리로 */
    const from = location.state?.from ?? '/';

    /** 가입 직후 넘어온 경우 (SignupPage 가 넘겨준다) */
    const justSignedUp = location.state?.signedUp === true;

    /** 지역 확정 게이트를 통해 왔다면 계정에 붙여줄 세션 id */
    const linkSession = location.state?.linkSession ?? null;

    useEffect(() => {
        if (!isAuthenticated) return undefined;

        let alive = true;

        (async () => {
            if (linkSession) {
                try {
                    await linkSessionToUser(linkSession);
                } catch {
                    /*
                     * 연결 실패는 이 기기에서 만든 세션이 아닐 때 나온다
                     * (주소를 받아 열었거나, 저장소를 지워 deviceId 가 바뀐 경우).
                     * 로그인 자체는 성공했으므로 튕기지 않고 메인으로 보내 다시 정하게 한다.
                     * 그 여행으로 보내봐야 남의 세션이라 상세 조회에서 어차피 막힌다.
                     */
                    if (alive) navigate('/', { replace: true });
                    return;
                }
            }

            if (alive) navigate(from, { replace: true });
        })();

        return () => {
            alive = false;
        };
    }, [isAuthenticated, from, linkSession, navigate]);

    async function handleSubmit(event) {
        event.preventDefault();
        if (submitting) return;

        setError('');
        setSubmitting(true);

        try {
            await login(username.trim(), password);
            // 이동은 위 useEffect 가 맡는다. 여기서 또 navigate 하면 두 번 밀린다
        } catch (caught) {
            setError(caught.message || '로그인에 실패했습니다.');
            setSubmitting(false);
        }
    }

    return (
        <>
            <div className="page-head">
                <h1 className="page-head__title">로그인</h1>
                <p className="page-head__desc">
                    {justSignedUp
                        ? '가입이 완료됐어요. 방금 만든 계정으로 들어와 주세요.'
                        : '여정을 이어가려면 로그인이 필요해요.'}
                </p>
            </div>

            <form className="form auth-form" onSubmit={handleSubmit} noValidate>
                {/* 아이디가 틀렸는지 비밀번호가 틀렸는지는 구분해서 알려주지 않는다.
                    구분해 주면 어떤 아이디가 존재하는지 확인할 수 있는 통로가 된다 */}
                {error && (
                    <p className="form-error" role="alert">
                        {error}
                    </p>
                )}

                <div className="field">
                    <label className="field__label" htmlFor="username">
                        아이디<span className="req">*</span>
                    </label>
                    <input
                        id="username"
                        className="field__input"
                        type="text"
                        autoComplete="username"
                        autoCapitalize="none"
                        spellCheck="false"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        disabled={submitting}
                        required
                    />
                </div>

                <div className="field">
                    <label className="field__label" htmlFor="password">
                        비밀번호<span className="req">*</span>
                    </label>
                    <input
                        id="password"
                        className="field__input"
                        type="password"
                        autoComplete="current-password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        disabled={submitting}
                        required
                    />
                </div>

                <button
                    type="submit"
                    className={`btn btn--accent btn--lg btn--block${submitting ? ' is-loading' : ''}`}
                    disabled={submitting || !username.trim() || !password}
                >
                    로그인
                </button>
            </form>

            {/* 가입하러 갔다 와도 정해둔 지역을 잃지 않도록 게이트 정보를 들려 보낸다 */}
            <p className="hint-link">
                아직 계정이 없으신가요?{' '}
                <Link to="/signup" state={{ from, linkSession }}>
                    회원가입
                </Link>
            </p>
        </>
    );
}
