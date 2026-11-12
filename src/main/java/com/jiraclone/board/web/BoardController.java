package com.jiraclone.board.web;

import com.jiraclone.common.ErrorViews;
import com.jiraclone.common.NotFoundException;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.identity.UserRef;
import com.jiraclone.issues.IssueRef;
import com.jiraclone.issues.IssueStatus;
import com.jiraclone.issues.IssuesApi;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.ProjectsApi;
import com.jiraclone.sprints.SprintsApi;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
class BoardController {

	private final ProjectsApi projects;
	private final IssuesApi issues;
	private final IdentityApi identity;
	private final SprintsApi sprints;

	BoardController(ProjectsApi projects, IssuesApi issues, IdentityApi identity, SprintsApi sprints) {
		this.projects = projects;
		this.issues = issues;
		this.identity = identity;
		this.sprints = sprints;
	}

	@ModelAttribute("project")
	ProjectRef project(@PathVariable String key) {
		return projects.findByKey(key).orElseThrow(() -> new NotFoundException("Project " + key));
	}

	@GetMapping("/projects/{key}/board")
	Object board(@ModelAttribute("project") ProjectRef project,
			@RequestParam(required = false) String sprint,
			@RequestHeader(value = "HX-Request", required = false) String hxRequest,
			@RequestHeader(value = "HX-History-Restore-Request", required = false) String historyRestore, Model model) {
		try {
			populate(project, sprint, model);
		} catch (IllegalArgumentException ex) {
			return ErrorViews.unprocessable(invalidView("true".equalsIgnoreCase(hxRequest) && !"true".equalsIgnoreCase(historyRestore)));
		}
		return view("true".equalsIgnoreCase(hxRequest) && !"true".equalsIgnoreCase(historyRestore));
	}

	@PostMapping("/projects/{key}/board/move")
	Object move(@ModelAttribute("project") ProjectRef project, @RequestParam(defaultValue = "") String issue,
			@RequestParam(defaultValue = "") String status,
			@RequestParam(required = false) String sprint,
			@RequestHeader(value = "HX-Request", required = false) String hxRequest, Model model) {
		boolean fragment = "true".equalsIgnoreCase(hxRequest);
		int number;
		try {
			number = Integer.parseInt(issue);
		} catch (NumberFormatException ex) {
			return rejected(project, sprint, model, fragment, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid issue number");
		}
		if (issues.findIssue(project.id(), number).isEmpty()) {
			return rejected(project, sprint, model, fragment, HttpStatus.NOT_FOUND, "Issue not found");
		}
		IssueStatus next;
		try {
			next = IssueStatus.valueOf(status);
		} catch (IllegalArgumentException ex) {
			return rejected(project, sprint, model, fragment, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid status");
		}
		// Validate the filter before changing status, so a rejected refresh cannot mutate an issue.
		if (sprint != null) {
			try { sprints.issueIds(project.id(), sprint); }
			catch (IllegalArgumentException ex) { return ErrorViews.unprocessable(invalidView(fragment)); }
		}
		issues.changeStatus(project.id(), number, next, identity.currentUser().orElseThrow().id());
		populate(project, sprint, model);
		return view(fragment);
	}

	private Object rejected(ProjectRef project, String sprint, Model model, boolean fragment, HttpStatus status, String error) {
		try { populate(project, sprint, model); }
		catch (IllegalArgumentException ex) { return ErrorViews.unprocessable(invalidView(fragment)); }
		model.addAttribute("error", error);
		if (status == HttpStatus.UNPROCESSABLE_ENTITY) {
			return ErrorViews.unprocessable(view(fragment));
		}
		return new ModelAndView(view(fragment), status);
	}

	private void populate(ProjectRef project, String sprint, Model model) {
		Set<Long> included = sprint == null ? null : sprints.issueIds(project.id(), sprint);
		model.addAttribute("sprintFilter", sprint == null ? "" : sprint);
		model.addAttribute("sprints", sprints.list(project.id()));
		Map<Long, UserRef> users = identity.listUsers().stream().collect(Collectors.toMap(UserRef::id, Function.identity()));
		Map<IssueStatus, List<BoardCard>> columns = new EnumMap<>(IssueStatus.class);
		for (IssueStatus status : IssueStatus.values()) {
			columns.put(status, new ArrayList<>());
		}
		for (IssueRef issue : issues.findByProject(project.id())) {
			if (included != null && !included.contains(issue.id())) continue;
			String assignee = issue.assigneeId().isPresent()
					? users.get(issue.assigneeId().getAsLong()).displayName() : "Unassigned";
			columns.get(issue.status()).add(new BoardCard(issue, assignee));
		}
		model.addAttribute("columns", columns);
		model.addAttribute("statuses", IssueStatus.values());
	}

	private static String view(boolean fragment) {
		return fragment ? "board/board :: board" : "board/board";
	}

	private static String invalidView(boolean fragment) {
		return fragment ? "board/invalid :: board" : "board/invalid";
	}

}
