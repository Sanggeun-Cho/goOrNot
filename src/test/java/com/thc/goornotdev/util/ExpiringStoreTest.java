package com.thc.goornotdev.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpiringStoreTest {

    @Test
    @DisplayName("저장한 값을 그대로 꺼낸다")
    void putAndGet() {
        ExpiringStore<Long, String> store = new ExpiringStore<>(Duration.ofMinutes(1));

        store.put(1L, "42210");

        assertThat(store.get(1L)).isEqualTo("42210");
    }

    @Test
    @DisplayName("없는 키는 null")
    void getMissing() {
        ExpiringStore<Long, String> store = new ExpiringStore<>(Duration.ofMinutes(1));

        assertThat(store.get(1L)).isNull();
        assertThat(store.get(null)).isNull();
    }

    @Test
    @DisplayName("같은 키에 다시 넣으면 덮어쓴다")
    void putOverwrites() {
        ExpiringStore<Long, String> store = new ExpiringStore<>(Duration.ofMinutes(1));

        store.put(1L, "42210");
        store.put(1L, "11680");

        assertThat(store.get(1L)).isEqualTo("11680");
    }

    @Test
    @DisplayName("take 는 꺼내면서 지운다 (1회용 토큰)")
    void takeRemoves() {
        ExpiringStore<Long, String> store = new ExpiringStore<>(Duration.ofMinutes(1));

        store.put(1L, "42210");

        assertThat(store.take(1L)).isEqualTo("42210");
        assertThat(store.take(1L)).isNull();
    }

    @Test
    @DisplayName("TTL 이 지난 값은 조회되지 않는다")
    void expiresAfterTtl() throws InterruptedException {
        // 실제 시간을 기다려야 해서 TTL 을 아주 짧게 준다
        ExpiringStore<Long, String> store = new ExpiringStore<>(Duration.ofMillis(30));

        store.put(1L, "42210");
        Thread.sleep(60);

        assertThat(store.get(1L)).isNull();
        assertThat(store.take(1L)).isNull();
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("null 키·값은 저장하지 않는다")
    void ignoresNulls() {
        ExpiringStore<Long, String> store = new ExpiringStore<>(Duration.ofMinutes(1));

        store.put(null, "42210");
        store.put(1L, null);

        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("엔트리 상한을 넘으면 오래된 것부터 정리된다")
    void evictsOverMaxEntries() {
        ExpiringStore<Long, String> store = new ExpiringStore<>(Duration.ofMinutes(1), 10);

        for (long i = 0; i < 50; i++) {
            store.put(i, "code-" + i);
        }

        assertThat(store.size()).isLessThanOrEqualTo(10);
        // 가장 최근에 넣은 값은 살아 있어야 한다
        assertThat(store.get(49L)).isEqualTo("code-49");
    }

    @Test
    @DisplayName("잘못된 TTL / 상한은 생성 시점에 막는다")
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new ExpiringStore<Long, String>(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExpiringStore<Long, String>(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExpiringStore<Long, String>(Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
