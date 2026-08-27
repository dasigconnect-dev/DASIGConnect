-- scheduled_job_runs has RLS enabled with INSERT (WITH CHECK true) and admin
-- SELECT policies, but no DELETE policy — so ScheduledJobRunRetentionJob's prune
-- would silently affect zero rows. Add a system DELETE policy mirroring the
-- existing system INSERT policy.

DROP POLICY IF EXISTS scheduled_job_runs_system_delete ON scheduled_job_runs;
CREATE POLICY scheduled_job_runs_system_delete ON scheduled_job_runs
    FOR DELETE
    USING (true);
