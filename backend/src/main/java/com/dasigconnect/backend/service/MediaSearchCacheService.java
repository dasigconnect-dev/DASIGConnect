package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.media.MediaAssetListResponseDto;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Short-lived cache for expensive Voyage-backed Media Library searches. */
@Service
public class MediaSearchCacheService {

    private static final long TTL_MILLIS = Duration.ofSeconds(30).toMillis();
    private static final int MAX_ENTRIES = 500;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public MediaAssetListResponseDto get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expiresAtMillis() <= System.currentTimeMillis()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    public void put(String key, MediaAssetListResponseDto value) {
        if (entries.size() >= MAX_ENTRIES) entries.clear();
        entries.put(key, new Entry(value, System.currentTimeMillis() + TTL_MILLIS));
    }

    public void invalidateAll() {
        entries.clear();
    }

    private record Entry(MediaAssetListResponseDto value, long expiresAtMillis) {}
}
