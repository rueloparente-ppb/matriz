package com.jiraclone.issues;

import java.util.Set;

public record IssueRef(long id, long projectId, int number, String title, IssueType type, IssuePriority priority,
		IssueStatus status, Set<Long> assigneeIds) {
	public IssueRef {
		assigneeIds = Set.copyOf(assigneeIds);
	}
}
