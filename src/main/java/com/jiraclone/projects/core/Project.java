package com.jiraclone.projects.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "project")
class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "key", nullable = false, updatable = false)
	private String key;

	@Column(nullable = false, updatable = false)
	private String name;

	@Column(name = "created_by", nullable = false, updatable = false)
	private Long createdBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Project() {
	}

	Project(String key, String name, long createdBy) {
		this.key = key;
		this.name = name;
		this.createdBy = createdBy;
	}

	Long getId() {
		return id;
	}

	String getKey() {
		return key;
	}

	String getName() {
		return name;
	}
}
