package com.jiraclone.comments.core;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CommentRepository extends JpaRepository<Comment, Long> {
	List<Comment> findByIssueIdOrderByCreatedAtAscIdAsc(long issueId);
}
