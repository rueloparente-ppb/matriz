package com.jiraclone.sprints.core;

import com.jiraclone.common.NotFoundException;
import com.jiraclone.issues.IssueRef;
import com.jiraclone.issues.IssueStatus;
import com.jiraclone.issues.IssuesApi;
import com.jiraclone.sprints.IssueSprintChanged;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SprintService {
	private final SprintRepository sprints;
	private final JdbcTemplate jdbc;
	private final IssuesApi issues;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactions;

	SprintService(SprintRepository sprints, JdbcTemplate jdbc, IssuesApi issues,
			ApplicationEventPublisher events, TransactionTemplate transactions) {
		this.sprints = sprints;
		this.jdbc = jdbc;
		this.issues = issues;
		this.events = events;
		this.transactions = transactions;
	}

	public record Entry(long id, String name, String state, long issueCount) { }

	@Transactional(readOnly = true)
	public List<Entry> list(long projectId) {
		Map<Long, Long> counts = jdbc.query("select sprint_id, count(*) from sprint_issue where sprint_id in "
				+ "(select id from sprint where project_id = ?) group by sprint_id", rs -> {
					Map<Long, Long> result = new java.util.HashMap<>();
					while (rs.next()) result.put(rs.getLong(1), rs.getLong(2));
					return result;
				}, projectId);
		return sprints.findByProjectIdOrderByIdAsc(projectId).stream()
				.map(s -> new Entry(s.getId(), s.getName(), s.getState().name(), counts.getOrDefault(s.getId(), 0L)))
				.toList();
	}

	/** Separate write transaction lets us classify a failed unique constraint in a fresh transaction. */
	public boolean create(long projectId, String name) {
		String trimmed = name.trim();
		if (trimmed.isEmpty() || trimmed.length() > 100) throw new IllegalArgumentException("Name must be 1–100 characters");
		try {
			transactions.executeWithoutResult(status -> sprints.saveAndFlush(new Sprint(projectId, trimmed)));
			return true;
		} catch (DataIntegrityViolationException ex) {
			if (sprints.existsByProjectIdAndNameIgnoreCase(projectId, trimmed)) return false;
			throw ex;
		}
	}

	public boolean start(long projectId, long id) {
		try {
			return Boolean.TRUE.equals(transactions.execute(status -> {
				Sprint sprint = find(projectId, id);
				if (sprint.getState() != Sprint.State.PLANNED) return false;
				sprint.setState(Sprint.State.ACTIVE);
				sprints.saveAndFlush(sprint);
				return true;
			}));
		} catch (DataIntegrityViolationException ex) {
			// The partial unique index decides races between two different planned sprints.
			if (jdbc.queryForObject("select count(*) from sprint where project_id = ? and state = 'ACTIVE'",
					Integer.class, projectId) > 0) return false;
			throw ex;
		}
	}

	@Transactional
	public boolean complete(long projectId, long id, long actorId) {
		Sprint sprint = find(projectId, id);
		if (sprint.getState() != Sprint.State.ACTIVE) return false;
		List<Long> memberIds = jdbc.queryForList("select issue_id from sprint_issue where sprint_id = ?", Long.class, id);
		Map<Long, IssueRef> members = issues.findByIds(memberIds).stream().collect(Collectors.toMap(IssueRef::id, Function.identity()));
		for (long issueId : memberIds) {
			if (members.get(issueId).status() != IssueStatus.DONE) {
				jdbc.update("delete from sprint_issue where issue_id = ?", issueId);
				issues.markUpdated(issueId);
				events.publishEvent(new IssueSprintChanged(issueId, actorId, sprint.getName(), "None"));
			}
		}
		sprint.setState(Sprint.State.COMPLETED);
		return true;
	}

	private Sprint find(long projectId, long id) {
		return sprints.findByIdAndProjectId(id, projectId).orElseThrow(() -> new NotFoundException("Sprint " + id));
	}
}
