CREATE TABLE idempotency_keys (
    key_value VARCHAR(128) PRIMARY KEY,
    response_status INT NOT NULL,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
