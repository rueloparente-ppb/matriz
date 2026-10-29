package com.jiraclone.issues.core;

import java.util.List;

/** One page of the issue list; {@code page} is 1-based and may be beyond {@code totalPages} (empty items). */
public record IssuePage(List<IssueDetail> items, int page, int totalPages, long total) {
}
