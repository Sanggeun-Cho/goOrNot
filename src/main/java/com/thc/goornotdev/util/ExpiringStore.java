package com.thc.goornotdev.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TTL 이 붙은 인메모리 저장소.
 *
 * 왜 DB 가 아닌가:
 * 여기에 들어가는 값은 "방금 추첨한 지역", "리롤 사이에 재사용할 장소 후보" 처럼
 * 몇 분만 살아 있으면 되는 임시 상태다. 테이블로 만들면 마이그레이션 대상과
 * 정리 배치만 늘어나고, 유실돼도 사용자가 한 번 더 요청하면 그만이다.
 *
 * 만료 처리:
 * 별도 스케줄러를 두지 않고 조회 시점에 지연 만료(lazy expiration)한다.
 * 아무도 다시 찾지 않아 조회되지 않는 엔트리는 {@code maxEntries} 를 넘길 때
 * 일괄 청소한다. 스레드 하나를 상시로 띄우는 비용을 피하기 위함이다.
 *
 * 스레드 안전:
 * {@link ConcurrentHashMap} 이라 동시 요청이 서로를 막지 않는다.
 * 다만 put/evict 사이에 원자성은 보장하지 않는다 — 상한을 잠깐 넘겨도 문제없는 용도만 담는다.
 *
 * 한계 (알고 쓰는 것):
 * 인스턴스 로컬이다. 서버를 여러 대로 늘리면 같은 세션이 다른 인스턴스로 가서 값을 못 찾는다.
 * 그때는 세션 어피니티를 걸거나 Redis 같은 외부 저장소로 옮겨야 한다.
 * 재시작 시 전부 사라지지만, 값이 없으면 "다시 요청" 으로 떨어지므로 안전한 방향의 실패다.
 */
public class ExpiringStore<K, V> {

    /** 상한을 따로 정하지 않았을 때의 엔트리 수 */
    public static final int DEFAULT_MAX_ENTRIES = 10_000;

    /**
     * 저장된 값 하나.
     * 바깥 타입 파라미터 V 를 가리지 않도록 이름을 T 로 둔다.
     */
    private record Entry<T>(T value, Instant expiresAt) {
        boolean expired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private final Map<K, Entry<V>> store = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxEntries;

    public ExpiringStore(Duration ttl) {
        this(ttl, DEFAULT_MAX_ENTRIES);
    }

    public ExpiringStore(Duration ttl, int maxEntries) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL 은 0보다 커야 합니다.");
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("최대 엔트리 수는 1 이상이어야 합니다.");
        }

        this.ttl = ttl;
        this.maxEntries = maxEntries;
    }

    /** 저장. 같은 키가 있으면 덮어쓰고 TTL 도 새로 시작한다 */
    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }

        store.put(key, new Entry<>(value, Instant.now().plus(ttl)));

        if (store.size() > maxEntries) {
            evict();
        }
    }

    /** 조회. 없거나 만료됐으면 null */
    public V get(K key) {
        if (key == null) {
            return null;
        }

        Entry<V> entry = store.get(key);

        if (entry == null) {
            return null;
        }

        if (entry.expired(Instant.now())) {
            // 같은 엔트리일 때만 지운다. 그 사이 새로 put 된 값을 날리지 않기 위함이다
            store.remove(key, entry);

            return null;
        }

        return entry.value();
    }

    /**
     * 꺼내면서 지우기 (1회용 토큰).
     * 조회와 삭제가 한 번에 일어나야 같은 값을 두 번 쓰는 것을 막을 수 있다.
     */
    public V take(K key) {
        if (key == null) {
            return null;
        }

        Entry<V> entry = store.remove(key);

        return (entry == null || entry.expired(Instant.now())) ? null : entry.value();
    }

    public void remove(K key) {
        if (key != null) {
            store.remove(key);
        }
    }

    /** 만료되지 않은 엔트리 수. 테스트·모니터링용 */
    public int size() {
        Instant now = Instant.now();

        return (int) store.values().stream()
                .filter(entry -> !entry.expired(now))
                .count();
    }

    public void clear() {
        store.clear();
    }

    /* ── 내부 ────────────────────────────────────────────── */

    /**
     * 상한 초과 시 청소.
     * 만료된 것부터 지우고, 그래도 넘치면 만료가 임박한(= 가장 오래된) 순으로 잘라낸다.
     */
    private void evict() {
        Instant now = Instant.now();

        store.entrySet().removeIf(entry -> entry.getValue().expired(now));

        int overflow = store.size() - maxEntries;

        if (overflow <= 0) {
            return;
        }

        List<K> oldest = store.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList();

        oldest.forEach(store::remove);
    }
}
