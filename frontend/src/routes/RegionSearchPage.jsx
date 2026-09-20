import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import Icon from '../components/Icon.jsx';
import { searchRegions, createSearchSession } from '../lib/trip.js';

/**
 * 화면 7 · 지역 직접 검색.
 *
 * 기획안대로 "의도적으로 소극 제공" 하는 화면이다. 검색을 잘 만들수록
 * 이 서비스가 내세우는 오버투어리즘 분산이라는 명분과 정면으로 부딪힌다.
 * 그래서 메인에서도 작은 글씨 링크로만 걸어두었고, 여기서도 자동완성이나
 * 인기 지역 추천 같은 "더 쉽게 고르게 하는" 장치는 넣지 않는다.
 *
 * 지역을 고르면 SEARCH 세션이 바로 CONFIRMED 로 생성된다(던지기를 건너뛴 경로).
 *
 * ── 로그인 ──
 * [2026-09-18] 결정으로 이 화면은 로그인해야 들어온다(App.jsx 의 RequireAuth).
 * 비로그인에게 열어두는 건 던지기까지다. 검색은 "이미 갈 곳을 정한 사람" 의 경로라
 * 후크로서의 값어치가 없고, 오히려 던져 보지도 않고 아는 지역을 찍는 지름길이 된다.
 * 그래서 로그인한 사람만 쓸 수 있게 뒀다.
 */
const DEBOUNCE_MS = 250;

export default function RegionSearchPage() {
    const navigate = useNavigate();

    const [keyword, setKeyword] = useState('');
    const [results, setResults] = useState(null); // null = 아직 검색 전
    const [searching, setSearching] = useState(false);
    const [error, setError] = useState('');
    const [selectingCode, setSelectingCode] = useState('');

    /*
     * 타이핑 중에는 요청이 겹친다. "영" 의 응답이 "영양" 보다 늦게 도착하면
     * 화면에는 한 글자짜리 결과가 남는다. 요청마다 번호를 매겨 마지막 것만 반영한다.
     * (AbortController 로 끊는 방법도 있지만, 이미 떠난 요청을 취소한다고
     *  서버 일이 줄지는 않고 코드만 늘어난다. 늦게 온 응답을 버리는 쪽이 단순하다)
     */
    const latestRequest = useRef(0);

    useEffect(() => {
        const trimmed = keyword.trim();

        if (!trimmed) {
            setResults(null);
            setSearching(false);
            setError('');
            return;
        }

        setSearching(true);

        const timer = setTimeout(async () => {
            const requestId = latestRequest.current + 1;
            latestRequest.current = requestId;

            try {
                const found = await searchRegions(trimmed);

                if (latestRequest.current !== requestId) return;

                setResults(found ?? []);
                setError('');
            } catch (caught) {
                if (latestRequest.current !== requestId) return;

                setResults([]);
                setError(caught.message || '검색하지 못했습니다.');
            } finally {
                if (latestRequest.current === requestId) setSearching(false);
            }
        }, DEBOUNCE_MS);

        // 글자가 더 들어오면 직전 예약은 취소한다
        return () => clearTimeout(timer);
    }, [keyword]);

    async function handleSelect(region) {
        if (selectingCode) return;

        setSelectingCode(region.code);
        setError('');

        try {
            const { id } = await createSearchSession(region);
            navigate(`/category/${id}`);
        } catch (caught) {
            setError(caught.message || '지역을 확정하지 못했습니다.');
            setSelectingCode('');
        }
    }

    return (
        <>
            {/*
              [2026-09-19] 탭바에서 검색을 내리면서 뒤로가기 줄을 되살렸다.
              이제 이 화면으로 들어오는 길은 메인의 작은 링크와 후보 소진 모달뿐이라
              양쪽 다 "메인에서 왔다" 가 참이다. navigate(-1) 대신 경로로 보내는 이유는
              CardsPage 와 같다 — 히스토리가 아니라 흐름상 돌아갈 곳으로 가야 한다.
            */}
            <div className="topbar">
                <button
                    type="button"
                    className="topbar__back"
                    onClick={() => navigate('/')}
                    aria-label="던지기로"
                >
                    <Icon name="back" size={20} />
                </button>

                <h1 className="topbar__title">지역 검색</h1>
            </div>

            <div className="page-head">
                <p className="page-head__desc">갈 곳이 이미 정해졌다면 여기서 바로 고르세요.</p>
            </div>

            {/* 엔터로 제출해도 새로고침이 일어나지 않게 막는다. 실제 검색은 타이핑에 따라 자동으로 돈다 */}
            <form role="search" onSubmit={(event) => event.preventDefault()}>
                <div className="field">
                    <label className="field__label" htmlFor="region-keyword">
                        어디로 가볼까요
                    </label>

                    <input
                        id="region-keyword"
                        className="field__input"
                        type="search"
                        placeholder="예) 영양, 고흥"
                        value={keyword}
                        onChange={(event) => setKeyword(event.target.value)}
                        autoComplete="off"
                        autoCapitalize="none"
                        spellCheck="false"
                        disabled={Boolean(selectingCode)}
                    />

                    <p className="field__hint">시·군·구 이름으로 찾습니다.</p>
                </div>
            </form>

            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}

            <div className="stack stack--4">
                {results && results.length > 0 && (
                    <div className="row-list">
                        {results.map((region) => (
                            <button
                                key={region.code}
                                type="button"
                                className="row"
                                onClick={() => handleSelect(region)}
                                disabled={Boolean(selectingCode)}
                            >
                                <span className="row__body">
                                    <span className="row__title">{region.sigunguName}</span>
                                    <span className="row__meta">{region.sidoName}</span>
                                </span>

                                {selectingCode === region.code && <span className="badge">여는 중</span>}
                            </button>
                        ))}
                    </div>
                )}

                {/* 검색 중에는 "없음" 을 띄우지 않는다. 글자를 칠 때마다 빈 상태가 깜빡인다 */}
                {results && results.length === 0 && !searching && !error && (
                    <div className="empty">
                        <p className="empty__title">찾는 지역이 없어요</p>
                        <p className="empty__desc">시·군·구 이름으로 다시 검색해 보세요.</p>
                    </div>
                )}
            </div>
        </>
    );
}
