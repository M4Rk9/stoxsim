ALTER TABLE app_user
    ADD COLUMN platform_role VARCHAR(16) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT ck_app_user_platform_role CHECK (
        platform_role IN ('USER', 'ADMIN')
    );

CREATE TABLE campus_institution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) NOT NULL,
    email_domain VARCHAR(190) NOT NULL,
    website_url VARCHAR(300),
    verified_by_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_campus_institution_name UNIQUE (normalized_name),
    CONSTRAINT uk_campus_institution_domain UNIQUE (email_domain),
    CONSTRAINT ck_campus_institution_name CHECK (
        char_length(trim(name)) BETWEEN 3 AND 160
    ),
    CONSTRAINT ck_campus_institution_domain CHECK (
        email_domain = lower(email_domain)
        AND email_domain NOT LIKE '%@%'
    ),
    CONSTRAINT ck_campus_institution_website CHECK (
        website_url IS NULL OR website_url LIKE 'https://%'
    )
);

CREATE TABLE campus_verification_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    institution_name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) NOT NULL,
    email_domain VARCHAR(190) NOT NULL,
    website_url VARCHAR(300),
    request_status VARCHAR(16) NOT NULL,
    reviewed_by_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    review_note VARCHAR(500),
    submitted_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT ck_campus_request_status CHECK (
        request_status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_campus_request_identity CHECK (
        normalized_name = lower(normalized_name)
        AND email_domain = lower(email_domain)
        AND email_domain NOT LIKE '%@%'
        AND (website_url IS NULL OR website_url LIKE 'https://%')
    ),
    CONSTRAINT ck_campus_request_review CHECK (
        (request_status = 'PENDING' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR
        (request_status IN ('APPROVED', 'REJECTED') AND reviewed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_campus_request_pending_user
    ON campus_verification_request(requester_user_id)
    WHERE request_status = 'PENDING';

CREATE INDEX idx_campus_request_moderation
    ON campus_verification_request(request_status, submitted_at, id);

CREATE TABLE campus_membership (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES campus_institution(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    member_role VARCHAR(16) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_campus_membership_user UNIQUE (user_id),
    CONSTRAINT uk_campus_membership_institution_user UNIQUE (institution_id, user_id),
    CONSTRAINT ck_campus_membership_role CHECK (
        member_role IN ('ORGANIZER', 'MEMBER')
    )
);

CREATE INDEX idx_campus_membership_institution
    ON campus_membership(institution_id, joined_at, user_id);
