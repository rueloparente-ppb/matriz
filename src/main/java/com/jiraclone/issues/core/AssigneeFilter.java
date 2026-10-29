package com.jiraclone.issues.core;

/** Assignee restriction of the issue list: anyone, nobody ({@code assignee=none}) or one user. */
public sealed interface AssigneeFilter {

	record Any() implements AssigneeFilter {
	}

	record Unassigned() implements AssigneeFilter {
	}

	record User(long id) implements AssigneeFilter {
	}
}
