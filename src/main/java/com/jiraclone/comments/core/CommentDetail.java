package com.jiraclone.comments.core;

import java.time.Instant;

public record CommentDetail(long authorId, String body, Instant createdAt) {
}
