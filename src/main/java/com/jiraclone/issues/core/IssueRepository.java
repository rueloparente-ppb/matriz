package com.jiraclone.issues.core;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

	Optional<Issue> findByProjectIdAndNumber(long projectId, int number);

	List<Issue> findByProjectIdOrderByNumberAsc(long projectId);

	@Query("select count(i) > 0 from Issue i join i.assigneeIds a where i.projectId = :projectId and a = :userId")
	boolean isAssignee(long projectId, long userId);

	@Query("select distinct i.projectId from Issue i join i.assigneeIds a where a = :userId")
	List<Long> projectIdsForAssignee(long userId);
}
