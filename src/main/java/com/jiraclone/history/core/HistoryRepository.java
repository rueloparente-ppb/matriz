package com.jiraclone.history.core;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface HistoryRepository extends JpaRepository<HistoryEntry, Long> {
	List<HistoryEntry> findByIssueIdOrderByCreatedAtDescIdDesc(long issueId);
}
