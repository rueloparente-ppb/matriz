package com.jiraclone.sprints.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "sprint")
class Sprint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "project_id", nullable = false, updatable = false)
	private Long projectId;
	@Column(nullable = false)
	private String name;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private State state = State.PLANNED;
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Sprint() {
	}

	Sprint(long projectId, String name) {
		this.projectId = projectId;
		this.name = name;
	}

	Long getId() { return id; }
	Long getProjectId() { return projectId; }
	String getName() { return name; }
	State getState() { return state; }
	void setState(State state) { this.state = state; }

	enum State { PLANNED, ACTIVE, COMPLETED }
}
