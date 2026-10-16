-- Frontend routes renamed (no redirects kept):
--   /scheduler/calendar            -> /calendar
--   /validation/queue              -> /queue
--   /admin/institution-management  -> /institution-management
-- Notifications store their link and the app opens it as-is, so rewrite the
-- prefix on existing rows (query strings / fragments are kept).
UPDATE notifications
SET deep_link = '/calendar' || substring(deep_link FROM char_length('/scheduler/calendar') + 1)
WHERE deep_link = '/scheduler/calendar' OR deep_link LIKE '/scheduler/calendar?%' OR deep_link LIKE '/scheduler/calendar#%';

UPDATE notifications
SET deep_link = '/queue' || substring(deep_link FROM char_length('/validation/queue') + 1)
WHERE deep_link = '/validation/queue' OR deep_link LIKE '/validation/queue?%' OR deep_link LIKE '/validation/queue#%';

UPDATE notifications
SET deep_link = '/institution-management' || substring(deep_link FROM char_length('/admin/institution-management') + 1)
WHERE deep_link = '/admin/institution-management' OR deep_link LIKE '/admin/institution-management?%' OR deep_link LIKE '/admin/institution-management#%';
