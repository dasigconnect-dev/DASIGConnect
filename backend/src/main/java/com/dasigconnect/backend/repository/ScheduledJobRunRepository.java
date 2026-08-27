package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.ScheduledJobRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledJobRunRepository extends JpaRepository<ScheduledJobRun, UUID> {

    Optional<ScheduledJobRun> findTopByJobNameOrderByStartedAtDesc(String jobName);

    /** Retention prune — see {@code schedule/ScheduledJobRunRetentionJob}. */
    @Modifying
    @Query("delete from ScheduledJobRun r where r.startedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    @Query("""
            select run
            from ScheduledJobRun run
            where run.startedAt = (
                select max(latest.startedAt)
                from ScheduledJobRun latest
                where latest.jobName = run.jobName
            )
            order by run.jobName asc
            """)
    List<ScheduledJobRun> findLatestRunsByJobName();
}
