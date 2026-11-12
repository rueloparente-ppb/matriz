package com.jiraclone.sprints.web;

import com.jiraclone.common.NotFoundException;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.issues.IssueRef;
import com.jiraclone.issues.IssuesApi;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.ProjectsApi;
import com.jiraclone.sprints.core.SprintService;
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
class SprintController {
	private final ProjectsApi projects;
	private final SprintService sprints;
	private final IdentityApi identity;
	private final IssuesApi issues;

	SprintController(ProjectsApi projects, SprintService sprints, IdentityApi identity, IssuesApi issues) {
		this.projects = projects;
		this.sprints = sprints;
		this.identity = identity;
		this.issues = issues;
	}

	@ModelAttribute("project")
	ProjectRef project(@PathVariable String key) {
		return projects.findByKey(key).orElseThrow(() -> new NotFoundException("Project " + key));
	}

	@GetMapping("/projects/{key}/sprints")
	String list(@ModelAttribute("project") ProjectRef project,
			@RequestHeader(value = "HX-Request", required = false) String hx,
			@RequestHeader(value = "HX-History-Restore-Request", required = false) String restore, Model model) {
		populate(project, model);
		return view("true".equalsIgnoreCase(hx) && !"true".equalsIgnoreCase(restore));
	}

	@PostMapping("/projects/{key}/sprints")
	Object create(@ModelAttribute("project") ProjectRef project, @RequestParam(defaultValue = "") String name,
			@RequestHeader(value = "HX-Request", required = false) String hx, Model model) {
		model.addAttribute("name", name);
		try {
			if (!sprints.create(project.id(), name)) return error(project, model, hx, HttpStatus.UNPROCESSABLE_ENTITY, "Name already in use");
		} catch (IllegalArgumentException ex) {
			return error(project, model, hx, HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		}
		return success(project, hx, model);
	}

	@PostMapping("/projects/{key}/sprints/{id}/start")
	Object start(@ModelAttribute("project") ProjectRef project, @PathVariable long id,
			@RequestHeader(value = "HX-Request", required = false) String hx, Model model) {
		if (!sprints.start(project.id(), id)) return error(project, model, hx, HttpStatus.CONFLICT, "Cannot start sprint");
		return success(project, hx, model);
	}

	@PostMapping("/projects/{key}/sprints/{id}/complete")
	Object complete(@ModelAttribute("project") ProjectRef project, @PathVariable long id,
			@RequestHeader(value = "HX-Request", required = false) String hx, Model model) {
		if (!sprints.complete(project.id(), id, identity.currentUser().orElseThrow().id()))
			return error(project, model, hx, HttpStatus.CONFLICT, "Cannot complete sprint");
		return success(project, hx, model);
	}

	@GetMapping("/projects/{key}/issues/{number}/sprint")
	String picker(@ModelAttribute("project") ProjectRef project, @PathVariable int number, Model model) {
		populatePicker(project, number, model);
		return "sprints/picker :: picker";
	}

	@PostMapping("/projects/{key}/issues/{number}/sprint")
	Object assign(@ModelAttribute("project") ProjectRef project, @PathVariable int number,
			@RequestParam(defaultValue = "") String sprint,
			@RequestHeader(value = "HX-Request", required = false) String hx, Model model) {
		issue(project, number);
		try {
			sprints.assign(project.id(), number, sprint, identity.currentUser().orElseThrow().id());
		} catch (IllegalArgumentException ex) {
			populatePicker(project, number, model);
			model.addAttribute("error", ex.getMessage());
			return new ModelAndView("sprints/picker :: picker", HttpStatus.UNPROCESSABLE_ENTITY);
		}
		if (!"true".equalsIgnoreCase(hx))
			return "redirect:/projects/" + project.key() + "/issues/" + number;
		populatePicker(project, number, model);
		return "sprints/picker :: picker";
	}

	private IssueRef issue(ProjectRef project, int number) {
		return issues.findIssue(project.id(), number).orElseThrow(() -> new NotFoundException("Issue " + number));
	}

	private void populatePicker(ProjectRef project, int number, Model model) {
		IssueRef issue = issue(project, number);
		model.addAttribute("number", number);
		model.addAttribute("currentSprint", sprints.currentId(issue.id()));
		model.addAttribute("currentName", sprints.currentName(project.id(), issue.id()));
		model.addAttribute("availableSprints", sprints.list(project.id()).stream()
				.filter(s -> !s.state().equals("COMPLETED")).toList());
	}

	private Object success(ProjectRef project, String hx, Model model) {
		if (!"true".equalsIgnoreCase(hx)) return "redirect:/projects/" + project.key() + "/sprints";
		populate(project, model);
		return view(true);
	}

	private ModelAndView error(ProjectRef project, Model model, String hx, HttpStatus status, String message) {
		populate(project, model);
		model.addAttribute("error", message);
		return new ModelAndView(view("true".equalsIgnoreCase(hx)), status);
	}

	private void populate(ProjectRef project, Model model) {
		model.addAttribute("sprints", sprints.list(project.id()));
	}

	private static String view(boolean fragment) {
		return fragment ? "sprints/list :: sprints" : "sprints/list";
	}
}
