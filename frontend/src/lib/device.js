/**
 * device.js
 * 기기 식별자 저장소.
 *
 * 던지기는 로그인 없이도 쓸 수 있어야 해서, 비로그인 세션의 소유권을 이 값으로 판단한다.
 * (서버: ThrowSessionServiceImpl.verifyOwner — userId 가 없으면 deviceId 로 본다)
 *
 * 토큰과 달리 localStorage 를 쓴다. 탭을 닫아도 "내가 던진 기록" 이 남아야 하고,
 * 개인정보가 아니라 브라우저에서 만든 임의의 UUID 라 노출돼도 잃을 것이 없다.
 *
 * 다만 이 값을 아는 사람은 해당 익명 세션에 접근할 수 있으므로 추측 불가능해야 한다.
 * Math.random 이 아니라 crypto 를 쓰는 이유다.
 */

const KEY_DEVICE_ID = 'goornot.deviceId';

export const DEVICE_HEADER = 'X-Device-Id';

function createId() {
    if (window.crypto?.randomUUID) {
        return window.crypto.randomUUID();
    }

    // randomUUID 가 없는 구형 브라우저용 대체 경로. 여전히 암호학적 난수를 쓴다
    const bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);

    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

export const deviceStore = {
    /** 없으면 만들어서 저장한 뒤 돌려준다 */
    get() {
        let id = window.localStorage.getItem(KEY_DEVICE_ID);

        if (!id) {
            id = createId();
            window.localStorage.setItem(KEY_DEVICE_ID, id);
        }

        return id;
    },

    /** 테스트용. 새 기기인 척하고 싶을 때 쓴다 */
    reset() {
        window.localStorage.removeItem(KEY_DEVICE_ID);

        return this.get();
    },
};
