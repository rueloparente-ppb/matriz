package com.jiraclone.projects;

import java.util.Set;

/** Membership derived from issue assignments; implemented by the issues module. */
public interface ProjectMembership {
	boolean isMember(long projectId, long userId);
	Set<Long> projectIds(long userId);
}
