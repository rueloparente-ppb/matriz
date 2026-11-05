CREATE TABLE issue_history (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    issue_id   bigint       NOT NULL REFERENCES issue (id),
    actor_id   bigint       NOT NULL REFERENCES app_user (id),
    actor_name varchar(100) NOT NULL,
    kind       varchar(20)  NOT NULL CHECK (kind IN ('CREATED', 'FIELD_CHANGED', 'SPRINT_CHANGED')),
    field      varchar(20),
    old_value  text,
    new_value  text,
    created_at timestamptz  NOT NULL,
    CONSTRAINT issue_history_field CHECK ((kind = 'FIELD_CHANGED') = (field IS NOT NULL))
);

CREATE INDEX issue_history_issue_order ON issue_history (issue_id, created_at DESC, id DESC);
