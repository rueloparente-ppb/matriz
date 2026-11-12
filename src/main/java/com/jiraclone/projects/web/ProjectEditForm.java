package com.jiraclone.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProjectEditForm {
	@NotBlank(message = "Name is required")
	@Size(min = 3, max = 100, message = "Name must be 3 to 100 characters")
	private String name;
	@Size(max = 1000, message = "Description must be at most 1000 characters")
	private String description = "";

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
}
