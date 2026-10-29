package com.jiraclone.issues.core;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class IssueSpecs {

	private IssueSpecs() {
	}

	/** Issues of one project matching every restriction; {@code q} is a case-insensitive title or {@code KEY-n} substring. */
	static Specification<Issue> matching(long projectId, String projectKey, IssueFilter filter) {
		return (root, query, cb) -> {
			var predicates = new ArrayList<Predicate>();
			predicates.add(cb.equal(root.get("projectId"), projectId));
			if (!filter.statuses().isEmpty()) {
				predicates.add(root.get("status").in(filter.statuses()));
			}
			if (!filter.types().isEmpty()) {
				predicates.add(root.get("type").in(filter.types()));
			}
			switch (filter.assignee()) {
				case AssigneeFilter.Any any -> { }
				case AssigneeFilter.Unassigned none -> predicates.add(cb.isNull(root.get("assigneeId")));
				case AssigneeFilter.User user -> predicates.add(cb.equal(root.get("assigneeId"), user.id()));
			}
			if (!filter.query().isEmpty()) {
				String pattern = "%" + escapeLike(filter.query().toLowerCase(Locale.ROOT)) + "%";
				var key = cb.concat(cb.literal(projectKey + "-"), root.get("number").as(String.class));
				predicates.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
						cb.like(cb.lower(key), pattern, '\\')));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static String escapeLike(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
