package com.jiraclone.issues.core;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Allocates per-project issue numbers (ISS-5). The issues module owns {@code issue_counter}. */
@Component
class IssueNumbering {

	private final JdbcClient jdbc;

	IssueNumbering(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Atomic upsert: the row lock it takes is held until the caller's transaction ends, so concurrent creates in one
	 * project serialize, numbers are gap-free, and a rolled-back create returns its number.
	 */
	int next(long projectId) {
		return jdbc.sql("""
				INSERT INTO issue_counter (project_id, last_number) VALUES (:projectId, 1)
				ON CONFLICT (project_id) DO UPDATE SET last_number = issue_counter.last_number + 1
				RETURNING last_number
				""")
				.param("projectId", projectId)
				.query(Integer.class)
				.single();
	}
}
