CREATE TABLE organization (
    id    BIGSERIAL PRIMARY KEY,
    code  VARCHAR(20)  NOT NULL UNIQUE,     -- matches the "org" claim in the JWT
    name  VARCHAR(150) NOT NULL
);

CREATE TABLE company (
    id      BIGSERIAL PRIMARY KEY,
    ticker  VARCHAR(12)  NOT NULL UNIQUE,
    name    VARCHAR(150) NOT NULL,
    country CHAR(2)      NOT NULL
);

CREATE TABLE meeting (
    id               BIGSERIAL PRIMARY KEY,
    external_id      VARCHAR(50) NOT NULL UNIQUE,     -- idempotent upserts from the ingest feed
    company_id       BIGINT NOT NULL REFERENCES company(id),
    meeting_date     DATE NOT NULL,                    -- local date in the market
    vote_deadline    TIMESTAMPTZ NOT NULL,             -- an exact instant (stored in UTC)
    market_time_zone VARCHAR(40) NOT NULL,             -- e.g. America/New_York, for display
    meeting_type     VARCHAR(5) NOT NULL CHECK (meeting_type IN ('AGM', 'EGM')),
    updated_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_meeting_deadline ON meeting(vote_deadline);

CREATE TABLE proposal (
    id                      BIGSERIAL PRIMARY KEY,
    meeting_id              BIGINT NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    seq                     INT NOT NULL,
    category                VARCHAR(30) NOT NULL,
    title                   VARCHAR(300) NOT NULL,
    description             TEXT,
    board_recommendation    VARCHAR(10) NOT NULL CHECK (board_recommendation IN ('FOR', 'AGAINST')),
    pay_score               INT CHECK (pay_score BETWEEN 0 AND 100),
    board_independence_pct  INT CHECK (board_independence_pct BETWEEN 0 AND 100),
    ai_summary              TEXT,
    ai_summary_source       VARCHAR(20),
    CONSTRAINT uq_proposal_seq UNIQUE (meeting_id, seq)
);

CREATE TABLE voting_policy (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT NOT NULL UNIQUE REFERENCES organization(id),   -- one active policy per org
    name             VARCHAR(100) NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL,
    updated_by       VARCHAR(50) NOT NULL
);

CREATE TABLE policy_rule (
    id              BIGSERIAL PRIMARY KEY,
    policy_id       BIGINT NOT NULL REFERENCES voting_policy(id) ON DELETE CASCADE,
    priority        INT NOT NULL,
    category        VARCHAR(30),          -- NULL = any category
    condition_type  VARCHAR(40),          -- NULL = no extra condition
    threshold       INT,
    decision        VARCHAR(10) NOT NULL CHECK (decision IN ('FOR', 'AGAINST', 'ABSTAIN')),
    rationale       VARCHAR(300) NOT NULL
);

CREATE TABLE recommendation (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT NOT NULL REFERENCES organization(id),
    proposal_id      BIGINT NOT NULL REFERENCES proposal(id) ON DELETE CASCADE,
    decision         VARCHAR(10) NOT NULL,
    rationale        VARCHAR(500) NOT NULL,
    rule_priority    INT,
    generated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_recommendation UNIQUE (organization_id, proposal_id)
);

CREATE TABLE vote (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT NOT NULL REFERENCES organization(id),
    proposal_id      BIGINT NOT NULL REFERENCES proposal(id) ON DELETE CASCADE,
    decision         VARCHAR(10) NOT NULL CHECK (decision IN ('FOR', 'AGAINST', 'ABSTAIN')),
    submitted_by     VARCHAR(50) NOT NULL,
    submitted_at     TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_vote UNIQUE (organization_id, proposal_id)
);

-- Append-only audit trail (in production: the app's DB role gets INSERT + SELECT only on this table)
CREATE TABLE audit_event (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT REFERENCES organization(id),
    actor            VARCHAR(50) NOT NULL,
    action           VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(30) NOT NULL,
    entity_id        VARCHAR(50) NOT NULL,
    details          JSONB,
    occurred_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_org_time ON audit_event(organization_id, occurred_at DESC);

-- Idempotent consumer: an SQS message is processed at most once even if it's delivered twice
CREATE TABLE processed_message (
    message_id    VARCHAR(100) PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL
);
