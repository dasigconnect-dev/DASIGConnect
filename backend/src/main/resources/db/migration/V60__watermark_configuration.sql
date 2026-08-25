CREATE TABLE IF NOT EXISTS watermark_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID REFERENCES institutions(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    elements_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(150)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_watermark_institution 
ON watermark_configurations (institution_id) 
WHERE institution_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_watermark_network_default 
ON watermark_configurations ((institution_id IS NULL)) 
WHERE institution_id IS NULL;
