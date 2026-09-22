ALTER TABLE campus_institution ADD COLUMN suspended boolean NOT NULL DEFAULT false;

CREATE TABLE campus_join_request (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id uuid NOT NULL REFERENCES campus_institution(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    note varchar(300) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    review_note varchar(500),
    reviewed_by uuid REFERENCES app_user(id) ON DELETE SET NULL,
    submitted_at timestamptz NOT NULL,
    reviewed_at timestamptz,
    CHECK ((status = 'PENDING' AND reviewed_at IS NULL) OR (status <> 'PENDING' AND reviewed_at IS NOT NULL))
);
CREATE UNIQUE INDEX uk_campus_join_pending_user ON campus_join_request(user_id) WHERE status = 'PENDING';
CREATE INDEX idx_campus_join_queue ON campus_join_request(institution_id, status, submitted_at);

CREATE TABLE campus_competition (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id uuid NOT NULL REFERENCES campus_institution(id) ON DELETE CASCADE,
    title varchar(100) NOT NULL CHECK (char_length(trim(title)) BETWEEN 3 AND 100),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    capacity integer NOT NULL CHECK (capacity BETWEEN 2 AND 200),
    cancelled boolean NOT NULL DEFAULT false,
    cancellation_note varchar(500),
    created_by uuid REFERENCES app_user(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL,
    CHECK (ends_at >= starts_at + interval '1 hour' AND ends_at <= starts_at + interval '90 days')
);
CREATE INDEX idx_campus_competition_institution ON campus_competition(institution_id, starts_at DESC);

CREATE TABLE campus_competition_entry (
    competition_id uuid NOT NULL REFERENCES campus_competition(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES virtual_account(id) ON DELETE CASCADE,
    baseline_value numeric(19,4) NOT NULL CHECK (baseline_value > 0),
    latest_value numeric(19,4) NOT NULL,
    return_percent numeric(12,4) NOT NULL DEFAULT 0,
    data_status varchar(24) NOT NULL,
    joined_at timestamptz NOT NULL,
    valued_at timestamptz NOT NULL,
    withdrawn_at timestamptz,
    PRIMARY KEY (competition_id, user_id)
);
CREATE INDEX idx_campus_entry_user ON campus_competition_entry(user_id);

CREATE TABLE campus_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id uuid NOT NULL REFERENCES campus_institution(id) ON DELETE CASCADE,
    actor_user_id uuid REFERENCES app_user(id) ON DELETE SET NULL,
    target_user_id uuid REFERENCES app_user(id) ON DELETE SET NULL,
    competition_id uuid REFERENCES campus_competition(id) ON DELETE SET NULL,
    action varchar(64) NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_campus_audit_institution ON campus_audit(institution_id, created_at DESC);
