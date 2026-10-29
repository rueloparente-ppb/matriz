package com.jiraclone.identity;

/** A user as other modules see them: no credentials, no authentication details. */
public record UserRef(long id, String username, String displayName) {
}
