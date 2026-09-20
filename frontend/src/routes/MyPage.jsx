import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import Icon from '../components/Icon.jsx';
import { fetchMe } from '../lib/auth.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { fetchSavedPlaces, fetchSessions } from '../lib/trip.js';

/**
 * 마이페이지.
 *
 * [2026-09-19] 탭바 4번째 칸. 여행 중에 쓰는 화면이 아니라 "내 것들이 모여 있는 곳" 이다.
 *   - 내가 누구인지 (아이디)
 *   - 지금까지 쌓인 것 (여행 수 · 간 곳 수)
 *   - 찜한 곳으로 가는 길  ← 기존에 하트를 볼 데가 없던 문제를 여기서 푼다
 *   - 처리방침 · 로그아웃
 *
 * 숫자 두 개(여행·간 곳)를 여기 둔 이유는, 이 앱에서 "쌓인다" 는 감각이 유일하게
 * 보이는 자리가 여기이기 때문이다. 던지기 화면은 늘 처음처럼 비어 있어야 하고
 * 내 여행은 목록이라 총합이 안 보인다.
 */
export default function MyPage() {
    const navigate = useNavigate();
    const { logout } = useAuth();

    const [me, setMe] = useState(null);
    const [counts, setCounts] = useState(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        let alive = true;

        /*
         * 세 요청을 한 번에 보낸다. allSettled 를 쓰는 이유:
         * 숫자 하나를 못 받았다고 화면 전체가 에러로 바뀌면 로그아웃 버튼까지 못 누른다.
         * 마이페이지는 "막혔을 때 나가는 문" 이기도 해서 부분 실패를 견뎌야 한다.
         */
        Promise.allSettled([
            fetchMe(),
            fetchSessions({ status: 'CONFIRMED' }),
            fetchSavedPlaces({ visited: true }),
        ]).then(([profile, trips, visited]) => {
            if (!alive) return;

            if (profile.status === 'fulfilled') {
                setMe(profile.value);
            } else {
                setError(profile.reason?.message || '내 정보를 불러오지 못했습니다.');
            }

            setCounts({
                trips: trips.status === 'fulfilled' ? (trips.value?.length ?? 0) : null,
                visited: visited.status === 'fulfilled' ? (visited.value?.length ?? 0) : null,
            });
        });

        return () => {
            alive = false;
        };
    }, []);

    async function handleLogout() {
        setBusy(true);
        await logout();
        navigate('/', { replace: true });
    }

    return (
        <>
            <div className="page-head">
                <h1 className="page-head__title">마이페이지</h1>
                {/*
                  아직 안 왔을 때 자리만 잡아둔다. 글자가 뒤늦게 끼어들면 아래가 밀린다.

                  ⚠ username(로그인 ID)이 아니라 name(이름)이다. 화면에 ID 를 띄우면
                    어깨너머나 캡처로 계정 절반이 새어나간다. 가입 때 이름을 따로 받는
                    이유가 이것이다.
                */}
                <p className="page-head__desc">{me ? `${me.name} 님` : ' '}</p>
            </div>

            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}

            {/* 쌓인 것. 두 칸 고정이라 값이 늦게 와도 레이아웃이 흔들리지 않는다 */}
            <div className="stat-grid">
                <div className="stat">
                    <span className="stat__value">{counts?.trips ?? '–'}</span>
                    <span className="stat__label">다녀온 여행</span>
                </div>
                <div className="stat">
                    <span className="stat__value">{counts?.visited ?? '–'}</span>
                    <span className="stat__label">간 곳</span>
                </div>
            </div>

            <nav className="menu">
                <Link className="menu__item" to="/wishlist">
                    <Icon name="heart" size={20} />
                    <span className="menu__label">찜한 곳</span>
                    <Icon name="chevron" size={18} className="menu__arrow" />
                </Link>

                <Link className="menu__item" to="/trips">
                    <Icon name="map" size={20} />
                    <span className="menu__label">내 여행</span>
                    <Icon name="chevron" size={18} className="menu__arrow" />
                </Link>

                <Link className="menu__item" to="/privacy">
                    <Icon name="doc" size={20} />
                    <span className="menu__label">개인정보처리방침</span>
                    <Icon name="chevron" size={18} className="menu__arrow" />
                </Link>

                <button className="menu__item" type="button" onClick={handleLogout} disabled={busy}>
                    <Icon name="logout" size={20} />
                    <span className="menu__label">로그아웃</span>
                </button>
            </nav>
        </>
    );
}
