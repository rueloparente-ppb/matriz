package com.jiraclone.common;

/** Raised by any controller when the addressed resource does not exist; rendered as a {@code 404} page. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
