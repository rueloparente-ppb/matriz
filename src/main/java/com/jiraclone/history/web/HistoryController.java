package com.jiraclone.history.web;

import com.jiraclone.common.NotFoundException;
import com.jiraclone.history.core.HistoryService;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.ProjectsApi;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
class HistoryController {

	private final ProjectsApi projects;
	private final HistoryService history;

	HistoryController(ProjectsApi projects, HistoryService history) {
		this.projects = projects;
		this.history = history;
	}

	@ModelAttribute("project")
	ProjectRef project(@PathVariable String key) {
		return projects.findByKey(key).orElseThrow(() -> new NotFoundException("Project " + key));
	}

	@GetMapping("/projects/{key}/issues/{number}/history")
	String list(@ModelAttribute("project") ProjectRef project, @PathVariable int number, Model model) {
		model.addAttribute("entries", history.list(project.id(), number).stream()
				.map(e -> new HistoryView(e.getActorName(), e.getCreatedAt(), e.getKind(), e.getField(),
						HistoryView.shorten(e.getOldValue()), HistoryView.shorten(e.getNewValue()))).toList());
		return "history/list :: history";
	}
}
