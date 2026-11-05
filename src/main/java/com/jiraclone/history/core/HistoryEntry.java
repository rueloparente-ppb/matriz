package com.jiraclone.history.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "issue_history")
public class HistoryEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "issue_id", nullable = false, updatable = false)
	private long issueId;
	@Column(name = "actor_id", nullable = false, updatable = false)
	private long actorId;
	@Column(name = "actor_name", nullable = false, updatable = false)
	private String actorName;
	@Column(nullable = false, updatable = false)
	private String kind;
	@Column(updatable = false)
	private String field;
	@Column(name = "old_value", columnDefinition = "text", updatable = false)
	private String oldValue;
	@Column(name = "new_value", columnDefinition = "text", updatable = false)
	private String newValue;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected HistoryEntry() {
	}

	HistoryEntry(long issueId, long actorId, String actorName, String kind, String field, String oldValue, String newValue) {
		this.issueId = issueId;
		this.actorId = actorId;
		this.actorName = actorName;
		this.kind = kind;
		this.field = field;
		this.oldValue = oldValue;
		this.newValue = newValue;
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public String getActorName() { return actorName; }
	public String getKind() { return kind; }
	public String getField() { return field; }
	public String getOldValue() { return oldValue; }
	public String getNewValue() { return newValue; }
	public Instant getCreatedAt() { return createdAt; }
}
