/**
 * place.js
 * 장소를 화면에 그릴 때 쓰는 표시 보조 함수.
 *
 * API 호출이 없는 순수 함수만 둔다(그쪽은 trip.js).
 * 카드 화면과 찜 목록이 같은 장소를 서로 다르게 그리지 않도록 여기 모았다 —
 * 특히 길찾기 링크는 규격을 잘못 만들면 좌표가 밀려 엉뚱한 곳으로 안내한다.
 */

/**
 * TourAPI 이미지 주소가 http 로 오는 경우가 있다.
 * 배포는 https 라 그대로 쓰면 브라우저가 혼합 콘텐츠로 막아 사진이 통째로 비어버린다.
 */
export function toHttps(url) {
    if (!url) return '';
    return url.startsWith('http://') ? `https://${url.slice('http://'.length)}` : url;
}

/** 1km 아래는 m, 그 위는 소수 한 자리 km */
export function formatDistance(meters) {
    return meters < 1000 ? `${meters}m` : `${(meters / 1000).toFixed(1)}km`;
}

/**
 * 카카오맵 길찾기 링크.
 *
 * SDK 가 아니라 공개 링크 규격이라 JS 키가 필요 없고, 앱이 깔려 있으면 앱으로 열린다.
 * 장소명에 쉼표가 들어가면 좌표 자리가 밀리므로 지우고 보낸다.
 */
export function directionsUrl(place) {
    const name = encodeURIComponent((place.placeName ?? '').replace(/,/g, ' '));
    return `https://map.kakao.com/link/to/${name},${place.lat},${place.lng}`;
}

/** "2026년 9월 19일" — 서버가 준 ISO 문자열을 못 읽으면 날짜 줄을 비운다 */
export function formatDate(value) {
    if (!value) return '';

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) return '';

    return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
}
