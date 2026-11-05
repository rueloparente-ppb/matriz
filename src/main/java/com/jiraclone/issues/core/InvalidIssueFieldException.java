package com.jiraclone.issues.core;

public class InvalidIssueFieldException extends RuntimeException {

	public InvalidIssueFieldException(String message) {
		super(message);
	}
}
