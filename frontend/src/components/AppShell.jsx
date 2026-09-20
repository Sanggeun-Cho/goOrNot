import { Link, useLocation } from 'react-router-dom';

import TabBar from './TabBar.jsx';

/**
 * 헤더 + 본문 + 푸터 + 하단 탭바 껍데기.
 *
 * 로그인 여부 토글은 common.css 의 body[data-auth] 규칙 대신 조건부 렌더링으로 한다.
 * React 가 상태를 이미 들고 있는데 body 속성을 또 만지면 진실이 두 군데가 된다.
 *
 * 하단 고정 버튼(.cta)이 있는 화면의 본문 여백은 app.css 의 :has() 가 알아서 잡는다.
 * 셸에 플래그를 넘기지 않는 이유는 화면을 추가할 때 고칠 곳을 늘리지 않기 위해서다.
 */

/**
 * 탭바를 감추는 화면.
 *
 * 기준은 "돌아갈 곳이 정해져 있는 흐름인가" 다.
 *   로그인·가입 : 끝내거나 그만두거나 둘 중 하나다. 탭으로 새는 길을 열어둘 이유가 없다
 *   카드        : 한 판을 고르는 중이다. 화면 안의 뒤로가기가 유일한 출구다
 *
 * 반대로 여행 상세(/category)와 처리방침(/privacy)은 "보는 화면" 이라 탭바를 남겨둔다.
 * 읽다가 다른 데로 갈 수 있어야 앱처럼 느껴진다.
 */
const TABLESS = [/^\/login/, /^\/signup/, /^\/cards\//];

export default function AppShell({ children }) {
    const { pathname } = useLocation();
    const tabbed = !TABLESS.some((pattern) => pattern.test(pathname));

    return (
        <div className={`app${tabbed ? ' app--tabbed' : ''}`}>
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

                {/*
                  헤더에 있던 "내 여행 / 로그아웃" 은 하단 탭바와 마이페이지로 옮겼다.
                  같은 이동 수단이 위아래로 두 벌 있으면 어느 쪽이 정식인지 모호해지고,
                  폰에서 헤더는 엄지가 가장 안 닿는 자리다.
                */}
            </header>

            <main className="app__main">{children}</main>

            {/*
              저작권 표시는 어느 화면에서든 닿을 수 있어야 한다. 푸터가 그 자리다.
              탭바는 fixed 라 이 푸터를 가리지 않는다 (본문 아래 여백으로 비켜둔다)

              ⚠ [2026-09-19] "출처: ⓒ한국관광공사" 문구를 추가했다.
                공공데이터 이용조건(제3유형: 출처 표시 + 변경 금지)이 요구하는 표기이고,
                Thymeleaf 화면(layout/base.html)에는 있는데 실제 서비스 화면에만 빠져 있었다.
                기능설명서에 붙일 캡처마다 찍히는 자리라 여기서 맞춰 둔다.

                금지: 공사 로고·"한국관광공사가 제공"처럼 후원/제휴로 읽히는 표현.
                     출처는 지금처럼 텍스트로만 적는다.
            */}
            <footer className="app-footer">
                한국관광공사 TourAPI 4.0 의 정보를 활용합니다.
                <br />
                출처: ⓒ한국관광공사
                <br />
                <Link to="/privacy">개인정보처리방침</Link>
            </footer>

            {tabbed && <TabBar />}
        </div>
    );
}
