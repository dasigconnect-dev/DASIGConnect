package com.dasigconnect.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.dasigconnect.backend.repository.AnalyticsRepository.AnalyticsScope;

@ExtendWith(MockitoExtension.class)
class AnalyticsRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private PublishSuccessRateRepository publishSuccessRateRepository;

    private AnalyticsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AnalyticsRepository(jdbc, publishSuccessRateRepository);
    }

    @Test
    void reportRows_countsAllMatchesButFetchesOnlyRequestedDatabasePage() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(125L);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("submission_id", "abc")));

        var result = repository.reportRows(
                "posting-delay",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                new AnalyticsScope("admin", null, null),
                3,
                50);

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(125);
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.pageSize()).isEqualTo(50);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(countSql.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertThat(countSql.getValue()).startsWith("SELECT COUNT(*) FROM (");

        ArgumentCaptor<String> pageSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(pageSql.capture(), params.capture());
        assertThat(pageSql.getValue()).contains("LIMIT :pageSize OFFSET :offset");
        assertThat(params.getValue().getValue("pageSize")).isEqualTo(50);
        assertThat(params.getValue().getValue("offset")).isEqualTo(100L);
    }
}
