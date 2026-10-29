CREATE TABLE project (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key        varchar(10)  NOT NULL,
    name       varchar(100) NOT NULL,
    created_by bigint       NOT NULL REFERENCES app_user (id),
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT project_key_uq UNIQUE (key),
    CONSTRAINT project_key_format CHECK (key ~ '^[A-Z]{2,10}$')
);
