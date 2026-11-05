package com.jiraclone.issues.core;

import com.jiraclone.common.NotFoundException;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.issues.IssueCreated;
import com.jiraclone.issues.IssueFieldChanged;
import com.jiraclone.issues.IssuePriority;
import com.jiraclone.issues.IssueRef;
import com.jiraclone.issues.IssueStatus;
import com.jiraclone.issues.IssueType;
import com.jiraclone.issues.IssuesApi;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IssueService implements IssuesApi {

	public static final int PAGE_SIZE = 25;

	private final IssueRepository issues;
	private final IssueNumbering numbering;
	private final IdentityApi identity;
	private final ApplicationEventPublisher events;

	IssueService(IssueRepository issues, IssueNumbering numbering, IdentityApi identity, ApplicationEventPublisher events) {
		this.issues = issues;
		this.numbering = numbering;
		this.identity = identity;
		this.events = events;
	}

	/**
	 * Callers validate first: the number is allocated here, inside the transaction, so an invalid create never
	 * consumes one (ISS-3).
	 */
	@Transactional
	public IssueDetail create(long projectId, String title, String description, IssueType type,
			IssuePriority priority, OptionalLong assigneeId, long reporterId) {
		int number = numbering.next(projectId);
		String storedDescription = description == null || description.isEmpty() ? null : description;
		Issue issue = new Issue(projectId, number, title, storedDescription, type, priority,
				assigneeId.isPresent() ? assigneeId.getAsLong() : null, reporterId);
		IssueDetail created = toDetail(issues.saveAndFlush(issue));
		events.publishEvent(new IssueCreated(created.id(), reporterId));
		return created;
	}

	public Optional<IssueDetail> find(long projectId, int number) {
		return issues.findByProjectIdAndNumber(projectId, number).map(IssueService::toDetail);
	}

	@Override
	public Optional<IssueRef> findIssue(long projectId, int number) {
		return issues.findByProjectIdAndNumber(projectId, number).map(IssueService::toRef);
	}

	@Override
	public List<IssueRef> findByIds(Collection<Long> ids) {
		return issues.findAllById(ids).stream().map(IssueService::toRef).toList();
	}

	@Override
	public List<IssueRef> findByProject(long projectId) {
		return issues.findByProjectIdOrderByNumberAsc(projectId).stream().map(IssueService::toRef).toList();
	}

	@Override
	@Transactional
	public IssueRef changeStatus(long projectId, int number, IssueStatus status, long actorId) {
		updateField(projectId, number, "status", status.name(), actorId);
		return findIssue(projectId, number).orElseThrow();
	}

	@Override
	@Transactional
	public void markUpdated(long issueId) {
		issues.findById(issueId).orElseThrow(() -> new NotFoundException("Issue " + issueId)).touch();
	}

	/** Validates and changes exactly one field; events run inside the same transaction. */
	@Transactional
	public IssueDetail updateField(long projectId, int number, String field, String raw, long actorId) {
		if (!List.of("title", "description", "type", "priority", "status", "assignee").contains(field)) {
			throw new NotFoundException("Issue field " + field);
		}
		Issue issue = issues.findByProjectIdAndNumber(projectId, number)
				.orElseThrow(() -> new NotFoundException("Issue " + number));
		Object next = parse(field, raw);
		Object previous = switch (field) {
			case "title" -> issue.getTitle();
			case "description" -> issue.getDescription();
			case "type" -> issue.getType();
			case "priority" -> issue.getPriority();
			case "status" -> issue.getStatus();
			case "assignee" -> issue.getAssigneeId();
			default -> throw new IllegalStateException();
		};
		if (!Objects.equals(previous, next)) {
			String oldValue = display(field, previous);
			switch (field) {
				case "title" -> issue.setTitle((String) next);
				case "description" -> issue.setDescription((String) next);
				case "type" -> issue.setType((IssueType) next);
				case "priority" -> issue.setPriority((IssuePriority) next);
				case "status" -> issue.setStatus((IssueStatus) next);
				case "assignee" -> issue.setAssigneeId((Long) next);
				default -> throw new IllegalStateException();
			}
			issue.touch();
			events.publishEvent(new IssueFieldChanged(issue.getId(), actorId, field, oldValue, display(field, next)));
		}
		return toDetail(issue);
	}

	private Object parse(String field, String raw) {
		return switch (field) {
			case "title" -> {
				String title = raw.trim();
				if (title.isEmpty() || title.length() > 200) {
					throw new InvalidIssueFieldException("Title must be 1–200 characters");
				}
				yield title;
			}
			case "description" -> {
				if (raw.length() > 5000) {
					throw new InvalidIssueFieldException("Description must be at most 5000 characters");
				}
				yield raw.isEmpty() ? null : raw;
			}
			case "type" -> parseEnum(IssueType.class, raw, "type");
			case "priority" -> parseEnum(IssuePriority.class, raw, "priority");
			case "status" -> parseEnum(IssueStatus.class, raw, "status");
			case "assignee" -> parseAssignee(raw);
			default -> throw new NotFoundException("Issue field " + field);
		};
	}

	private Long parseAssignee(String raw) {
		if (raw.isBlank() || raw.equals("none")) {
			return null;
		}
		try {
			long id = Long.parseLong(raw.trim());
			if (identity.findUser(id).isPresent()) {
				return id;
			}
		} catch (NumberFormatException ignored) {
			// Report the same validation error for malformed and unknown ids.
		}
		throw new InvalidIssueFieldException("Unknown assignee");
	}

	private static <T extends Enum<T>> T parseEnum(Class<T> type, String raw, String field) {
		try {
			return Enum.valueOf(type, raw);
		} catch (IllegalArgumentException ex) {
			throw new InvalidIssueFieldException("Invalid " + field);
		}
	}

	private String display(String field, Object value) {
		if (value == null || value.equals("")) {
			return "None";
		}
		if (field.equals("assignee")) {
			return identity.findUser((Long) value).orElseThrow().displayName();
		}
		return value.toString();
	}

	private static IssueRef toRef(Issue i) {
		return new IssueRef(i.getId(), i.getProjectId(), i.getNumber(), i.getTitle(), i.getType(), i.getPriority(),
				i.getStatus(), i.getAssigneeId() == null ? OptionalLong.empty() : OptionalLong.of(i.getAssigneeId()));
	}

	/** One page of a project's issues, number descending, {@value #PAGE_SIZE} per page; {@code page} is 1-based. */
	public IssuePage list(long projectId, String projectKey, IssueFilter filter, int page) {
		var pageable = PageRequest.of(page - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "number"));
		var result = issues.findAll(IssueSpecs.matching(projectId, projectKey, filter), pageable);
		return new IssuePage(result.map(IssueService::toDetail).getContent(), page, result.getTotalPages(),
				result.getTotalElements());
	}

	private static IssueDetail toDetail(Issue i) {
		return new IssueDetail(i.getId(), i.getProjectId(), i.getNumber(), i.getTitle(),
				i.getDescription() == null ? "" : i.getDescription(), i.getType(), i.getPriority(), i.getStatus(),
				i.getAssigneeId() == null ? OptionalLong.empty() : OptionalLong.of(i.getAssigneeId()),
				i.getReporterId(), i.getCreatedAt(), i.getUpdatedAt());
	}
}
