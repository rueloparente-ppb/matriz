package com.jiraclone.board.web;

import com.jiraclone.issues.IssueRef;

public record BoardCard(IssueRef issue, String assignee) {
}
