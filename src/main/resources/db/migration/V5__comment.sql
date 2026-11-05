CREATE TABLE comment (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    issue_id   bigint        NOT NULL REFERENCES issue (id),
    author_id  bigint        NOT NULL REFERENCES app_user (id),
    body       varchar(2000) NOT NULL,
    created_at timestamptz   NOT NULL,
    CONSTRAINT comment_body_length CHECK (length(body) BETWEEN 1 AND 2000)
);

CREATE INDEX comment_issue_order ON comment (issue_id, created_at, id);
