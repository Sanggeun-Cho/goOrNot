import { useEffect, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';

import Icon, { CATEGORY_ICONS } from '../components/Icon.jsx';
import { useAuth } from '../lib/AuthContext.jsx';
import { categoryLabel, fetchSavedPlaces, fetchSession, markPlace, THROW_CATEGORIES } from '../lib/trip.js';

/**
 * 화면 3 · 카테고리 선택 = 여행 상세.
 *
 * 내 여행에서 여행을 누르면 여기로 들어온다. 따로 상세 화면을 만들지 않은 이유는
 * 여행에서 할 일이 "이번엔 뭘 볼까" 하나뿐이라, 카테고리를 고르는 이 화면이
 * 곧 여행의 얼굴이기 때문이다. 지금까지 간 곳도 여기서 카테고리별로 보여준다.
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
 * ── 카테고리는 몇 번이든, 한 번에 한 곳 ──
 * 기획안 5번 [2026-09-17]: 열 수 있는 카테고리 수를 제한하지 않는다(5종 전부).
 * [2026-09-19] 대신 한 판은 한 곳으로 끝난다. 관광지를 골라 한 곳을 정하면
 * 그 판은 닫히고, 다음 날 관광지를 다시 누르면 새 카드 세 장이 깔린다.
 * 그래서 이 화면의 타일은 "다녀온 곳" 을 세어 보여줄 뿐 잠기지 않는다.
 */
export default function CategoryPage() {
    const navigate = useNavigate();
    const { sessionId } = useParams();
    const { isAuthenticated, isPending } = useAuth();

    const [session, setSession] = useState(null);
    const [visited, setVisited] = useState([]);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(null);
    const [busyHeart, setBusyHeart] = useState(null);

    useEffect(() => {
        // 로그인 판정 전이거나 비로그인이면 부르지 않는다. 어차피 401 이 돌아온다
        if (isPending || !isAuthenticated) return undefined;

        let alive = true;

        /*
         * 이 여행에서 visited 가 켜진 행만 받는다. wished 로는 거르지 않는다 —
         * 응답의 wished 값이 아래 하트 버튼의 초기 상태가 되기 때문이다.
         * (찜만 해둔 곳의 목록은 여기가 아니라 마이페이지에서 본다)
         */
        Promise.all([fetchSession(sessionId), fetchSavedPlaces({ sessionId, visited: true })])
            .then(([found, places]) => {
                if (!alive) return;
                setSession(found);
                setVisited(places ?? []);
            })
            .catch((caught) => {
                if (alive) setError(caught.message || '여행 정보를 불러오지 못했습니다.');
            });

        return () => {
            alive = false;
        };
    }, [sessionId, isAuthenticated, isPending]);

    /**
     * 간 곳에서 뺀다.
     *
     * 카드 화면에서 잘못 눌렀을 때 되돌릴 유일한 길이라 눈에 보이는 자리에 둔다.
     *
     * ⚠ DELETE 가 아니라 visited: false upsert 다. DELETE 는 행 전체를 지워서
     *   같은 장소에 눌러둔 하트까지 함께 사라진다.
     *
     * 판(카드 풀)은 되살아나지 않는다. 서버가 이미 버렸기 때문에 같은 카테고리를
     * 다시 열면 새 카드가 깔린다. 기록을 되돌리는 것과 판을 되돌리는 것은 다른 일이고,
     * 후자를 허용하면 "골랐다가 취소" 로 리롤을 무한히 얻는 우회가 된다.
     */
    async function undoVisit(place) {
        setBusy(place.id);
        setError('');

        try {
            await markPlace({
                sessionId,
                category: place.category,
                place,
                visited: false,
            });

            setVisited((prev) => prev.filter((row) => row.id !== place.id));
        } catch (caught) {
            setError(caught.message || '기록을 되돌리지 못했습니다.');
        } finally {
            setBusy(null);
        }
    }

    /**
     * 간 곳에 하트를 켜고 끈다.
     *
     * 왜 여기에도 두는가: 카드 화면은 한 곳을 고르면 닫히고 다시 열리지 않는다.
     * 그래서 "갔다 와서 마음에 들었다" 를 남길 자리가 없었다. 간 곳 목록이
     * 그 장소를 다시 만나는 유일한 화면이라 하트를 여기에 붙인다.
     *
     * 간 곳이면서 찜인 상태는 모순이 아니다 — "갔는데 또 가고 싶다" 다.
     * SavedPlace 는 (여행, 장소)당 행 하나에 visited / wished 를 따로 켠다.
     *
     * ⚠ wished 만 보낸다. visited 를 같이 실어 보내면 화면이 들고 있던 낡은 값이
     *   서버 값을 덮어쓴다. 서버는 undefined 를 "그대로 두라" 로 읽는다.
     */
    async function toggleWish(place) {
        const next = !place.wished;

        setBusyHeart(place.id);
        setError('');

        try {
            await markPlace({
                sessionId,
                category: place.category,
                place,
                wished: next,
            });

            setVisited((prev) =>
                prev.map((row) => (row.id === place.id ? { ...row, wished: next } : row)),
            );
        } catch (caught) {
            setError(caught.message || '찜하지 못했습니다.');
        } finally {
            setBusyHeart(null);
        }
    }

    // 카테고리별로 묶어둔다. 타일의 개수 배지와 아래 목록이 같은 자료를 본다
    const byCategory = {};
    visited.forEach((place) => {
        (byCategory[place.category] ??= []).push(place);
    });

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

    const locked = session?.status !== 'CONFIRMED';

    return (
        <>
            <div className="topbar">
                <button
                    type="button"
                    className="topbar__back"
                    onClick={() => navigate('/trips')}
                    aria-label="내 여행으로"
                >
                    <Icon name="back" size={20} />
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
                        <h2 className="page-head__title">오늘은 뭘 할까요</h2>
                        <p className="page-head__desc">종류를 고르면 카드 세 장을 뽑아드려요.</p>
                    </div>

                    <div className="tile-grid">
                        {THROW_CATEGORIES.map((category) => {
                            const count = byCategory[category.value]?.length ?? 0;

                            return (
                                <button
                                    key={category.value}
                                    type="button"
                                    className="tile"
                                    /*
                                     * 세션 상세가 오기 전이거나 지역이 확정되지 않았으면 잠가둔다.
                                     * 확정 전 세션으로 카드를 요청하면 서버가 400
                                     * ("아직 지역이 확정되지 않았습니다")을 주는데, 그 에러를 카드
                                     * 화면에서 받느니 여기서 못 누르게 하는 편이 낫다.
                                     */
                                    disabled={locked}
                                    onClick={() => navigate(`/cards/${sessionId}/${category.value}`)}
                                >
                                    <Icon name={CATEGORY_ICONS[category.value]} size={26} className="tile__icon" />
                                    <span className="tile__label">{category.label}</span>

                                    {/*
                                      이 여행에서 이 종류로 간 곳 수.
                                      타일 모서리에 띄워 둔다(absolute) — 흐름에 넣으면 숫자가 붙은
                                      타일만 키가 달라져 격자가 들쭉날쭉해진다.
                                    */}
                                    {count > 0 && (
                                        <span className="tile__count" aria-label={`간 곳 ${count}곳`}>
                                            {count}
                                        </span>
                                    )}
                                </button>
                            );
                        })}
                    </div>

                    <p className="hint-link">한 번에 한 곳씩 정해요. 다 보고 나면 또 고를 수 있어요.</p>

                    {visited.length > 0 && (
                        <section className="visited">
                            <h3 className="visited__title">이 여행에서 간 곳</h3>

                            {THROW_CATEGORIES.filter((category) => byCategory[category.value]).map(
                                (category) => (
                                    <div key={category.value} className="visited__group">
                                        <p className="visited__label">
                                            <Icon name={CATEGORY_ICONS[category.value]} size={15} />
                                            {categoryLabel(category.value)}
                                        </p>

                                        <ul className="visited__list">
                                            {byCategory[category.value].map((place) => (
                                                <li key={place.id} className="visited__item">
                                                    <span className="visited__name">{place.placeName}</span>

                                                    <button
                                                        type="button"
                                                        className={`icon-btn icon-btn--heart${place.wished ? ' is-on' : ''}`}
                                                        onClick={() => toggleWish(place)}
                                                        disabled={busyHeart === place.id}
                                                        aria-pressed={place.wished}
                                                        aria-label={`${place.placeName} ${place.wished ? '찜 해제' : '찜하기'}`}
                                                        title={place.wished ? '찜 해제' : '찜하기'}
                                                    >
                                                        <Icon name="heart" size={16} filled={place.wished} />
                                                    </button>

                                                    <button
                                                        type="button"
                                                        className="icon-btn"
                                                        onClick={() => undoVisit(place)}
                                                        disabled={busy === place.id}
                                                        aria-label={`${place.placeName} 간 곳에서 빼기`}
                                                        title="간 곳에서 빼기"
                                                    >
                                                        <Icon name="undo" size={16} />
                                                    </button>
                                                </li>
                                            ))}
                                        </ul>
                                    </div>
                                ),
                            )}
                        </section>
                    )}
                </>
            )}
        </>
    );
}
