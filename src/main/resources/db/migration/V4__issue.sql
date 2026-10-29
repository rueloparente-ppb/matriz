CREATE TABLE issue_counter (
    project_id  bigint  PRIMARY KEY REFERENCES project (id),
    last_number integer NOT NULL
);

CREATE TABLE issue (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id  bigint        NOT NULL REFERENCES project (id),
    number      integer       NOT NULL,
    title       varchar(200)  NOT NULL,
    description varchar(5000),
    type        varchar(20)   NOT NULL DEFAULT 'TASK',
    priority    varchar(20)   NOT NULL DEFAULT 'MEDIUM',
    status      varchar(20)   NOT NULL DEFAULT 'TO_DO',
    assignee_id bigint        REFERENCES app_user (id),
    reporter_id bigint        NOT NULL REFERENCES app_user (id),
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT issue_project_number_uq UNIQUE (project_id, number),
    CONSTRAINT issue_number_positive CHECK (number >= 1),
    CONSTRAINT issue_type_check CHECK (type IN ('TASK', 'BUG', 'STORY')),
    CONSTRAINT issue_priority_check CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT issue_status_check CHECK (status IN ('TO_DO', 'IN_PROGRESS', 'DONE'))
);
