CREATE TABLE notification_settings (
 user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 learning BOOLEAN NOT NULL DEFAULT TRUE, competitions BOOLEAN NOT NULL DEFAULT TRUE,
 portfolio_education BOOLEAN NOT NULL DEFAULT TRUE, product_announcements BOOLEAN NOT NULL DEFAULT FALSE,
 promotional_messages BOOLEAN NOT NULL DEFAULT FALSE, push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 email_digest BOOLEAN NOT NULL DEFAULT FALSE, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE user_notification (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 event_key VARCHAR(160) NOT NULL, category VARCHAR(32) NOT NULL CHECK(category IN ('SECURITY','LEARNING','COMPETITION','PORTFOLIO_EDUCATION','PRODUCT','BILLING')),
 title VARCHAR(160) NOT NULL, body VARCHAR(1000) NOT NULL, href VARCHAR(300) NOT NULL,
 promotional BOOLEAN NOT NULL DEFAULT FALSE CHECK(NOT promotional OR category='PRODUCT'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, read_at TIMESTAMPTZ,
 UNIQUE(user_id,event_key)
);
CREATE INDEX idx_notification_inbox ON user_notification(user_id,created_at DESC,id);
CREATE INDEX idx_notification_unread ON user_notification(user_id) WHERE read_at IS NULL;
CREATE TABLE notification_device (
 token_hash VARCHAR(64) PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 token TEXT NOT NULL, platform VARCHAR(16) NOT NULL CHECK(platform='ANDROID'),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notification_device_user ON notification_device(user_id,updated_at DESC);
CREATE TABLE notification_outbox (
 id UUID PRIMARY KEY, notification_id UUID NOT NULL REFERENCES user_notification(id) ON DELETE CASCADE,
 channel VARCHAR(8) NOT NULL CHECK(channel IN ('EMAIL','PUSH')),
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','SENT','CANCELLED','UNCERTAIN','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 3),
 available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, claimed_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
 UNIQUE(notification_id,channel)
);
CREATE INDEX idx_notification_outbox_due ON notification_outbox(available_at,id) WHERE status='PENDING';
CREATE TABLE notification_suppression (
 user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 reason VARCHAR(16) NOT NULL CHECK(reason IN ('UNSUBSCRIBED','BOUNCE','COMPLAINT')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE notification_unsubscribe (
 token_hash VARCHAR(64) PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE notification_source_seen (
 user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE, event_key VARCHAR(160) NOT NULL,
 observed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(user_id,event_key)
);
CREATE TABLE notification_observation (
 singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK(singleton), started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO notification_observation(singleton) VALUES(TRUE);
