package com.jiraclone.sprints;

import java.util.List;
import java.util.Set;

public interface SprintsApi {
	List<SprintSummary> list(long projectId);
	Set<Long> issueIds(long projectId, String filter);
}
