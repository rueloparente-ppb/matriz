package com.jiraclone.projects.web;

import com.jiraclone.common.ErrorViews;
import com.jiraclone.common.NotFoundException;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.core.CreateProjectResult;
import com.jiraclone.projects.core.ProjectService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

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
	@InitBinder({"form", "editForm"})
	void initBinder(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, "name", new StringTrimmerEditor(false));
	}

	@GetMapping("/projects")
	Object list(@RequestParam(required = false) String q, @RequestParam(required = false) String page, Model model) {
		String query = q == null ? "" : q.trim();
		model.addAttribute("q", query);
		int number = 1;
		if (page != null && !page.isBlank()) {
			try {
				number = Integer.parseInt(page.trim());
			} catch (NumberFormatException e) {
				model.addAttribute("error", "Page must be a number");
				return ErrorViews.unprocessable("projects/list");
			}
			if (number < 1) {
				model.addAttribute("error", "Page must be 1 or greater");
				return ErrorViews.unprocessable("projects/list");
			}
		}
		var result = projects.searchAccessible(identity.currentUser().orElseThrow().id(), query, number);
		model.addAttribute("projects", result.getContent());
		model.addAttribute("page", number);
		model.addAttribute("totalPages", result.getTotalPages());
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
		return switch (projects.create(form.getKey(), form.getName(), form.getDescription(), userId)) {
			case CreateProjectResult.Created created -> "redirect:/projects/" + created.project().key();
			case CreateProjectResult.KeyInUse inUse -> {
				errors.rejectValue("key", "key.inUse", "Key already in use");
				yield ErrorViews.unprocessable(FORM_VIEW);
			}
		};
	}

	@GetMapping("/projects/{key}/edit")
	String editForm(@PathVariable String key, Model model) {
		ProjectRef project = owner(key);
		ProjectEditForm form = new ProjectEditForm();
		form.setName(project.name());
		form.setDescription(project.description());
		model.addAttribute("editForm", form);
		model.addAttribute("key", key);
		return "projects/edit";
	}

	@PostMapping("/projects/{key}")
	Object edit(@PathVariable String key, @Valid @ModelAttribute("editForm") ProjectEditForm form,
			BindingResult errors, Model model, @RequestHeader(value = "HX-Request", required = false) String hx) {
		owner(key);
		model.addAttribute("key", key);
		if (errors.hasErrors()) return ErrorViews.unprocessable("projects/edit");
		projects.edit(key, form.getName(), form.getDescription() == null ? "" : form.getDescription());
		if ("true".equalsIgnoreCase(hx)) {
			return ResponseEntity.ok().header("HX-Redirect", "/projects/" + key).build();
		}
		return "redirect:/projects/" + key;
	}

	@GetMapping("/projects/{key}/delete")
	String deleteForm(@PathVariable String key, Model model) {
		model.addAttribute("project", owner(key));
		return "projects/delete";
	}

	@PostMapping("/projects/{key}/delete")
	Object delete(@PathVariable String key, @RequestHeader(value = "HX-Request", required = false) String hx) {
		owner(key);
		projects.delete(key);
		return "true".equalsIgnoreCase(hx) ? ResponseEntity.ok().header("HX-Redirect", "/projects").build()
				: "redirect:/projects";
	}

	private ProjectRef owner(String key) {
		var access = projects.findAccessible(key, identity.currentUser().orElseThrow().id())
				.orElseThrow(() -> new NotFoundException("Project " + key));
		access.requireOwner();
		return access.project();
	}

	@GetMapping("/projects/{key}")
	String show(@PathVariable String key) {
		ProjectRef project = projects.findAccessible(key, identity.currentUser().orElseThrow().id())
				.orElseThrow(() -> new NotFoundException("Project " + key)).project();
		return "redirect:/projects/" + project.key() + "/issues";
	}
}
