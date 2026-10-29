package com.jiraclone.issues.core;

import com.jiraclone.issues.IssuePriority;
import com.jiraclone.issues.IssueStatus;
import com.jiraclone.issues.IssueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "issue")
class Issue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private long projectId;

	@Column(nullable = false, updatable = false)
	private int number;

	@Column(nullable = false)
	private String title;

	@Column
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IssueType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IssuePriority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IssueStatus status;

	@Column(name = "assignee_id")
	private Long assigneeId;

	@Column(name = "reporter_id", nullable = false, updatable = false)
	private long reporterId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Issue() {
	}

	Issue(long projectId, int number, String title, String description, IssueType type, IssuePriority priority,
			Long assigneeId, long reporterId) {
		this.projectId = projectId;
		this.number = number;
		this.title = title;
		this.description = description;
		this.type = type;
		this.priority = priority;
		this.status = IssueStatus.TO_DO;
		this.assigneeId = assigneeId;
		this.reporterId = reporterId;
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		this.updatedAt = this.createdAt;
	}

	Long getId() {
		return id;
	}

	long getProjectId() {
		return projectId;
	}

	int getNumber() {
		return number;
	}

	String getTitle() {
		return title;
	}

	String getDescription() {
		return description;
	}

	IssueType getType() {
		return type;
	}

	IssuePriority getPriority() {
		return priority;
	}

	IssueStatus getStatus() {
		return status;
	}

	Long getAssigneeId() {
		return assigneeId;
	}

	long getReporterId() {
		return reporterId;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}
}
