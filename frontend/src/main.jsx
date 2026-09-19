import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import App from './App.jsx';
import { AuthProvider } from './lib/AuthContext.jsx';
import './styles/app.css';

/**
 * basename 을 '/app' 으로 두는 이유:
 * 지금은 기존 Thymeleaf 화면(/ , /user/**)이 살아 있어 경로가 겹치면 안 된다.
 * React 화면이 전부 서면 '/' 로 옮기면서 Thymeleaf 쪽을 정리한다.
 * (vite.config.js 의 base 와 반드시 같이 움직여야 한다)
 */
createRoot(document.getElementById('root')).render(
    <StrictMode>
        <BrowserRouter basename="/app">
            <AuthProvider>
                <App />
            </AuthProvider>
        </BrowserRouter>
    </StrictMode>,
);
