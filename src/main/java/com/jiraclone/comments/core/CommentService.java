package com.jiraclone.comments.core;

import com.jiraclone.common.NotFoundException;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.issues.IssuesApi;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommentService {

	private final CommentRepository comments;
	private final IssuesApi issues;
	private final IdentityApi identity;

	CommentService(CommentRepository comments, IssuesApi issues, IdentityApi identity) {
		this.comments = comments;
		this.issues = issues;
		this.identity = identity;
	}

	public List<CommentDetail> list(long projectId, int number) {
		long issueId = issueId(projectId, number);
		return comments.findByIssueIdOrderByCreatedAtAscIdAsc(issueId).stream()
				.map(c -> new CommentDetail(c.getAuthorId(), c.getBody(), c.getCreatedAt())).toList();
	}

	@Transactional
	public void add(long projectId, int number, String body) {
		long issueId = issueId(projectId, number);
		String text = body.trim();
		if (text.isEmpty() || text.length() > 2000) {
			throw new InvalidCommentException();
		}
		comments.save(new Comment(issueId, identity.currentUser().orElseThrow().id(), text));
	}

	private long issueId(long projectId, int number) {
		return issues.findIssue(projectId, number).orElseThrow(() -> new NotFoundException("Issue " + number)).id();
	}
}
