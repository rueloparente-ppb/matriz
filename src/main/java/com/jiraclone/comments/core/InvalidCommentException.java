package com.jiraclone.comments.core;

public class InvalidCommentException extends RuntimeException {
	public InvalidCommentException() {
		super("Comment must be 1–2000 characters");
	}
}
