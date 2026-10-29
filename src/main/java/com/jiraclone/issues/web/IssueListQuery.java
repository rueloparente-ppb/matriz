package com.jiraclone.issues.web;

import com.jiraclone.identity.IdentityApi;
import com.jiraclone.issues.IssueStatus;
import com.jiraclone.issues.IssueType;
import com.jiraclone.issues.core.AssigneeFilter;
import com.jiraclone.issues.core.IssueFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Raw list parameters (blank = absent) and their strict parsing. Any invalid value is reported, never ignored,
 * so the list is never silently shown unfiltered (ISS-12).
 */
record IssueListQuery(String status, String type, String assignee, String q, String page) {

	record Parsed(IssueFilter filter, int page, List<String> errors) {

		boolean valid() {
			return errors.isEmpty();
		}
	}

	Parsed parse(IdentityApi identity) {
		var errors = new ArrayList<String>();
		Set<IssueStatus> statuses = Set.of();
		if (!blank(status)) {
			try {
				statuses = Set.of(IssueStatus.valueOf(status.trim()));
			} catch (IllegalArgumentException e) {
				errors.add("Unknown status: " + status);
			}
		}
		Set<IssueType> types = Set.of();
		if (!blank(type)) {
			try {
				types = Set.of(IssueType.valueOf(type.trim()));
			} catch (IllegalArgumentException e) {
				errors.add("Unknown type: " + type);
			}
		}
		AssigneeFilter assigneeFilter = new AssigneeFilter.Any();
		if (!blank(assignee)) {
			if (assignee.trim().equals("none")) {
				assigneeFilter = new AssigneeFilter.Unassigned();
			} else {
				try {
					long id = Long.parseLong(assignee.trim());
					if (identity.findUser(id).isPresent()) {
						assigneeFilter = new AssigneeFilter.User(id);
					} else {
						errors.add("Unknown assignee: " + assignee);
					}
				} catch (NumberFormatException e) {
					errors.add("Unknown assignee: " + assignee);
				}
			}
		}
		int pageNumber = 1;
		if (!blank(page)) {
			try {
				pageNumber = Integer.parseInt(page.trim());
				if (pageNumber < 1) {
					errors.add("Page must be 1 or greater");
				}
			} catch (NumberFormatException e) {
				errors.add("Page must be a number");
			}
		}
		String query = blank(q) ? "" : q.trim();
		return new Parsed(new IssueFilter(statuses, types, assigneeFilter, query), pageNumber, errors);
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
