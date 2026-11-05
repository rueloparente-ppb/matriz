package com.jiraclone.issues;

import java.util.OptionalLong;

public record IssueRef(long id, long projectId, int number, String title, IssueType type, IssuePriority priority,
		IssueStatus status, OptionalLong assigneeId) {
}
