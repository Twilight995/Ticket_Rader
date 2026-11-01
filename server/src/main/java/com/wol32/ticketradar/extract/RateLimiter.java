package com.wol32.ticketradar.extract;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * 분 단위 고정 창 호출 횟수 제한 (서버 메모리). AI 비용과 무료 티어 한도를 지킨다.
 * ponytail: 서버 1대 전제. 여러 대가 되면 공유 저장소로 올린다. 오래된 창은 다음 분에 지운다.
 */
@Component
public class RateLimiter {

    private record Window(long minute, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** 이번 분에 limit 번째까지는 true, 그 다음부터 false. */
    public boolean tryAcquire(String key, int limit) {
        long minute = System.currentTimeMillis() / 60_000;
        Window window = windows.compute(key, (k, old) ->
                old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        }
        return window.count().incrementAndGet() <= limit;
    }
}
