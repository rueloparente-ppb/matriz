package com.jiraclone.issues.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class IssueForm {

	@NotBlank(message = "Title is required")
	@Size(max = 200, message = "Title must be at most 200 characters")
	private String title = "";

	@Size(max = 5000, message = "Description must be at most 5000 characters")
	private String description = "";

	@Pattern(regexp = "TASK|BUG|STORY", message = "Invalid type")
	private String type = "TASK";

	@Pattern(regexp = "LOW|MEDIUM|HIGH", message = "Invalid priority")
	private String priority = "MEDIUM";

	private String assigneeId = "";

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getPriority() {
		return priority;
	}

	public void setPriority(String priority) {
		this.priority = priority;
	}

	public String getAssigneeId() {
		return assigneeId;
	}

	public void setAssigneeId(String assigneeId) {
		this.assigneeId = assigneeId;
	}
}
