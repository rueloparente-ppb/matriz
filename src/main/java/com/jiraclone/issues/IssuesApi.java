package com.jiraclone.issues;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Public issue operations for the board and other modules. Project ids must be resolved by ProjectsApi. */
public interface IssuesApi {

	Optional<IssueRef> findIssue(long projectId, int number);

	List<IssueRef> findByIds(Collection<Long> ids);

	IssueRef changeStatus(long projectId, int number, IssueStatus status, long actorId);

	void markUpdated(long issueId);
}
