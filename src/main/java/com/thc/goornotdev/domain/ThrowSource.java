package com.thc.goornotdev.domain;

/**
 * 세션이 어떻게 시작됐는지.
 *
 * RANDOM : 지도를 던져 랜덤 지역을 뽑는 흐름. 여러 회차(ThrowRound)가 쌓인다.
 * SEARCH : 사용자가 지역을 직접 검색한 흐름. 회차 없이 곧바로 확정된다.
 */
public enum ThrowSource {
    RANDOM,
    SEARCH
}
