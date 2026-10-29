package com.jiraclone.projects;

/** A project as other modules see it. */
public record ProjectRef(long id, String key, String name) {
}
