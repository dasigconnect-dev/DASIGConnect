-- Seed default network-wide watermark configuration if not already present
INSERT INTO watermark_configurations (id, institution_id, enabled, elements_json, updated_by)
SELECT gen_random_uuid(), NULL, TRUE,
  '[{"id":"default-logo","type":"image","xPercent":78.0,"yPercent":82.0,"widthPercent":18.0,"heightPercent":14.0,"opacity":0.9,"imageUrl":"/dasig-logo.png"},{"id":"default-text","type":"text","xPercent":55.0,"yPercent":92.0,"widthPercent":40.0,"heightPercent":6.0,"opacity":0.9,"text":"@DASIGCentralVisayas","textColor":"#FFFFFF","fontSizePercent":2.8,"fontWeight":"700"}]',
  'system'
WHERE NOT EXISTS (
  SELECT 1 FROM watermark_configurations WHERE institution_id IS NULL
);
