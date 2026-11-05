package com.jiraclone.comments.web;

import com.jiraclone.comments.core.CommentService;
import com.jiraclone.comments.core.InvalidCommentException;
import com.jiraclone.common.ErrorViews;
import com.jiraclone.common.NotFoundException;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.ProjectsApi;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class CommentController {

	private static final String VIEW = "comments/list :: comments";
	private final CommentService comments;
	private final ProjectsApi projects;
	private final IdentityApi identity;

	CommentController(CommentService comments, ProjectsApi projects, IdentityApi identity) {
		this.comments = comments;
		this.projects = projects;
		this.identity = identity;
	}

	@ModelAttribute("project")
	ProjectRef project(@PathVariable String key) {
		return projects.findByKey(key).orElseThrow(() -> new NotFoundException("Project " + key));
	}

	@GetMapping("/projects/{key}/issues/{number}/comments")
	String list(@ModelAttribute("project") ProjectRef project, @PathVariable int number, Model model) {
		populate(project, number, model);
		return VIEW;
	}

	@PostMapping("/projects/{key}/issues/{number}/comments")
	Object add(@ModelAttribute("project") ProjectRef project, @PathVariable int number,
			@RequestParam(defaultValue = "") String body, Model model) {
		try {
			comments.add(project.id(), number, body);
		} catch (InvalidCommentException ex) {
			model.addAttribute("body", body);
			model.addAttribute("error", ex.getMessage());
			populate(project, number, model);
			return ErrorViews.unprocessable(VIEW);
		}
		populate(project, number, model);
		return VIEW;
	}

	private void populate(ProjectRef project, int number, Model model) {
		model.addAttribute("number", number);
		model.addAttribute("comments", comments.list(project.id(), number).stream()
				.map(c -> new CommentView(identity.findUser(c.authorId()).orElseThrow().displayName(),
						c.body(), c.createdAt())).toList());
	}
}
