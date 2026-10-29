package com.jiraclone.issues.core;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface IssueRepository extends JpaRepository<Issue, Long> {

	Optional<Issue> findByProjectIdAndNumber(long projectId, int number);

	List<Issue> findByProjectIdOrderByNumberDesc(long projectId);
}
