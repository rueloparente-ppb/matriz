package com.jiraclone.history.core;

import com.jiraclone.common.NotFoundException;
import com.jiraclone.issues.IssuesApi;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HistoryService {

	private final IssuesApi issues;
	private final HistoryRepository entries;

	HistoryService(IssuesApi issues, HistoryRepository entries) {
		this.issues = issues;
		this.entries = entries;
	}

	public List<HistoryEntry> list(long projectId, int number) {
		long issueId = issues.findIssue(projectId, number)
				.orElseThrow(() -> new NotFoundException("Issue " + number)).id();
		return entries.findByIssueIdOrderByCreatedAtDescIdDesc(issueId);
	}
}
