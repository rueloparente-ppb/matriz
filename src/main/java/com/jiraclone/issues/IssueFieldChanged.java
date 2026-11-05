package com.jiraclone.issues;

public record IssueFieldChanged(long issueId, long actorId, String field, String oldValue, String newValue) {
}
