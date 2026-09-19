import { useEffect, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';

import { useAuth } from '../lib/AuthContext.jsx';
import { fetchSession, THROW_CATEGORIES } from '../lib/trip.js';

/**
 * 화면 3 · 카테고리 선택.
 *
 * 지역이 확정된 세션 하나를 들고 들어온다. 세션 id 는 URL 에 둔다.
 * 상태로 넘기면(navigate state) 새로고침이나 주소 공유에서 그대로 날아가고,
 * 화면이 무엇을 보고 있는지 주소창만으로는 알 수 없게 된다.
 *
 * ── 로그인 게이트 ──
 * 기획안 5번: "로그인 요구 시점 = 지역이 최종 확정된 직후(던지기든 검색이든 같은 한 지점),
 * 카테고리 선택 진입 전". 그 "한 지점" 을 이 화면의 입구로 잡았다.
 * 던지기(2-2)든 검색(2-7)이든 확정되면 결국 여기로 들어오므로, 게이트를 경로마다
 * 따로 두지 않고 도착지 한 곳에서만 본다. 주소창 직접 진입·새로고침도 같이 막힌다.
 *
 * RequireAuth 를 쓰지 않는 이유는 로그인 후 세션을 계정에 연결해야 하는데,
 * 그 세션 id 를 아는 건 URL 파라미터를 읽는 이 화면뿐이기 때문이다.
 *
 * ⚠ 카드 화면(2-6)이 아직 없어서 타일은 눌리지 않는다. 지금은 확정된 지역 확인용이다.
 */
export default function CategoryPage() {
    const navigate = useNavigate();
    const { sessionId } = useParams();
    const { isAuthenticated, isPending } = useAuth();

    const [session, setSession] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        // 로그인 판정 전이거나 비로그인이면 부르지 않는다. 어차피 401 이 돌아온다
        if (isPending || !isAuthenticated) return undefined;

        let alive = true;

        fetchSession(sessionId)
            .then((found) => {
                if (alive) setSession(found);
            })
            .catch((caught) => {
                if (alive) setError(caught.message || '여행 정보를 불러오지 못했습니다.');
            });

        return () => {
            alive = false;
        };
    }, [sessionId, isAuthenticated, isPending]);

    // 판정 중에 로그인 화면을 스치면 깜빡인다. 자리만 잡아둔다
    if (isPending) {
        return <p className="state">확인하는 중…</p>;
    }

    /*
     * 여기서 로그인으로 보낸다. 지역은 이미 확정돼 있으므로 돌아올 곳(from)과
     * 계정에 붙일 세션(linkSession)을 같이 들려 보낸다. 로그인 후 처음부터 다시
     * 던지거나 검색하지 않고 이어서 진행되어야 한다(기획안 8번 3단계).
     */
    if (!isAuthenticated) {
        return (
            <Navigate
                to="/login"
                replace
                state={{ from: `/category/${sessionId}`, linkSession: sessionId }}
            />
        );
    }

    return (
        <>
            <div className="topbar">
                <button
                    type="button"
                    className="topbar__back"
                    onClick={() => navigate(-1)}
                    aria-label="뒤로"
                >
                    ←
                </button>

                {/* 지역명은 서버에서 받아 채운다. 오는 동안은 자리만 비워둔다 */}
                <h1 className="topbar__title">{session?.regionName ?? ' '}</h1>
            </div>

            {error ? (
                <p className="form-error" role="alert">
                    {error}
                </p>
            ) : (
                <>
                    <div className="page-head">
                        <h2 className="page-head__title">무엇을 볼까요</h2>
                        <p className="page-head__desc">고른 종류로 카드 세 장을 뽑아드려요.</p>
                    </div>

                    <div className="tile-grid">
                        {THROW_CATEGORIES.map((category) => (
                            <button
                                key={category.value}
                                type="button"
                                className="tile"
                                disabled
                            >
                                {category.label}
                            </button>
                        ))}
                    </div>

                    <p className="hint-link">카드 뽑기는 아직 준비 중이에요.</p>
                </>
            )}
        </>
    );
}
