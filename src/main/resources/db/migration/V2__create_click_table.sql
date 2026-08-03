CREATE TABLE click (
    id         BIGINT                   GENERATED ALWAYS AS IDENTITY,
    shortcode  VARCHAR(11)              NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    referer    TEXT,
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_click PRIMARY KEY (id)
);

CREATE INDEX idx_click_shortcode ON click (shortcode);
