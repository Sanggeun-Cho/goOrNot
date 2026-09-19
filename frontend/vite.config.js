import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * 빌드 결과를 Spring 의 static 폴더 안으로 떨어뜨린다.
 *
 * 프론트를 별도 호스트(Vercel 등)에 올리지 않는 이유:
 * 같은 오리진이면 CORS 설정이 필요 없고, 인증 필터를 건드릴 일도 없다.
 * 토큰을 크로스오리진으로 주고받는 문제(쿠키 SameSite 등)도 생기지 않는다.
 * 배포도 jar 하나로 끝나서 EC2 한 곳만 보면 된다.
 *
 * base 가 '/app/' 인 이유:
 * 지금은 기존 Thymeleaf 화면(/ , /user/**)이 살아 있어 경로가 겹치면 안 된다.
 * React 화면이 전부 서면 그때 '/' 로 옮기면서 Thymeleaf 쪽을 정리한다.
 */
export default defineConfig({
    plugins: [react()],

    base: '/app/',

    build: {
        outDir: '../src/main/resources/static/app',

        // 이전 빌드 산출물(해시가 다른 파일들)이 쌓이지 않도록 매번 비운다.
        // outDir 이 프로젝트 밖이라 Vite 가 확인을 요구하므로 명시적으로 켠다
        emptyOutDir: true,

        // 배포 번들에 원본 경로가 딸려 나가지 않게 한다
        sourcemap: false,
    },

    server: {
        /*
         * Vite 기본 포트(5173)를 쓰지 않는 이유:
         * Windows 가 Hyper-V/WSL 용으로 잡아두는 예약 구간(5141~5240)에 5173 이 들어가 있어서
         * EACCES(permission denied) 로 뜨지 않는다. 관리자 권한으로 예약을 풀 수도 있지만
         * 시스템 설정을 건드리는 일이라 그냥 예약 구간 밖의 포트를 쓴다.
         *
         * 현재 예약 구간: 4929~5028 / 5141~5240 / 6096~6195 / 6340~6439 / 12441~12540 / 50000~50059
         * (구간은 재부팅 때 바뀔 수 있다. 또 EACCES 가 나면
         *  netsh interface ipv4 show excludedportrange protocol=tcp 로 확인하고 포트를 옮긴다)
         */
        port: 5300,

        // 포트가 막혔을 때 조용히 옆 포트로 옮겨가면 프록시 디버깅이 헷갈린다. 차라리 실패시킨다
        strictPort: true,

        // 개발 중에는 Vite 가 화면을, Spring 이 API 를 맡는다.
        // 프록시를 두면 브라우저 입장에서는 같은 오리진이라 운영과 동일하게 동작한다
        proxy: {
            '/api': {
                target: 'http://localhost:8081',
                changeOrigin: false,
            },

            // 디자인 토큰(common.css)을 기존 화면과 공유한다. 사본을 두지 않기 위한 프록시
            '/css': {
                target: 'http://localhost:8081',
                changeOrigin: false,
            },

            /*
             * '/app/css' 도 같이 받는 이유:
             * 개발 서버는 index.html 안의 절대경로 앞에 base('/app/')를 붙인다.
             * 그래서 <link href="/css/common.css"> 가 실제로는 /app/css/common.css 로 요청된다.
             * 반면 빌드 결과물에서는 (빌드 시점에 그 파일이 없어서) 경로가 그대로 남는다.
             *   개발  : /app/css/common.css  → 여기서 /app 을 떼고 Spring 으로
             *   운영  : /css/common.css      → Spring 이 바로 응답
             * 양쪽 다 결국 같은 파일 하나를 본다.
             *
             * ⚠ vite build 로그에 "/css/common.css doesn't exist at build time" 경고가 사라지면
             *   빌드 쪽도 경로를 고쳐 쓰기 시작했다는 뜻이므로 이 설정을 다시 봐야 한다.
             */
            '/app/css': {
                target: 'http://localhost:8081',
                changeOrigin: false,
                rewrite: (path) => path.replace(/^\/app/, ''),
            },

            // 아이콘도 같은 이유로 원본(static/**)을 그대로 본다. css 와 짝이 같다
            '/images': {
                target: 'http://localhost:8081',
                changeOrigin: false,
            },
            '/app/images': {
                target: 'http://localhost:8081',
                changeOrigin: false,
                rewrite: (path) => path.replace(/^\/app/, ''),
            },

            // 브라우저가 링크 없이도 자동으로 찾는 경로라 루트에도 하나 둔다
            '/favicon.ico': {
                target: 'http://localhost:8081',
                changeOrigin: false,
            },
            '/app/favicon.ico': {
                target: 'http://localhost:8081',
                changeOrigin: false,
                rewrite: (path) => path.replace(/^\/app/, ''),
            },
        },
    },
});
