package com.jiraclone.issues.web;

import com.jiraclone.identity.IdentityApi;
import com.jiraclone.issues.IssuesApi;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.ProjectsApi;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class DashboardController {

	private final ProjectsApi projects;
	private final IssuesApi issues;
	private final IdentityApi identity;

	DashboardController(ProjectsApi projects, IssuesApi issues, IdentityApi identity) {
		this.projects = projects;
		this.issues = issues;
		this.identity = identity;
	}

	@GetMapping("/")
	String dashboard(Model model) {
		long userId = identity.currentUser().orElseThrow().id();
		List<ProjectSummary> summaries = projects.listAccessible(userId).stream()
				.map(project -> new ProjectSummary(project, issues.countsByStatus(project.id()))).toList();
		model.addAttribute("projects", summaries);
		return "issues/dashboard";
	}
}
