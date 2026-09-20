import { NavLink } from 'react-router-dom';

import Icon from './Icon.jsx';

/**
 * 하단 탭바.
 *
 * [2026-09-19 확정] 앱의 뼈대를 탭 3개로 잡는다.
 *   던지기   / : 앱의 시작. 비로그인도 여기까지는 들어온다
 *   내 여행  /trips : 확정된 세션 목록
 *   마이페이지 /me : 계정 + 찜한 곳 + 처리방침 + 로그아웃
 *
 * 왜 탭바인가: 화면 하나에 정보 하나만 두려면 화면 수가 늘어나고, 그러면 화면끼리
 * 오가는 길이 필요하다. 헤더에 링크를 늘리면 폰에서 엄지가 닿지 않는 위치에 쌓인다.
 *
 * ── 검색(/search)에 탭을 주지 않는 이유 [2026-09-19 정정] ──
 * 탭바는 "이 앱이 무엇을 하는 앱인지" 를 한 줄로 말하는 자리다. 거기에 검색을 올리면
 * 던지기와 검색이 같은 급의 두 갈래로 읽히는데, 이 서비스가 내세우는 건 던져서
 * 쏠림을 흩는 쪽이고 검색은 의도적으로 소극 제공하는 보조 경로다(기획안 화면 7).
 * 상시 노출은 그 기조와 정면으로 부딪히므로, 진입은 메인의 작은 링크와
 * 후보 소진 모달에서만 연다. 라우트 자체는 그대로 살아 있다.
 *
 * 하트(찜)에 탭을 따로 주지 않은 이유:
 * 하트는 "여행 중에 하는 일" 이 아니라 "쌓아두고 나중에 보는 것" 이라 성격이 마이페이지에 가깝다.
 *
 * ── 비로그인에게도 그대로 보여준다 ──
 * 내 여행·마이페이지는 로그인이 필요하지만, 탭을 숨기면 "이 앱이 뭘 하는지"
 * 자체가 안 보인다. 눌렀을 때 RequireAuth 가 로그인으로 보내는 편이 낫다.
 */
const TABS = [
    { to: '/', icon: 'dice', label: '던지기', end: true },
    { to: '/trips', icon: 'map', label: '내 여행' },
    { to: '/me', icon: 'user', label: '마이' },
];

export default function TabBar() {
    return (
        <nav className="tabbar" aria-label="주요 화면">
            {TABS.map((tab) => (
                <NavLink
                    key={tab.to}
                    to={tab.to}
                    /* end 를 주지 않으면 '/' 가 모든 경로의 접두사라 항상 활성으로 보인다.
                       나머지 탭은 end 를 빼서 하위 경로(/me/... 등)에서도 활성이 유지된다 */
                    end={tab.end}
                    className={({ isActive }) => `tabbar__item${isActive ? ' is-active' : ''}`}
                >
                    <Icon name={tab.icon} size={22} />
                    <span className="tabbar__label">{tab.label}</span>
                </NavLink>
            ))}
        </nav>
    );
}
