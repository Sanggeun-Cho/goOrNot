import { Link } from 'react-router-dom';

/**
 * 화면 6 · 내 여행.
 *
 * 레이아웃은 wireframes.svg 화면 6 을 따른다. (지역명 / 날짜 / "저장 N" 배지)
 * 실제 목록은 2-8 에서 붙인다. 지금은 빈 상태만 제대로 보여준다.
 *
 * 빈 상태를 먼저 만드는 이유: 가입 직후 사용자가 가장 먼저 보게 될 화면이라
 * 여기서 "아무것도 없음" 으로 끝나면 다음에 뭘 해야 할지 알 수 없다. 메인으로 돌려보낸다.
 */
export default function MyTripsPage() {
    return (
        <>
            <div className="page-head">
                <h1 className="page-head__title">내 여행</h1>
                <p className="page-head__desc">하트를 누른 곳이 여행 단위로 쌓입니다.</p>
            </div>

            <div className="empty">
                <p className="empty__title">아직 저장한 여행이 없어요</p>
                <p className="empty__desc">한 번 던져서 첫 여행을 만들어 보세요.</p>

                <p className="hint-link">
                    <Link to="/">던지러 가기</Link>
                </p>
            </div>
        </>
    );
}
