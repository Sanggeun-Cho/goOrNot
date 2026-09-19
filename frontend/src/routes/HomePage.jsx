import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { confirmSession, createRandomSession, drawRegion, recordRound } from '../lib/trip.js';

/** 와이어프레임 화면 1 의 1회 / 3회 / 5회 */
const THROW_COUNTS = [1, 3, 5];

/* ── 카카오맵 ────────────────────────────────────────── */

/**
 * JS 키는 frontend/.env.local 에 둔다(gitignored).
 *
 * ⚠ 이 값은 빌드 결과물에 그대로 박혀 브라우저까지 내려간다. 숨길 수 있는 값이 아니고,
 *   실제 방어선은 카카오 개발자센터 > 플랫폼 > Web 의 "사이트 도메인" 등록이다.
 *   등록되지 않은 곳에서 부르면 카카오가 거부한다. 배포 도메인을 사면 거기도 등록해야 한다.
 */
const KAKAO_JS_KEY = import.meta.env.VITE_KAKAO_JS_KEY;

/** 남한 전체가 들어오는 시점. 던지기 전에는 "어디든 나올 수 있다" 를 보여줘야 한다 */
const KOREA_CENTER = { lat: 36.2, lng: 127.9 };
const KOREA_LEVEL = 13;
/** 지역이 정해졌을 때 들어가는 깊이. 시군구 하나가 화면을 채운다 */
const REGION_LEVEL = 9;

/**
 * 카카오맵 SDK 를 한 번만 받아온다.
 *
 * autoload=false 로 받고 kakao.maps.load() 를 직접 부르는 이유:
 * 기본값(autoload=true)은 스크립트가 붙는 즉시 내부 모듈을 초기화하는데,
 * 그 타이밍이 React 의 렌더와 어긋나면 지도 컨테이너가 아직 없어서 빈 div 에 그린다.
 *
 * 중복 호출에 대비해 진행 중인 Promise 를 공유한다(React StrictMode 는 effect 를 두 번 실행한다).
 */
let sdkLoading = null;

function loadKakaoSdk() {
    if (window.kakao?.maps?.Map) return Promise.resolve(window.kakao);

    if (!sdkLoading) {
        sdkLoading = new Promise((resolve, reject) => {
            const script = document.createElement('script');

            script.async = true;
            script.src =
                `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_JS_KEY}&autoload=false`;

            script.onload = () => window.kakao.maps.load(() => resolve(window.kakao));
            // 키가 틀렸거나 도메인이 등록되지 않았을 때도 여기로 온다
            script.onerror = () => {
                sdkLoading = null;
                reject(new Error('지도를 불러오지 못했습니다.'));
            };

            document.head.appendChild(script);
        });
    }

    return sdkLoading;
}

/**
 * 화면 1 · 메인(던지기) + 화면 2 · 갈래/말래.
 *
 * 레이아웃은 기획안 wireframes.svg 화면 1 을 따른다.
 *   지도 → 횟수 선택 → 던지기 → "지역을 알고 계신가요? 직접 검색"
 *
 * ── 화면 2 를 같은 파일에 둔 이유 ──
 * 갈래/말래는 별도 화면이 아니라 이 화면 위에 뜨는 모달이다. 지도와 남은 횟수를 그대로
 * 보여준 채 물어야 "던져서 하나씩 지워간다" 는 감각이 유지된다. 라우트를 나누면 뒤로가기로
 * 모달을 되살릴 수 있게 되는데, 그건 이미 기록된 회차를 되돌리는 것처럼 보여 위험하다.
 *
 * ── 진행 상태 ──
 *   idle      던지기 전
 *   throwing  세션 생성 · 추첨 대기
 *   deciding  지역이 나왔고 갈래/말래를 묻는 중
 *   exhausted 고른 횟수를 다 썼는데 전부 말래였다
 *
 * ⚠ 지도는 카카오맵 JS 키가 없어 아직 자리표시자다. 실제 좌표(lat/lng)는 이미 받고 있으므로,
 *   키가 들어오면 이 파일에서 핀만 찍으면 된다. 레이아웃이 다시 흔들리지 않도록
 *   자리표시자도 실제 지도와 같은 크기를 차지하게 둔다.
 */
