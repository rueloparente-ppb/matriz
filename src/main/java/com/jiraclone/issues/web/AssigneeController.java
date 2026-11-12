package com.jiraclone.issues.web;

import com.jiraclone.common.ErrorViews;
import com.jiraclone.common.NotFoundException;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.identity.UserRef;
import com.jiraclone.issues.core.IssueDetail;
import com.jiraclone.issues.core.IssueService;
import com.jiraclone.issues.core.InvalidIssueFieldException;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.ProjectsApi;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class AssigneeController {
	private final IssueService issues;
	private final ProjectsApi projects;
	private final IdentityApi identity;

	AssigneeController(IssueService issues, ProjectsApi projects, IdentityApi identity) {
		this.issues = issues;
		this.projects = projects;
		this.identity = identity;
	}

	@ModelAttribute("project")
	ProjectRef project(@PathVariable String key) {
		return projects.findAccessible(key, identity.currentUser().orElseThrow().id())
				.orElseThrow(() -> new NotFoundException("Project " + key)).project();
	}

	@GetMapping("/projects/{key}/issues/{number}/assignees")
	String list(@ModelAttribute("project") ProjectRef project, @PathVariable int number,
			@RequestHeader(value = "HX-Request", required = false) String hx, Model model) {
		prepare(project, number, model);
		return view(hx);
	}

	@PostMapping("/projects/{key}/issues/{number}/assignees")
	Object add(@ModelAttribute("project") ProjectRef project, @PathVariable int number,
			@RequestParam(defaultValue = "") String userId,
			@RequestHeader(value = "HX-Request", required = false) String hx, Model model) {
		prepare(project, number, model);
		model.addAttribute("userId", userId);
		try {
			UserRef user = identity.findUser(Long.parseLong(userId)).orElseThrow();
			issues.addAssignee(project.id(), number, user.id(), identity.currentUser().orElseThrow().id(), user.displayName());
		} catch (NumberFormatException | java.util.NoSuchElementException | InvalidIssueFieldException ex) {
			model.addAttribute("error", "Unknown or already assigned user");
			prepare(project, number, model);
			return ErrorViews.unprocessable(view(hx));
		}
		return success(project, number, hx, model);
	}

	@PostMapping("/projects/{key}/issues/{number}/assignees/{userId}/delete")
	Object remove(@ModelAttribute("project") ProjectRef project, @PathVariable int number, @PathVariable String userId,
			@RequestHeader(value = "HX-Request", required = false) String hx, Model model) {
		prepare(project, number, model);
		long id;
		try { id = Long.parseLong(userId); }
		catch (NumberFormatException ex) { throw new NotFoundException("Assignment " + userId); }
		UserRef user = identity.findUser(id).orElseThrow(() -> new NotFoundException("Assignment " + userId));
		issues.removeAssignee(project.id(), number, id, identity.currentUser().orElseThrow().id(), user.displayName());
		return success(project, number, hx, model);
	}

	private Object success(ProjectRef project, int number, String hx, Model model) {
		if (!"true".equalsIgnoreCase(hx)) return "redirect:/projects/" + project.key() + "/issues/" + number;
		prepare(project, number, model);
		return view(hx);
	}

	private void owner(ProjectRef project) {
		projects.findAccessible(project.key(), identity.currentUser().orElseThrow().id()).orElseThrow().requireOwner();
	}

	private void prepare(ProjectRef project, int number, Model model) {
		IssueDetail issue = issues.find(project.id(), number).orElseThrow(() -> new NotFoundException("Issue " + number));
		owner(project);
		Set<Long> assigned = issue.assigneeIds();
		model.addAttribute("number", number);
		model.addAttribute("assigned", identity.listUsers().stream().filter(u -> assigned.contains(u.id())).toList());
		model.addAttribute("eligible", identity.listUsers().stream().filter(u -> !assigned.contains(u.id())).toList());
	}

	private static String view(String hx) {
		return "true".equalsIgnoreCase(hx) ? "issues/assignees :: assignees" : "issues/assignees";
	}
}
