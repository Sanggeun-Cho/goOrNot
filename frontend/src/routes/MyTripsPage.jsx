import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import Icon from '../components/Icon.jsx';
import { formatDate } from '../lib/place.js';
import { fetchSavedPlaces, fetchSessions } from '../lib/trip.js';

/**
 * 화면 6 · 내 여행.
 *
 * 레이아웃은 wireframes.svg 화면 6 을 따른다. (지역명 / 날짜 / "간 곳 N" 배지)
 *
 * ── 여행 = 확정된 던지기 세션 ──
 * 따로 "여행" 표를 두지 않는다. 갈래를 눌러 지역이 확정되는 순간(CONFIRMED)
 * 그 세션이 곧 하나의 여행이다. 그래서 목록은 세션 목록을 status 로 거른 것이고,
 * 여행을 여는 것은 그 세션의 카테고리 화면으로 들어가는 것과 같다.
 *
 * 여행에는 종료 개념이 없다. 나중에 같은 지역을 또 가더라도 그때는 새로 던져
 * 새 세션이 생기므로, 예전 여행을 닫아줄 필요가 없다.
 *
 * 빈 상태를 따로 만드는 이유: 가입 직후 사용자가 가장 먼저 보게 될 화면이라
 * 여기서 "아무것도 없음" 으로 끝나면 다음에 뭘 해야 할지 알 수 없다. 메인으로 돌려보낸다.
 */
export default function MyTripsPage() {
    const [trips, setTrips] = useState(null);
    const [counts, setCounts] = useState({});
    const [error, setError] = useState('');

    useEffect(() => {
        let alive = true;

        /*
         * 간 곳 개수는 여행마다 따로 부르지 않고 계정 전체를 한 번에 받아 세션별로 센다.
         * 여행이 열 개면 요청이 열한 번 나가는 구조가 되는 걸 피하려는 것이다.
         * (개수가 크게 늘면 서버에 집계 API 를 두는 편이 맞다)
         */
        Promise.all([fetchSessions({ status: 'CONFIRMED' }), fetchSavedPlaces({ visited: true })])
            .then(([sessions, places]) => {
                if (!alive) return;

                const tally = {};
                (places ?? []).forEach((place) => {
                    tally[place.throwSessionId] = (tally[place.throwSessionId] ?? 0) + 1;
                });

                setCounts(tally);
                setTrips(sessions ?? []);
            })
            .catch((caught) => {
                if (alive) setError(caught.message || '여행을 불러오지 못했습니다.');
            });

        return () => {
            alive = false;
        };
    }, []);

    return (
        <>
            <div className="page-head">
                <h1 className="page-head__title">내 여행</h1>
                <p className="page-head__desc">지역을 정한 여행이 쌓입니다. 눌러서 이어서 골라보세요.</p>
            </div>

            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}

            {!trips && !error && <p className="state">불러오는 중…</p>}

            {trips && trips.length === 0 && (
                <div className="empty">
                    <p className="empty__title">아직 저장한 여행이 없어요</p>
                    <p className="empty__desc">한 번 던져서 첫 여행을 만들어 보세요.</p>

                    <p className="hint-link">
                        <Link to="/">던지러 가기</Link>
                    </p>
                </div>
            )}

            {trips && trips.length > 0 && (
                <div className="row-list">
                    {trips.map((trip) => (
                        <Link key={trip.id} className="row" to={`/category/${trip.id}`}>
                            <span className="row__body">
                                <span className="row__title">{trip.regionName}</span>
                                <span className="row__meta">{formatDate(trip.createdAt)}</span>
                            </span>

                            <span className="badge">간 곳 {counts[trip.id] ?? 0}</span>
                            <Icon name="chevron" size={18} className="row__arrow" />
                        </Link>
                    ))}
                </div>
            )}
        </>
    );
}