export default function HomePage() {
    const navigate = useNavigate();

    const [count, setCount] = useState(3);
    const [phase, setPhase] = useState('idle');
    const [error, setError] = useState('');

    /** 진행 중인 세션. 던지기를 시작할 때 만들고, 확정하거나 처음으로 돌아갈 때 버린다 */
    const [session, setSession] = useState(null);
    /** 방금 뽑힌 지역 { regionCode, regionName, lat, lng } */
    const [region, setRegion] = useState(null);
    /** 지금까지 쓴 횟수. 말래를 고를 때마다 늘어난다 */
    const [used, setUsed] = useState(0);

    /*
     * 언마운트 뒤에 응답이 와서 setState 하는 걸 막는다(React 경고 + 메모리 누수).
     *
     * ⚠ 마운트될 때 true 로 되돌리는 줄이 반드시 있어야 한다.
     *   StrictMode 는 개발 모드에서 마운트 → 언마운트 → 재마운트를 한 번 돌린다.
     *   초기값만 true 로 두면 그 첫 언마운트에서 false 가 박힌 뒤 영영 돌아오지 않고,
     *   이후 모든 비동기 응답이 `if (!alive.current) return` 에 걸려 버려진다.
     *   화면은 '던지는 중' 에서 멈춘 것처럼 보인다 — [2026-09-19] 실제로 이 증상이 났다.
     */
    const alive = useRef(true);
    useEffect(() => {
        alive.current = true;
        return () => { alive.current = false; };
    }, []);

    const busy = phase === 'throwing';
    const remaining = session ? session.totalCount - used : count;

    /* ── 지도 ────────────────────────────────────────── */

    const mapElRef = useRef(null);
    const mapRef = useRef(null);
    /** 지금 뽑힌 지역의 핀 */
    const markerRef = useRef(null);
    /** 말래로 넘긴 지역들의 흔적. 지워나가는 느낌을 남긴다 */
    const pastRef = useRef([]);
    const [mapError, setMapError] = useState('');

    useEffect(() => {
        if (!KAKAO_JS_KEY) {
            setMapError('지도 키가 설정되지 않았습니다.');
            return undefined;
        }

        let cancelled = false;

        loadKakaoSdk()
            .then((kakao) => {
                // 컨테이너가 사라진 뒤(빠른 화면 이동)에 그리면 예외가 난다
                if (cancelled || !mapElRef.current) return;

                mapRef.current = new kakao.maps.Map(mapElRef.current, {
                    center: new kakao.maps.LatLng(KOREA_CENTER.lat, KOREA_CENTER.lng),
                    level: KOREA_LEVEL,
                });

                /*
                 * 확대·이동을 막는다. 이 지도는 "보여주는 화면" 이지 "탐색하는 화면" 이 아니다.
                 * 사용자가 지도를 끌어 원하는 지역을 찾기 시작하면 던지기라는 규칙 자체가 무너진다.
                 * (지역을 직접 고르고 싶으면 아래 "직접 검색" 이 정식 경로다)
                 */
                mapRef.current.setDraggable(false);
                mapRef.current.setZoomable(false);
            })
            .catch(() => {
                if (!cancelled) setMapError('지도를 불러오지 못했습니다.');
            });

        return () => {
            cancelled = true;
        };
    }, []);

    /** 뽑힌 지역으로 핀을 옮긴다 */
    const moveToRegion = useCallback((next) => {
        const kakao = window.kakao;
        const map = mapRef.current;
        if (!kakao || !map || next?.lat == null || next?.lng == null) return;

        const position = new kakao.maps.LatLng(next.lat, next.lng);

        if (!markerRef.current) {
            markerRef.current = new kakao.maps.Marker({ position, map });
        } else {
            markerRef.current.setPosition(position);
            markerRef.current.setMap(map);
        }

        map.setLevel(REGION_LEVEL);
        // panTo 는 SDK 가 알아서 부드럽게 움직인다. 직접 애니메이션을 만들지 않는다
        map.panTo(position);
    }, []);

    /** 말래로 넘긴 자리에 옅은 점을 남기고, 다시 전국 시점으로 뺀다 */
    const markPassed = useCallback((passed) => {
        const kakao = window.kakao;
        const map = mapRef.current;
        if (!kakao || !map || passed?.lat == null || passed?.lng == null) return;

        const circle = new kakao.maps.Circle({
            center: new kakao.maps.LatLng(passed.lat, passed.lng),
            radius: 6000,
            strokeWeight: 0,
            fillColor: '#8B8980',
            fillOpacity: 0.35,
            map,
        });

        pastRef.current.push(circle);

        markerRef.current?.setMap(null);
        map.setLevel(KOREA_LEVEL);
        map.panTo(new kakao.maps.LatLng(KOREA_CENTER.lat, KOREA_CENTER.lng));
    }, []);

    /** 지도를 처음 상태로 되돌린다 */
    const resetMap = useCallback(() => {
        const kakao = window.kakao;
        const map = mapRef.current;

        markerRef.current?.setMap(null);
        pastRef.current.forEach((circle) => circle.setMap(null));
        pastRef.current = [];

        if (kakao && map) {
            map.setLevel(KOREA_LEVEL);
            map.setCenter(new kakao.maps.LatLng(KOREA_CENTER.lat, KOREA_CENTER.lng));
        }
    }, []);

    /* ── 던지기 ──────────────────────────────────────── */

    async function handleThrow() {
        setError('');
        setPhase('throwing');

        try {
            // 세션이 없으면(첫 던지기) 먼저 연다. 두 번째부터는 같은 세션에 회차를 쌓는다
            const current = session ?? {
                id: (await createRandomSession(count)).id,
                totalCount: count,
            };

            if (!alive.current) return;
            setSession(current);

            const drawn = await drawRegion(current.id);

            if (!alive.current) return;
            moveToRegion(drawn);
            setRegion(drawn);
            setPhase('deciding');
        } catch (caught) {
            if (!alive.current) return;
            setError(caught.message || '던지지 못했습니다. 잠시 후 다시 시도해 주세요.');
            setPhase('idle');
        }
    }

    /* ── 갈래 / 말래 ─────────────────────────────────── */

    /** @param {'GO'|'AGAIN'} choice 서버 ThrowChoice enum 과 같은 값이어야 한다. 말래가 AGAIN 이다 */
    async function handleChoice(choice) {
        if (!session || !region) return;

        setError('');
        setPhase('throwing');

        try {
            // 어느 쪽을 골랐든 회차부터 남긴다. 이 기록이 다음 추첨의 제외 목록이자
            // 확정 단계에서 "정말 던져서 뽑은 지역인지" 를 대조하는 근거가 된다
            await recordRound({
                sessionId: session.id,
                regionCode: region.regionCode,
                lat: region.lat,
                lng: region.lng,
                choice,
            });

            if (!alive.current) return;

            if (choice === 'GO') {
                await confirmSession({ sessionId: session.id, regionCode: region.regionCode });

                if (!alive.current) return;

                // 로그인 게이트는 도착지인 카테고리 화면이 맡는다(기획안 5번).
                // 여기서 로그인을 먼저 묻지 않는 이유는 경로마다 게이트가 흩어지면
                // 검색으로 들어온 흐름과 규칙이 달라지기 때문이다
                navigate(`/category/${session.id}`);
                return;
            }

            // 말래 — 한 번 썼다
            const nextUsed = used + 1;
            setUsed(nextUsed);
            markPassed(region);
            setRegion(null);

            if (nextUsed >= session.totalCount) {
                setPhase('exhausted');
                return;
            }

            // 남은 횟수가 있으면 곧바로 다음 지역을 뽑는다. 모달을 닫았다 다시 열게 하면
            // "던지는 중" 이라는 흐름이 끊긴다
            const drawn = await drawRegion(session.id);

            if (!alive.current) return;
            moveToRegion(drawn);
            setRegion(drawn);
            setPhase('deciding');
        } catch (caught) {
            if (!alive.current) return;
            setError(caught.message || '처리하지 못했습니다. 잠시 후 다시 시도해 주세요.');
            setPhase(region ? 'deciding' : 'idle');
        }
    }

    /** 처음으로. 쓰던 세션은 버리고 새로 던진다 */
    function handleReset() {
        setSession(null);
        setRegion(null);
        setUsed(0);
        setError('');
        setPhase('idle');
        resetMap();
    }

    /**
     * 횟수를 바꾸면 진행 중이던 세션은 버린다.
     * 이미 3회짜리로 열어둔 세션에 5회를 적용할 방법이 없고, 남은 횟수 표시가
     * 실제 서버 기록과 어긋나기 시작하면 사용자가 속은 느낌을 받는다.
     */
    function handleCountChange(value) {
        setCount(value);
        if (session) handleReset();
    }

    return (
        <>
            <div className="page-head">
                <h1 className="page-head__title">오늘, 어디로 갈래?</h1>
                <p className="page-head__desc">
                    정하지 말고 던져 보세요. 안 가본 곳이 나옵니다.
                </p>
            </div>

            <div className="stack stack--5">
                <div className="map-stage">
                    {/* SDK 가 이 div 안에 지도를 그린다. 자리를 미리 차지하고 있어야
                        지도가 늦게 떠도 아래 버튼들이 밀렸다 돌아오지 않는다 */}
                    <div className="map-stage__canvas" ref={mapElRef} />

                    {mapError && (
                        <div className="map-stage__placeholder">
                            <span>{mapError}</span>
                            <span style={{ fontSize: '.74rem', opacity: 0.75 }}>
                                지도는 없어도 던지기는 됩니다.
                            </span>
                        </div>
                    )}
                </div>

                <div>
                    <p className="field__label" style={{ marginBottom: 'var(--space-2)' }}>
                        몇 번 던질까요?
                    </p>

                    {/* 라디오 그룹으로 읽히게 role 을 준다. 보기에는 버튼이지만 하는 일은 선택이다 */}
                    <div className="seg" role="radiogroup" aria-label="던지기 횟수">
                        {THROW_COUNTS.map((value) => (
                            <button
                                key={value}
                                type="button"
                                role="radio"
                                aria-checked={count === value}
                                className={`seg__item${count === value ? ' is-active' : ''}`}
                                onClick={() => handleCountChange(value)}
                                disabled={busy}
                            >
                                {value}회
                            </button>
                        ))}
                    </div>
                </div>

                {/* 던지는 중에만 남은 횟수를 보여준다. 시작 전에는 위 선택지가 곧 남은 횟수다 */}
                {session && phase !== 'exhausted' && (
                    <p className="throw-count" aria-live="polite">
                        남은 기회 <strong>{remaining}</strong>번
                    </p>
                )}

                {error && (
                    <p className="form-error" role="alert">
                        {error}
                    </p>
                )}
            </div>

            <p className="hint-link">
                지역을 이미 알고 계신가요? <Link to="/search">직접 검색</Link>
            </p>

            {/* 엄지가 닿는 자리에 고정한다. 본문 아래 여백은 app.css 가 알아서 확보한다 */}
            <div className="cta">
                <div className="cta__inner">
                    <button
                        type="button"
                        className={`btn btn--accent btn--lg btn--block${busy ? ' is-loading' : ''}`}
                        onClick={phase === 'exhausted' ? handleReset : handleThrow}
                        disabled={busy || phase === 'deciding'}
                    >
                        {phase === 'exhausted' ? '다시 던지기' : '던지기'}
                    </button>
                </div>
            </div>

            {/* ── 화면 2 · 갈래 / 말래 ───────────────────── */}
            {phase === 'deciding' && region && (
                <ResultModal
                    region={region}
                    remaining={remaining}
                    busy={busy}
                    onChoice={handleChoice}
                />
            )}

            {phase === 'exhausted' && (
                <ExhaustedModal
                    totalCount={session?.totalCount ?? count}
                    onRetry={handleReset}
                    onSearch={() => navigate('/search')}
                />
            )}
        </>
    );
}

