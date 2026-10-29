package com.jiraclone.issues.web;

/** Display model for an issue: names and timestamps already resolved to text. */
public record IssueView(String key, int number, String title, String description, String type, String priority,
		String status, String assigneeName, String reporterName, String createdAt, String updatedAt) {
}
