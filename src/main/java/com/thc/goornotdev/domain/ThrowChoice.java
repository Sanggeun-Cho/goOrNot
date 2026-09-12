package com.thc.goornotdev.domain;

/**
 * 한 회차에서 사용자가 고른 선택.
 *
 * GO    : 갈래. 이 회차의 지역으로 세션을 확정한다.
 * AGAIN : 말래. 다시 던진다. 이 회차의 좌표는 다음 후보에서 제외된다.
 */
public enum ThrowChoice {
    GO,
    AGAIN
}
