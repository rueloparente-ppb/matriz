package com.jiraclone.issues.core;

import com.jiraclone.issues.IssuePriority;
import com.jiraclone.issues.IssueType;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IssueService {

	public static final int PAGE_SIZE = 25;

	private final IssueRepository issues;
	private final IssueNumbering numbering;

	IssueService(IssueRepository issues, IssueNumbering numbering) {
		this.issues = issues;
		this.numbering = numbering;
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
		return toDetail(issues.saveAndFlush(issue));
	}

	public Optional<IssueDetail> find(long projectId, int number) {
		return issues.findByProjectIdAndNumber(projectId, number).map(IssueService::toDetail);
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
