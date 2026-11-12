package com.jiraclone.comments.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "comment")
class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "issue_id", nullable = false, updatable = false)
	private long issueId;

	@Column(name = "author_id", nullable = false, updatable = false)
	private long authorId;

	@Column(nullable = false, updatable = false, length = 2000)
	private String body;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Comment() {
	}

	Comment(long issueId, long authorId, String body) {
		this.issueId = issueId;
		this.authorId = authorId;
		this.body = body;
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	long getAuthorId() { return authorId; }
	String getBody() { return body; }
	Instant getCreatedAt() { return createdAt; }
}
