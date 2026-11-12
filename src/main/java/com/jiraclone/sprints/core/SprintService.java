package com.jiraclone.sprints.core;

import com.jiraclone.common.NotFoundException;
import com.jiraclone.issues.IssueRef;
import com.jiraclone.issues.IssueStatus;
import com.jiraclone.issues.IssuesApi;
import com.jiraclone.sprints.IssueSprintChanged;
import com.jiraclone.sprints.SprintSummary;
import com.jiraclone.sprints.SprintsApi;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SprintService implements SprintsApi {
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

	@Override
	@Transactional(readOnly = true)
	public List<SprintSummary> list(long projectId) {
		Map<Long, Long> counts = jdbc.query("select sprint_id, count(*) from sprint_issue where sprint_id in "
				+ "(select id from sprint where project_id = ?) group by sprint_id", rs -> {
					Map<Long, Long> result = new java.util.HashMap<>();
					while (rs.next()) result.put(rs.getLong(1), rs.getLong(2));
					return result;
				}, projectId);
		return sprints.findByProjectIdOrderByIdAsc(projectId).stream()
				.map(s -> new SprintSummary(s.getId(), s.getName(), s.getState().name(), counts.getOrDefault(s.getId(), 0L)))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Set<Long> issueIds(long projectId, String filter) {
		if (filter.equals("none")) {
			Set<Long> assigned = Set.copyOf(jdbc.queryForList("select si.issue_id from sprint_issue si "
					+ "join sprint s on s.id=si.sprint_id where s.project_id=?", Long.class, projectId));
			return issues.findByProject(projectId).stream().map(IssueRef::id)
					.filter(id -> !assigned.contains(id)).collect(Collectors.toSet());
		}
		long id = parseId(filter);
		find(projectId, id);
		return Set.copyOf(jdbc.queryForList("select issue_id from sprint_issue where sprint_id=?", Long.class, id));
	}

	@Transactional
	public void assign(long projectId, int number, String raw, long actorId) {
		IssueRef issue = issues.findIssue(projectId, number)
				.orElseThrow(() -> new NotFoundException("Issue " + number));
		Sprint next = raw.isEmpty() || raw.equals("none") ? null : find(projectId, parseId(raw));
		if (next != null && next.getState() == Sprint.State.COMPLETED)
			throw new IllegalArgumentException("Completed sprint cannot receive issues");
		List<Long> oldIds = jdbc.queryForList("select sprint_id from sprint_issue where issue_id=?", Long.class, issue.id());
		Sprint old = oldIds.isEmpty() ? null : find(projectId, oldIds.getFirst());
		if (old == null && next == null || old != null && next != null && old.getId().equals(next.getId())) return;
		if (next == null) jdbc.update("delete from sprint_issue where issue_id=?", issue.id());
		else jdbc.update("insert into sprint_issue(issue_id,sprint_id) values (?,?) "
				+ "on conflict (issue_id) do update set sprint_id=excluded.sprint_id", issue.id(), next.getId());
		issues.markUpdated(issue.id());
		events.publishEvent(new IssueSprintChanged(issue.id(), actorId,
				old == null ? "None" : old.getName(), next == null ? "None" : next.getName()));
	}

	@Transactional(readOnly = true)
	public String currentName(long projectId, long issueId) {
		List<Long> ids = jdbc.queryForList("select sprint_id from sprint_issue where issue_id=?", Long.class, issueId);
		return ids.isEmpty() ? "None" : find(projectId, ids.getFirst()).getName();
	}

	@Transactional(readOnly = true)
	public String currentId(long issueId) {
		List<Long> ids = jdbc.queryForList("select sprint_id from sprint_issue where issue_id=?", Long.class, issueId);
		return ids.isEmpty() ? "none" : ids.getFirst().toString();
	}

	private static long parseId(String raw) {
		if (!raw.matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid sprint");
		try {
			long id = Long.parseLong(raw);
			if (id > 0) return id;
		} catch (NumberFormatException ignored) { }
		throw new IllegalArgumentException("Invalid sprint");
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
