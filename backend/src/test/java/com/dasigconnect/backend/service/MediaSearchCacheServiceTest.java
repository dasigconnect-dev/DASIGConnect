package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dasigconnect.backend.model.dto.media.MediaAssetListResponseDto;

class MediaSearchCacheServiceTest {

    private MediaSearchCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new MediaSearchCacheService();
    }

    @Test
    void get_nonExistentKey_returnsNull() {
        assertThat(cacheService.get("missing-key")).isNull();
    }

    @Test
    void putAndGet_validKey_returnsCachedResponse() {
        MediaAssetListResponseDto dto = new MediaAssetListResponseDto(Collections.emptyList(), 0, 1, 10);

        cacheService.put("test-query", dto);

        MediaAssetListResponseDto retrieved = cacheService.get("test-query");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getTotalCount()).isEqualTo(0);
        assertThat(retrieved.getPage()).isEqualTo(1);
    }

    @Test
    void invalidateAll_clearsAllEntries() {
        MediaAssetListResponseDto dto = new MediaAssetListResponseDto(Collections.emptyList(), 5, 1, 10);
        cacheService.put("query-1", dto);
        cacheService.put("query-2", dto);

        cacheService.invalidateAll();

        assertThat(cacheService.get("query-1")).isNull();
        assertThat(cacheService.get("query-2")).isNull();
    }

    @Test
    void put_exceedsMaxEntries_clearsAndAcceptsNew() {
        MediaAssetListResponseDto dto = new MediaAssetListResponseDto(Collections.emptyList(), 1, 1, 10);

        for (int i = 0; i < 500; i++) {
            cacheService.put("key-" + i, dto);
        }

        // 501st insert should trigger entries.clear() and put the new key
        cacheService.put("key-overflow", dto);

        assertThat(cacheService.get("key-overflow")).isNotNull();
    }
}
