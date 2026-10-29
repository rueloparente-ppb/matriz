package com.jiraclone.projects.web;

import com.jiraclone.common.ErrorViews;
import com.jiraclone.common.NotFoundException;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.core.CreateProjectResult;
import com.jiraclone.projects.core.ProjectService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
class ProjectController {

	private static final String FORM_VIEW = "projects/new";

	private final ProjectService projects;
	private final IdentityApi identity;

	ProjectController(ProjectService projects, IdentityApi identity) {
		this.projects = projects;
		this.identity = identity;
	}

	/** Only the name is trimmed; the key is validated exactly as typed (PRJ-3). */
	@InitBinder("form")
	void initBinder(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, "name", new StringTrimmerEditor(false));
	}

	@GetMapping("/projects")
	String list(Model model) {
		model.addAttribute("projects", projects.list());
		return "projects/list";
	}

	@GetMapping("/projects/new")
	String newForm(Model model) {
		model.addAttribute("form", new ProjectForm());
		return FORM_VIEW;
	}

	@PostMapping("/projects")
	Object create(@Valid @ModelAttribute("form") ProjectForm form, BindingResult errors) {
		if (errors.hasErrors()) {
			return ErrorViews.unprocessable(FORM_VIEW);
		}
		long userId = identity.currentUser().orElseThrow().id();
		return switch (projects.create(form.getKey(), form.getName(), userId)) {
			case CreateProjectResult.Created created -> "redirect:/projects/" + created.project().key();
			case CreateProjectResult.KeyInUse inUse -> {
				errors.rejectValue("key", "key.inUse", "Key already in use");
				yield ErrorViews.unprocessable(FORM_VIEW);
			}
		};
	}

	@GetMapping("/projects/{key}")
	String show(@PathVariable String key) {
		ProjectRef project = projects.findByKey(key).orElseThrow(() -> new NotFoundException("Project " + key));
		return "redirect:/projects/" + project.key() + "/issues";
	}
}
