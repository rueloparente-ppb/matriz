package com.jiraclone.projects;

public record ProjectAccess(ProjectRef project, Role role) {
	public enum Role { OWNER, MEMBER }

	public void requireOwner() {
		if (role != Role.OWNER) {
			throw new com.jiraclone.common.ForbiddenException();
		}
	}
}
