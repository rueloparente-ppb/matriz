package com.jiraclone.issues.web;

import com.jiraclone.issues.IssueStatus;
import com.jiraclone.projects.ProjectRef;
import java.util.Map;

record ProjectSummary(ProjectRef project, Map<IssueStatus, Long> counts) {
}
