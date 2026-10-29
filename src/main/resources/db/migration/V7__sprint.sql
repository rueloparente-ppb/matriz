CREATE TABLE sprint (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint       NOT NULL REFERENCES project (id),
    name       varchar(100) NOT NULL,
    state      varchar(20)  NOT NULL DEFAULT 'PLANNED',
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT sprint_state_check CHECK (state IN ('PLANNED', 'ACTIVE', 'COMPLETED'))
);

CREATE UNIQUE INDEX sprint_project_name_uq ON sprint (project_id, lower(name));
CREATE UNIQUE INDEX sprint_project_active_uq ON sprint (project_id) WHERE state = 'ACTIVE';

CREATE TABLE sprint_issue (
    issue_id  bigint PRIMARY KEY REFERENCES issue (id),
    sprint_id bigint NOT NULL REFERENCES sprint (id)
);
CREATE INDEX sprint_issue_sprint_idx ON sprint_issue (sprint_id);
