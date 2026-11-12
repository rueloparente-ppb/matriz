package com.jiraclone.projects.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProjectForm {

	@NotNull(message = "Key is required")
	@Pattern(regexp = "[A-Z]{2,10}", message = "Key must be 2 to 10 uppercase letters (A-Z)")
	private String key;

	@NotBlank(message = "Name is required")
	@Size(min = 3, max = 100, message = "Name must be 3 to 100 characters")
	private String name;

	@Size(max = 1000, message = "Description must be at most 1000 characters")
	private String description = "";

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() { return description; }

	public void setDescription(String description) { this.description = description; }
}
