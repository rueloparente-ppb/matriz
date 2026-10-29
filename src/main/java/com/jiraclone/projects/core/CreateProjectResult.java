package com.jiraclone.projects.core;

import com.jiraclone.projects.ProjectRef;

public sealed interface CreateProjectResult {

	record Created(ProjectRef project) implements CreateProjectResult {
	}

	record KeyInUse() implements CreateProjectResult {
	}
}
