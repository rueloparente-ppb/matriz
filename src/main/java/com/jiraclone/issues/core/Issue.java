package com.jiraclone.issues.core;

import com.jiraclone.issues.IssuePriority;
import com.jiraclone.issues.IssueStatus;
import com.jiraclone.issues.IssueType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

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

	@ElementCollection
	@CollectionTable(name = "issue_assignee", joinColumns = @JoinColumn(name = "issue_id"))
	@Column(name = "user_id", nullable = false)
	private Set<Long> assigneeIds = new HashSet<>();

	@Column(name = "reporter_id", nullable = false, updatable = false)
	private long reporterId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Issue() {
	}

	Issue(long projectId, int number, String title, String description, IssueType type, IssuePriority priority,
			long reporterId) {
		this.projectId = projectId;
		this.number = number;
		this.title = title;
		this.description = description;
		this.type = type;
		this.priority = priority;
		this.status = IssueStatus.TO_DO;
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

	Set<Long> getAssigneeIds() {
		return assigneeIds;
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

	void setTitle(String title) { this.title = title; }
	void setDescription(String description) { this.description = description; }
	void setType(IssueType type) { this.type = type; }
	void setPriority(IssuePriority priority) { this.priority = priority; }
	void setStatus(IssueStatus status) { this.status = status; }

	void touch() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plus(1, ChronoUnit.MICROS);
	}
}
