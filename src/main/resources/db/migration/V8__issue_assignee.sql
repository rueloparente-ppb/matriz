CREATE TABLE issue_assignee (
    issue_id bigint NOT NULL REFERENCES issue (id),
    user_id bigint NOT NULL REFERENCES app_user (id),
    CONSTRAINT issue_assignee_uq UNIQUE (issue_id, user_id)
);

CREATE INDEX issue_assignee_user_idx ON issue_assignee (user_id);

INSERT INTO issue_assignee (issue_id, user_id)
SELECT id, assignee_id FROM issue WHERE assignee_id IS NOT NULL;

ALTER TABLE issue DROP COLUMN assignee_id;
