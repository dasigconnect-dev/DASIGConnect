CREATE TABLE messenger_connections (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    page_id VARCHAR(100) NOT NULL,
    page_scoped_user_id VARCHAR(255) UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    link_code_hash VARCHAR(64),
    link_code_expires_at TIMESTAMP WITH TIME ZONE,
    linked_at TIMESTAMP WITH TIME ZONE,
    last_interaction_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messenger_connections_link_code
    ON messenger_connections(link_code_hash);
