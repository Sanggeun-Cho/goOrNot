package com.thc.goornotdev.domain;

/**
 * 세션의 진행 상태.
 *
 * IN_PROGRESS : 아직 갈 지역이 정해지지 않음 (RANDOM 진행 중)
 * CONFIRMED   : 지역이 확정됨. regionCode / regionName / lat / lng 가 채워진다.
 */
public enum ThrowStatus {
    IN_PROGRESS,
    CONFIRMED
}
