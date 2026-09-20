import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import Icon from '../components/Icon.jsx';
import { directionsUrl } from '../lib/place.js';
import { categoryLabel, fetchSavedPlaces, fetchSessions, markPlace } from '../lib/trip.js';

/**
 * 찜한 곳.
 *
 * [2026-09-19] 하트를 누를 수는 있는데 볼 데가 없던 문제를 여기서 푼다.
 * 마이페이지 안쪽이고, 탭을 따로 주지는 않았다 (TabBar.jsx 주석 참고).
 *
 * ── 여행 상세의 "간 곳" 과 다른 점 ──
 * 간 곳은 여행 하나에 묶여 있지만 찜은 계정 전체를 가로지른다.
 * "작년 속초에서 찜해둔 횟집" 이 올해 속초를 던졌을 때 떠올라야 쓸모가 있기 때문이다.
 * 그래서 세션 필터 없이 wished 만으로 받고, 어느 여행에서 찜했는지는 줄 아래에 적는다.
 *
 * ── 해제를 DELETE 로 하지 않는 이유 ──
 * DELETE /api/saved-place 는 행 전체를 Soft Delete 한다. 그 장소를 실제로 다녀와서
 * visited 까지 켜져 있으면 하트만 끄려다 "간 곳" 기록까지 같이 지워진다.
 * 그래서 wished: false 만 보내는 upsert 로 끈다. 두 표시가 모두 꺼지면 서버가 알아서 지운다.
 */
export default function WishlistPage() {
    const navigate = useNavigate();

    const [places, setPlaces] = useState(null);

    /** 세션 ID → 지역명. "어디서 찜했더라" 를 줄 아래 한 줄로 알려주려고 같이 받는다 */
    const [regions, setRegions] = useState({});
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(null);

    useEffect(() => {
        let alive = true;

        Promise.all([fetchSavedPlaces({ wished: true }), fetchSessions({ status: 'CONFIRMED' })])
            .then(([wished, sessions]) => {
                if (!alive) return;

                const byId = {};
                (sessions ?? []).forEach((session) => {
                    byId[session.id] = session.regionName;
                });

                setRegions(byId);
                setPlaces(wished ?? []);
            })
            .catch((caught) => {
                if (alive) setError(caught.message || '찜한 곳을 불러오지 못했습니다.');
            });

        return () => {
            alive = false;
        };
    }, []);

    async function unwish(place) {
        setBusy(place.id);
        setError('');

        try {
            await markPlace({
                sessionId: place.throwSessionId,
                category: place.category,
                place,
                wished: false,
            });

            // 서버가 성공을 준 뒤에만 목록에서 뺀다. 먼저 지우면 실패했을 때 되돌릴 게 없다
            setPlaces((prev) => prev.filter((row) => row.id !== place.id));
        } catch (caught) {
            setError(caught.message || '찜을 해제하지 못했습니다.');
        } finally {
            setBusy(null);
        }
    }

    return (
        <>
            <div className="topbar">
                <button
                    type="button"
                    className="topbar__back"
                    onClick={() => navigate('/me')}
                    aria-label="마이페이지로"
                >
                    <Icon name="back" size={20} />
                </button>

                <h1 className="topbar__title">찜한 곳</h1>

                {places && places.length > 0 && <span className="topbar__sub">{places.length}곳</span>}
            </div>

            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}

            {!places && !error && <p className="state">불러오는 중…</p>}

            {places && places.length === 0 && (
                <div className="empty">
                    <p className="empty__title">아직 찜한 곳이 없어요</p>
                    <p className="empty__desc">카드에서 하트를 누르면 여기에 모입니다.</p>

                    <p className="hint-link">
                        <Link to="/">던지러 가기</Link>
                    </p>
                </div>
            )}

            {places && places.length > 0 && (
                <ul className="row-list">
                    {places.map((place) => (
                        <li key={place.id} className="row">
                            <span className="row__body">
                                <span className="row__title">{place.placeName}</span>
                                <span className="row__meta">
                                    {[regions[place.throwSessionId], categoryLabel(place.category)]
                                        .filter(Boolean)
                                        .join(' · ')}
                                </span>
                            </span>

                            <a
                                className="icon-btn"
                                href={directionsUrl(place)}
                                target="_blank"
                                rel="noreferrer"
                                aria-label={`${place.placeName} 길찾기`}
                                title="길찾기"
                            >
                                <Icon name="navigate" size={18} />
                            </a>

                            <button
                                type="button"
                                className="icon-btn icon-btn--heart is-on"
                                onClick={() => unwish(place)}
                                disabled={busy === place.id}
                                aria-label={`${place.placeName} 찜 해제`}
                                title="찜 해제"
                            >
                                <Icon name="heart" size={18} filled />
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </>
    );
}
