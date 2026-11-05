package com.jiraclone.sprints;

public record IssueSprintChanged(long issueId, long actorId, String oldValue, String newValue) {
}
