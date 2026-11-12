package com.jiraclone.issues.core;

import com.jiraclone.issues.IssuePriority;
import com.jiraclone.issues.IssueStatus;
import com.jiraclone.issues.IssueType;
import java.time.Instant;
import java.util.Set;

/** An issue as the web adapter sees it. {@code description} is empty (never null) when not set. */
public record IssueDetail(long id, long projectId, int number, String title, String description, IssueType type,
		IssuePriority priority, IssueStatus status, Set<Long> assigneeIds, long reporterId, Instant createdAt,
		Instant updatedAt) {
	public IssueDetail {
		assigneeIds = Set.copyOf(assigneeIds);
	}
}
