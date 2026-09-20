import { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';

import Icon from '../components/Icon.jsx';
import { useAuth } from '../lib/AuthContext.jsx';
import { directionsUrl, formatDistance, toHttps } from '../lib/place.js';
import { categoryLabel, dealCards, fetchSavedPlaces, markPlace, rerollCard } from '../lib/trip.js';

/**
 * 화면 4 · 카드 선택.
 *
 * 세션과 카테고리를 URL 에 둔다(/cards/:sessionId/:category).
 * 상태로 넘기면 새로고침에서 날아가고, 뒤로가기로 카테고리 화면에 돌아갔다
 * 다시 들어오는 흐름에서 어느 카테고리를 보던 중이었는지 잃는다.
 *
 * ── 한 판은 한 곳으로 끝난다 ──
 * [2026-09-19 확정] 카테고리는 일회성이다.
 *   "오늘 뭐하지 → 관광지 → 리롤 → 오 여기다" 가 한 판이고, 고르는 순간 판이 닫힌다.
 *   내일 또 관광지를 누르면 새 카드 세 장이 깔린다.
 *
 * 그래서 이 화면은 고른 뒤에 머무르지 않고 여행 화면으로 돌아간다.
 * 서버 쪽에서도 저장과 동시에 그 카테고리의 카드 풀을 버린다(CardService.endRound).
 *
 * 한 번 더 확인을 받는 이유: 판을 닫는 건 되돌리기 번거로운 행동이다.
 * 스크롤하다 카드를 잘못 스치면 안 간 곳이 기록되고 남은 리롤까지 같이 사라진다.
 *
 * ── 표시는 두 가지다 ──
 *   카드 고르기 : "여기 간다" — 이 여행에서 간 곳으로 기록하고 판을 닫는다
 *   하트        : 찜. 지금 가진 않지만 마음에 든 곳. 여러 장에 눌러도 판이 닫히지 않는다
 *
 * ── 서버가 쥐고 있는 것 ──
 * 카드 세트·리롤 잔여는 전부 서버 상태다(CardServiceImpl 의 풀, TTL 30분).
 * 프론트는 응답을 그대로 그리기만 한다. 리롤 횟수를 프론트가 세면 새로고침 한 번에
 * 되살아나고, 같은 카테고리를 다시 열면 같은 카드가 와야 하는 규칙도 깨진다.
 */
export default function CardsPage() {
    const navigate = useNavigate();
    const { sessionId, category } = useParams();
    const { isAuthenticated, isPending } = useAuth();

    const [set, setSet] = useState(null);
    const [error, setError] = useState('');

    /** contentId -> { visited, wished }. 서버가 준 표시 상태를 그대로 담는다 */
    const [marks, setMarks] = useState({});

    /** 확인 시트에 올라가 있는 장소. null 이면 시트가 닫힌 상태 */
    const [picking, setPicking] = useState(null);

    /** 진행 중인 버튼 잠금 */
    const [busySlot, setBusySlot] = useState(null);
    const [busyHeart, setBusyHeart] = useState(null);
    const [confirming, setConfirming] = useState(false);

    /*
     * 표시는 여행 단위라 이 세션 것만 받는다.
     * UNIQUE 가 (user, 여행, 장소) 라서 같은 장소를 다른 여행에서 표시해 뒀더라도
     * 이 여행에서는 꺼진 상태가 맞다 — 작년 속초에서 간 횟집이 올해 속초에서
     * 이미 간 곳으로 켜져 있으면 안 된다.
     *
     * 판이 닫히면 화면을 떠나므로 visited 는 "고른 표시" 로 쓰이지 않는다.
     * 다만 새 판에서 지난 판에 간 곳이 다시 뽑힐 수 있어, 그때 알려주려고 같이 받는다.
     */
    const loadMarks = useCallback(
        () =>
            fetchSavedPlaces({ sessionId }).then((list) =>
                Object.fromEntries(
                    (list ?? []).map((row) => [row.contentId, { visited: row.visited, wished: row.wished }]),
                ),
            ),
        [sessionId],
    );

    useEffect(() => {
        if (isPending || !isAuthenticated) return undefined;

        let alive = true;
        setSet(null);
        setError('');

        Promise.all([dealCards({ sessionId, category }), loadMarks()])
            .then(([dealt, loaded]) => {
                if (!alive) return;
                setSet(dealt);
                setMarks(loaded);
            })
            .catch((caught) => {
                if (alive) setError(caught.message || '카드를 받지 못했습니다.');
            });

        return () => {
            alive = false;
        };
    }, [sessionId, category, isAuthenticated, isPending, loadMarks]);

    // 시트가 떠 있을 때 Esc 로 닫는다. 확인 시트는 "실수로 열렸을" 가능성이 높은 창이다
    useEffect(() => {
        if (!picking) return undefined;

        function onKeyDown(event) {
            if (event.key === 'Escape' && !confirming) setPicking(null);
        }

        window.addEventListener('keydown', onKeyDown);

        return () => window.removeEventListener('keydown', onKeyDown);
    }, [picking, confirming]);

    async function handleReroll(slot) {
        setBusySlot(slot);
        setError('');

        try {
            setSet(await rerollCard({ sessionId, category, slot }));
        } catch (caught) {
            // 풀 TTL(30분)이 지나면 "카드를 다시 받아주세요" 가 온다. 문구를 그대로 보여준다
            setError(caught.message || '카드를 바꾸지 못했습니다.');
        } finally {
            setBusySlot(null);
        }
    }

    /**
     * 이 판을 이 장소로 끝낸다.
     *
     * 돌아갈 때 replace 를 쓰는 이유: 판은 이미 닫혔다. 히스토리에 카드 화면을 남겨두면
     * 뒤로가기로 돌아왔을 때 서버가 새 풀을 만들어 다른 카드 세 장을 깔아준다.
     * 방금 고른 화면이 사라진 것처럼 보이는데, 실은 다음 판이 열린 것이라 혼란스럽다.
     */
    async function confirmPick() {
        setConfirming(true);
        setError('');

        try {
            await markPlace({ sessionId, category, place: picking, visited: true });
            navigate(`/category/${sessionId}`, { replace: true });
        } catch (caught) {
            setError(caught.message || '기록하지 못했습니다.');
            setPicking(null);
        } finally {
            setConfirming(false);
        }
    }

    /**
     * 하트를 뒤집는다.
     *
     * ⚠ wished 만 보낸다. visited 를 같이 실어 보내면 화면이 들고 있던 낡은 값이
     *   서버 값을 덮어쓴다. 서버는 undefined 를 "그대로 두라" 로 읽는다.
     */
    async function toggleWish(place) {
        const current = marks[place.contentId] ?? { visited: false, wished: false };
        const next = !current.wished;

        setBusyHeart(place.contentId);
        setError('');

        try {
            await markPlace({ sessionId, category, place, wished: next });
            setMarks((prev) => ({
                ...prev,
                [place.contentId]: { ...current, wished: next },
            }));
        } catch (caught) {
            setError(caught.message || '찜하지 못했습니다.');
        } finally {
            setBusyHeart(null);
        }
    }

    if (isPending) {
        return <p className="state">확인하는 중…</p>;
    }

    // 카드는 회원 전용 구간이다. 카테고리 화면과 같은 방식으로 로그인에 들려 보낸다
    if (!isAuthenticated) {
        return (
            <Navigate
                to="/login"
                replace
                state={{
                    from: `/cards/${sessionId}/${category}`,
                    linkSession: sessionId,
                }}
            />
        );
    }

    const label = set?.categoryLabel ?? categoryLabel(category);
    const cards = set?.cards ?? [];

    return (
        <>
            <div className="topbar">
                <button
                    type="button"
                    className="topbar__back"
                    /* navigate(-1) 이 아니라 경로로 돌아간다. 리롤을 여러 번 누른 뒤
                       뒤로가기를 하면 히스토리가 아니라 카테고리 화면으로 가야 자연스럽다 */
                    onClick={() => navigate(`/category/${sessionId}`)}
                    aria-label="여행으로 돌아가기"
                >
                    <Icon name="back" size={20} />
                </button>

                <h1 className="topbar__title">{set ? `${set.regionName} · ${label}` : ' '}</h1>
            </div>

            {error && (
                <p className="form-error" role="alert">
                    {error}
                </p>
            )}

            {!set && !error && <p className="state">카드를 뽑는 중…</p>}

            {set && cards.length === 0 && (
                <div className="empty">
                    <p className="empty__title">이 동네엔 {label} 정보가 없어요</p>
                    <p className="empty__desc">뒤로 가서 다른 종류를 골라보세요.</p>
                </div>
            )}

            {cards.length > 0 && (
                <>
                    <div className="page-head">
                        <h2 className="page-head__title">오늘은 어디로</h2>
                        <p className="page-head__desc">
                            한 곳을 고르면 이번 {label}는 끝나요. 다음에 또 고르면 새 카드를 드려요.
                        </p>
                    </div>

                    <div className="stack stack--3">
                        {cards.map((card) => {
                            const mark = marks[card.place.contentId];

                            return (
                                <PlaceCard
                                    key={card.place.contentId}
                                    card={card}
                                    visited={Boolean(mark?.visited)}
                                    wished={Boolean(mark?.wished)}
                                    rerolling={busySlot === card.slot}
                                    wishing={busyHeart === card.place.contentId}
                                    onPick={() => setPicking(card.place)}
                                    onWish={() => toggleWish(card.place)}
                                    onReroll={() => handleReroll(card.slot)}
                                />
                            );
                        })}
                    </div>

                    <p className="hint-link">
                        하트는 여러 곳에 눌러도 돼요. 나중에 마이페이지에서 볼 수 있어요.
                    </p>
                </>
            )}

            {set && cards.length > 0 && cards.length < 3 && (
                <p className="hint-link">
                    이 동네에서 찾은 {label}가 {cards.length}곳뿐이에요.
                </p>
            )}

            {picking && (
                <ConfirmSheet
                    place={picking}
                    label={label}
                    busy={confirming}
                    onCancel={() => setPicking(null)}
                    onConfirm={confirmPick}
                />
            )}
        </>
    );
}

/**
 * 고르기 확인 시트.
 *
 * 화면 2(갈래/말래)와 같은 모양을 쓴다. 이 앱에서 되돌리기 어려운 선택은 전부
 * 아래에서 올라오는 같은 시트로 묻는다 — 모양이 같으면 "아, 이건 확정이구나" 를 학습한다.
 */
function ConfirmSheet({ place, label, busy, onCancel, onConfirm }) {
    return (
        <div
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-label="고른 곳 확인"
            /* 바깥을 눌러도 닫히게 한다. 저장 중일 때는 잠근다 —
               요청이 나간 뒤에 닫아봐야 기록은 이미 남는다 */
            onClick={busy ? undefined : onCancel}
        >
            {/* 패널 안쪽 클릭이 바깥 닫기로 새지 않게 막는다 */}
            <div className="modal__panel" onClick={(event) => event.stopPropagation()}>
                <p className="modal__eyebrow">{label}</p>
                <h2 className="modal__title">{place.placeName}</h2>

                {place.address && <p className="modal__desc">{place.address}</p>}

                <p className="modal__note">여기로 정하면 이번 {label} 고르기는 끝나요.</p>

                <div className="modal__actions">
                    <button type="button" className="btn btn--lg" onClick={onCancel} disabled={busy}>
                        더 볼래
                    </button>

                    <button
                        type="button"
                        className="btn btn--lg btn--accent"
                        onClick={onConfirm}
                        disabled={busy}
                    >
                        {busy ? '기록하는 중…' : '여기로 갈래'}
                    </button>
                </div>
            </div>
        </div>
    );
}

/**
 * 카드 한 장.
 *
 * 카드 본체 = "여기로 고르기" 버튼, 오른쪽 아래 버튼 세 개(다시 뽑기 / 길찾기 / 하트).
 * 와이어프레임 화면 4 기준.
 */
function PlaceCard({ card, visited, wished, rerolling, wishing, onPick, onWish, onReroll }) {
    const { place } = card;
    const image = toHttps(place.imageUrl || place.thumbnailUrl);

    /*
     * 대표이미지 저작권 Type3(제3유형)은 "출처 표시 + 변경 금지" 조건이다.
     * 카드 틀에 맞춰 잘라내는 것(cover)이 변경으로 읽힐 여지가 있어, 이 경우만
     * 원본 비율을 유지하고(contain) 출처를 함께 적는다.
     */
    const restricted = place.imageCopyrightCode === 'Type3';

    return (
        <article className="place-card">
            {/* 카드 전체를 덮는 투명 버튼. 내용은 pointer-events 로 클릭을 여기로 흘려보낸다 */}
            <button
                type="button"
                className="place-card__pick"
                onClick={onPick}
                aria-label={`${place.placeName} 고르기`}
            />

            {image ? (
                <img
                    className={`place-card__thumb${restricted ? ' is-contain' : ''}`}
                    src={image}
                    alt=""
                    loading="lazy"
                />
            ) : (
                <div className="place-card__thumb place-card__thumb--empty">사진 없음</div>
            )}

            <div className="place-card__body">
                <h3 className="place-card__name">{place.placeName}</h3>

                {place.address && <p className="place-card__address">{place.address}</p>}

                <p className="place-card__meta">
                    {/* 지난 판에 이미 다녀온 곳이 새 판에 다시 뽑힐 수 있다. 그걸 알려준다 */}
                    {visited && (
                        <span className="place-card__again">
                            <Icon name="check" size={13} /> 이 여행에서 간 곳
                        </span>
                    )}
                    {typeof place.distance === 'number' && <span>중심에서 {formatDistance(place.distance)}</span>}
                    {/*
                      [2026-09-20] "사진 제공 : 한국관광공사" → "사진 출처: ⓒ한국관광공사".
                      공모전 FAQ 가 올바른 표기를 "출처: ⓒ한국관광공사" 로 못박았다.
                      "제공" 은 공사가 이 서비스에 사진을 대준 것처럼, 즉 후원/제휴로 읽힐 여지가 있다.
                      금지되는 건 그 오인이지 기관명 자체가 아니다(기관명 출처 표기는 오히려 필수).
                    */}
                    {restricted && <span>사진 출처: ⓒ한국관광공사</span>}
                </p>

                <div className="place-card__actions">
                    <button
                        type="button"
                        className="icon-btn"
                        onClick={onReroll}
                        /* 리롤 가능 여부는 서버 판단(card.rerollable)만 믿는다.
                           후보가 깔린 카드 수와 같으면 바꿀 여분이 없어 false 로 온다 */
                        disabled={!card.rerollable || rerolling}
                        aria-label="다른 곳으로 바꾸기"
                        title={card.rerollable ? '다른 곳으로 바꾸기' : '이미 한 번 바꿨어요'}
                    >
                        <Icon name="refresh" size={18} />
                    </button>

                    {/* 길찾기는 외부 지도로 넘긴다. 앱 안에서 경로를 그리지 않는다 */}
                    <a
                        className="icon-btn"
                        href={directionsUrl(place)}
                        target="_blank"
                        rel="noreferrer"
                        aria-label="길찾기"
                        title="길찾기"
                    >
                        <Icon name="navigate" size={18} />
                    </a>

                    <button
                        type="button"
                        className={`icon-btn icon-btn--heart${wished ? ' is-on' : ''}`}
                        onClick={onWish}
                        disabled={wishing}
                        aria-pressed={wished}
                        aria-label={wished ? '찜 해제' : '찜하기'}
                        title={wished ? '찜 해제' : '찜하기'}
                    >
                        <Icon name="heart" size={18} filled={wished} />
                    </button>
                </div>
            </div>
        </article>
    );
}
