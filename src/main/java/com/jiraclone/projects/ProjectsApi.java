package com.jiraclone.projects;

import java.util.Optional;

/** Public synchronous API of the projects module. */
public interface ProjectsApi {

	Optional<ProjectRef> findByKey(String key);

	Optional<ProjectAccess> findAccessible(String key, long userId);
}
