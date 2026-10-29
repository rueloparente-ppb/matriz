package com.jiraclone.issues.core;

import com.jiraclone.issues.IssueStatus;
import com.jiraclone.issues.IssueType;
import java.util.Set;

/** Validated list restrictions, combined by AND. Empty sets and an empty query mean "no restriction". */
public record IssueFilter(Set<IssueStatus> statuses, Set<IssueType> types, AssigneeFilter assignee, String query) {

	public IssueFilter {
		statuses = Set.copyOf(statuses);
		types = Set.copyOf(types);
	}
}
