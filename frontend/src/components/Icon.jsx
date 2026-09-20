/**
 * 아이콘 한 벌.
 *
 * 왜 아이콘 라이브러리를 쓰지 않는가:
 * lucide/heroicons 를 넣으면 쓰는 건 열댓 개인데 패키지 전체가 의존성으로 들어오고,
 * 트리셰이킹이 먹는지 빌드마다 확인해야 한다. 여기서 필요한 모양은 선 몇 개짜리라
 * 파일 하나에 그려두는 편이 가볍고, 굵기·모양을 앱 톤에 맞춰 통일하기도 쉽다.
 *
 * 규칙
 *   - 24×24 좌표계, 선(stroke)만 쓰고 면은 채우지 않는다. 채운 아이콘은 하트뿐이다.
 *   - 색은 항상 currentColor. 부모의 color 만 바꾸면 아이콘도 따라온다.
 *   - aria-hidden. 아이콘 옆에는 늘 글자 라벨이 있거나, 버튼에 aria-label 이 붙는다.
 *     (읽는 도구가 같은 말을 두 번 하지 않게)
 */

/* 각 아이콘의 <path> 들. 좌표계는 모두 24×24 로 맞춘다 */
const SHAPES = {
    /* ── 탭바 ───────────────────────────────────────── */

    // 던지기. 주사위 = "정해주는 게 아니라 굴리는 것"
    dice: (
        <>
            <rect x="3" y="3" width="18" height="18" rx="4.5" />
            <circle cx="8.5" cy="8.5" r="1.3" fill="currentColor" stroke="none" />
            <circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none" />
            <circle cx="15.5" cy="15.5" r="1.3" fill="currentColor" stroke="none" />
        </>
    ),

    search: (
        <>
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.9-3.9" />
        </>
    ),

    // 내 여행. 접힌 지도
    map: (
        <>
            <path d="M9 3 3 5.4v15.1L9 18l6 3 6-2.4V3.5L15 6 9 3Z" />
            <path d="M9 3v15" />
            <path d="M15 6v15" />
        </>
    ),

    user: (
        <>
            <circle cx="12" cy="8" r="4" />
            <path d="M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5" />
        </>
    ),

    /* ── 카테고리 (화면 3 타일) ─────────────────────── */

    attraction: (
        <>
            <circle cx="17" cy="6.5" r="2.2" />
            <path d="M3 19h18" />
            <path d="m3 19 5.5-8 3.5 5 2.5-3.5L21 19" />
        </>
    ),

    food: (
        <>
            <path d="M5 3v5a2.2 2.2 0 0 0 4.4 0V3" />
            <path d="M7.2 10.2V21" />
            <path d="M17 3c-1.7 1.3-2.6 3.3-2.6 5.6 0 2.2 1.1 3.6 2.6 3.6h.8V3H17Z" />
            <path d="M17.8 12.2V21" />
        </>
    ),

    leisure: <path d="M3 12h4l3-7.5 4 15L17 12h4" />,

    stay: (
        <>
            <path d="M3 6v14" />
            <path d="M3 13h18v7" />
            <path d="M21 13v-1.5A2.5 2.5 0 0 0 18.5 9H10v4" />
            <circle cx="6.6" cy="10.4" r="1.9" />
        </>
    ),

    event: (
        <>
            <rect x="3" y="5" width="18" height="16" rx="3.5" />
            <path d="M8 3v4" />
            <path d="M16 3v4" />
            <path d="M3 10.5h18" />
        </>
    ),

    /* ── 행동 ───────────────────────────────────────── */

    // 하트는 유일하게 속을 채울 수 있다 (filled prop)
    heart: <path d="M12 20.4 4.3 12.9a4.9 4.9 0 1 1 7-6.9l.7.7.7-.7a4.9 4.9 0 1 1 7 6.9L12 20.4Z" />,

    // 다시 뽑기
    refresh: (
        <>
            <path d="M20.5 12a8.5 8.5 0 1 1-2.6-6.1" />
            <path d="M20.5 3.5V9H15" />
        </>
    ),

    // 길찾기. 종이비행기 = "여기서 저기로"
    navigate: <path d="M21 3 3.5 10.2l7.4 2.9 2.9 7.4L21 3Z" />,

    check: <path d="m4.5 12.5 5 5 10-11" />,

    // 되돌리기 (간 곳에서 빼기)
    undo: (
        <>
            <path d="M4 9h11a5 5 0 0 1 0 10h-6" />
            <path d="M8 5 4 9l4 4" />
        </>
    ),

    back: <path d="m15 5-7 7 7 7" />,

    chevron: <path d="m9 5 7 7-7 7" />,

    logout: (
        <>
            <path d="M9.5 3H5.5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4" />
            <path d="m16 8 4 4-4 4" />
            <path d="M20 12H9.5" />
        </>
    ),

    doc: (
        <>
            <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z" />
            <path d="M14 3v5h5" />
        </>
    ),
};

/** 카테고리 이름(서버 ThrowCategory) → 아이콘 이름 */
export const CATEGORY_ICONS = {
    ATTRACTION: 'attraction',
    FOOD: 'food',
    LEISURE: 'leisure',
    STAY: 'stay',
    EVENT: 'event',
};

export default function Icon({ name, size = 22, filled = false, className = '' }) {
    const shape = SHAPES[name];

    // 이름을 잘못 적었을 때 자리만 비워둔다. 여기서 던지면 화면 전체가 날아간다
    if (!shape) return null;

    return (
        <svg
            className={className}
            width={size}
            height={size}
            viewBox="0 0 24 24"
            fill={filled ? 'currentColor' : 'none'}
            stroke="currentColor"
            strokeWidth="1.7"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
            focusable="false"
        >
            {shape}
        </svg>
    );
}
