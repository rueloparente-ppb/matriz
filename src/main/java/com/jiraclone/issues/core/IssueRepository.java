package com.jiraclone.issues.core;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

	Optional<Issue> findByProjectIdAndNumber(long projectId, int number);
}
