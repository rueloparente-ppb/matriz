package com.jiraclone.comments.web;

import java.time.Instant;

record CommentView(String author, String body, Instant createdAt) {
}
