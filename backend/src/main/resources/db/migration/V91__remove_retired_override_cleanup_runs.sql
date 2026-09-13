-- Guard-rail override requests were removed by V84. Remove their obsolete
-- scheduled-job health history so the retired job is not shown in System Health.
DELETE FROM scheduled_job_runs
WHERE job_name = 'ExpiredOverrideCleanupJob';