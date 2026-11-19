ALTER TABLE comment DROP CONSTRAINT comment_issue_id_fkey;
ALTER TABLE comment ADD CONSTRAINT comment_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE;

ALTER TABLE issue_history DROP CONSTRAINT issue_history_issue_id_fkey;
ALTER TABLE issue_history ADD CONSTRAINT issue_history_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE;

ALTER TABLE issue_assignee DROP CONSTRAINT issue_assignee_issue_id_fkey;
ALTER TABLE issue_assignee ADD CONSTRAINT issue_assignee_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE;

ALTER TABLE sprint_issue DROP CONSTRAINT sprint_issue_issue_id_fkey;
ALTER TABLE sprint_issue ADD CONSTRAINT sprint_issue_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES issue (id) ON DELETE CASCADE;

ALTER TABLE sprint_issue DROP CONSTRAINT sprint_issue_sprint_id_fkey;
ALTER TABLE sprint_issue ADD CONSTRAINT sprint_issue_sprint_id_fkey FOREIGN KEY (sprint_id) REFERENCES sprint (id) ON DELETE CASCADE;

ALTER TABLE issue DROP CONSTRAINT issue_project_id_fkey;
ALTER TABLE issue ADD CONSTRAINT issue_project_id_fkey FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE;

ALTER TABLE issue_counter DROP CONSTRAINT issue_counter_project_id_fkey;
ALTER TABLE issue_counter ADD CONSTRAINT issue_counter_project_id_fkey FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE;

ALTER TABLE sprint DROP CONSTRAINT sprint_project_id_fkey;
ALTER TABLE sprint ADD CONSTRAINT sprint_project_id_fkey FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE;
