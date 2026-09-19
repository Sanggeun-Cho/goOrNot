import { Routes, Route, Navigate } from 'react-router-dom';

import AppShell from './components/AppShell.jsx';
import RequireAuth from './components/RequireAuth.jsx';
import HomePage from './routes/HomePage.jsx';
import LoginPage from './routes/LoginPage.jsx';
import SignupPage from './routes/SignupPage.jsx';
import MyTripsPage from './routes/MyTripsPage.jsx';
import RegionSearchPage from './routes/RegionSearchPage.jsx';
import CategoryPage from './routes/CategoryPage.jsx';
import PrivacyPage from './routes/PrivacyPage.jsx';

/**
 * 라우트 표.
 *
 * basename 은 main.jsx 의 BrowserRouter 가 '/app' 으로 쥐고 있으므로
 * 여기 경로는 전부 '/app' 이 빠진 형태로 적는다. ('/' → 실제 URL 은 '/app')
 *
 * ⚠ 경로를 추가하면 DefaultPageController 의 @GetMapping 목록에도 같이 넣어야 한다.
 *   안 그러면 주소창에 직접 치거나 새로고침할 때 404 가 난다 (배포 환경에서만 티가 난다).
 *
 * ── 비로그인에게 열어두는 범위 ──
 * [2026-09-18] 결정: 메인(던지기)까지만. 던져서 지역이 나오는 재미까지는 그냥 보여주고,
 * 그 뒤로 더 가려면 로그인하게 한다. 검색(/search)·내 여행(/trips)은 가드 안쪽이고,
 * 카테고리(/category/:id)는 로그인 후 세션을 계정에 붙여야 해서 화면 안에서 직접 막는다.
 */
export default function App() {
    return (
        <AppShell>
            <Routes>
                <Route path="/" element={<HomePage />} />

                {/* 세션 id 를 URL 에 둔다. 새로고침해도 어느 여행을 보는지 잃지 않는다 */}
                <Route path="/category/:sessionId" element={<CategoryPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/signup" element={<SignupPage />} />

                {/* 가입 전에 읽을 수 있어야 하므로 가드를 두르지 않는다 */}
                <Route path="/privacy" element={<PrivacyPage />} />

                {/* 로그인이 필요한 화면은 가드를 한 겹 두른다 */}
                <Route
                    path="/search"
                    element={
                        <RequireAuth>
                            <RegionSearchPage />
                        </RequireAuth>
                    }
                />
                <Route
                    path="/trips"
                    element={
                        <RequireAuth>
                            <MyTripsPage />
                        </RequireAuth>
                    }
                />

                {/* 없는 경로는 조용히 메인으로 돌린다 */}
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </AppShell>
    );
}