/* ── 모달 ────────────────────────────────────────────── */

/**
 * 뽑힌 지역을 보여주고 갈래/말래를 묻는다.
 *
 * 바깥을 눌러 닫는 동작은 일부러 넣지 않았다. 이미 서버에서 한 번 뽑힌 회차라,
 * 아무것도 고르지 않고 사라지면 화면과 서버 기록이 어긋난다. 닫는 길은 두 버튼뿐이다.
 */
function ResultModal({ region, remaining, busy, onChoice }) {
    return (
        <div className="modal" role="dialog" aria-modal="true" aria-labelledby="throw-result">
            <div className="modal__panel">
                <p className="modal__eyebrow">여기 어때요?</p>

                <h2 className="modal__title" id="throw-result">
                    {region.regionName}
                </h2>

                <p className="modal__desc">
                    {remaining > 1
                        ? `마음에 안 들면 ${remaining - 1}번 더 던질 수 있어요.`
                        : '마지막 기회예요.'}
                </p>

                <div className="modal__actions">
                    <button
                        type="button"
                        className="btn btn--ghost btn--lg"
                        onClick={() => onChoice('AGAIN')}
                        disabled={busy}
                    >
                        말래
                    </button>

                    <button
                        type="button"
                        className={`btn btn--primary btn--lg${busy ? ' is-loading' : ''}`}
                        onClick={() => onChoice('GO')}
                        disabled={busy}
                    >
                        갈래
                    </button>
                </div>
            </div>
        </div>
    );
}

/** 고른 횟수를 전부 말래로 넘겼을 때. 막다른 길로 두지 않고 두 갈래를 준다 */
function ExhaustedModal({ totalCount, onRetry, onSearch }) {
    return (
        <div className="modal" role="dialog" aria-modal="true" aria-labelledby="throw-exhausted">
            <div className="modal__panel">
                <h2 className="modal__title" id="throw-exhausted">
                    {totalCount}번 다 던졌어요
                </h2>

                <p className="modal__desc">
                    마음에 드는 곳이 없었네요. 다시 던지거나, 가고 싶은 곳을 직접 고를 수 있어요.
                </p>

                <div className="modal__actions">
                    <button type="button" className="btn btn--ghost btn--lg" onClick={onSearch}>
                        직접 검색
                    </button>

                    <button type="button" className="btn btn--primary btn--lg" onClick={onRetry}>
                        다시 던지기
                    </button>
                </div>
            </div>
        </div>
    );
}
