CREATE TABLE url (
    id           BIGINT                   NOT NULL,
    shortcode    VARCHAR(11)              NOT NULL,
    original_url TEXT                     NOT NULL,
    user_id      VARCHAR(255)             NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_url PRIMARY KEY (id),
    CONSTRAINT uk_url_shortcode UNIQUE (shortcode)
);

CREATE INDEX idx_url_user_id ON url (user_id);
