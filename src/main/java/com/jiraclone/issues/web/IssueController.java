package com.jiraclone.issues.web;

import com.jiraclone.common.ErrorViews;
import com.jiraclone.common.NotFoundException;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.identity.UserRef;
import com.jiraclone.issues.IssuePriority;
import com.jiraclone.issues.IssueType;
import com.jiraclone.issues.core.IssueDetail;
import com.jiraclone.issues.core.IssueService;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.ProjectsApi;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
class IssueController {

	private static final String LIST_VIEW = "issues/list";
	private static final String FORM_VIEW = "issues/new";
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	private final IssueService issues;
	private final ProjectsApi projects;
	private final IdentityApi identity;

	IssueController(IssueService issues, ProjectsApi projects, IdentityApi identity) {
		this.issues = issues;
		this.projects = projects;
		this.identity = identity;
	}

	/** Resolved before binding and validation, so an unknown project is a 404 before anything else (precedence). */
	@ModelAttribute("project")
	ProjectRef project(@PathVariable String key) {
		return projects.findByKey(key).orElseThrow(() -> new NotFoundException("Project " + key));
	}

	@InitBinder("form")
	void initBinder(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, "title", new StringTrimmerEditor(false));
	}

	@GetMapping("/projects/{key}/issues")
	Object list(@ModelAttribute("project") ProjectRef project, IssueListQuery query,
			@RequestHeader(value = "HX-Request", required = false) String hxRequest,
			@RequestHeader(value = "HX-History-Restore-Request", required = false) String historyRestore,
			Model model) {
		// A history restore after a cache miss needs the full page, not the fragment.
		boolean fragment = "true".equalsIgnoreCase(hxRequest) && !"true".equalsIgnoreCase(historyRestore);
		String view = fragment ? LIST_VIEW + " :: results" : LIST_VIEW;
		var parsed = query.parse(identity);
		model.addAttribute("query", query);
		model.addAttribute("users", identity.listUsers());
		model.addAttribute("errors", parsed.errors());
		if (!parsed.valid()) {
			return ErrorViews.unprocessable(view);
		}
		var result = issues.list(project.id(), project.key(), parsed.filter(), parsed.page());
		Map<Long, UserRef> users = userIndex();
		model.addAttribute("issues", result.items().stream().map(i -> toView(project, i, users)).toList());
		model.addAttribute("page", result.page());
		model.addAttribute("totalPages", result.totalPages());
		return view;
	}

	@GetMapping("/projects/{key}/issues/new")
	String newForm(Model model) {
		model.addAttribute("form", new IssueForm());
		model.addAttribute("users", identity.listUsers());
		return FORM_VIEW;
	}

	@PostMapping("/projects/{key}/issues")
	Object create(@ModelAttribute("project") ProjectRef project, @Valid @ModelAttribute("form") IssueForm form,
			BindingResult errors, Model model) {
		model.addAttribute("users", identity.listUsers());
		OptionalLong assignee = parseAssignee(form.getAssigneeId(), errors);
		if (errors.hasErrors()) {
			return ErrorViews.unprocessable(FORM_VIEW);
		}
		long reporter = identity.currentUser().orElseThrow().id();
		IssueDetail created = issues.create(project.id(), form.getTitle(), form.getDescription(),
				IssueType.valueOf(form.getType()), IssuePriority.valueOf(form.getPriority()), assignee, reporter);
		return "redirect:/projects/" + project.key() + "/issues/" + created.number();
	}

	@GetMapping("/projects/{key}/issues/{number}")
	String detail(@ModelAttribute("project") ProjectRef project, @PathVariable int number, Model model) {
		IssueDetail issue = issues.find(project.id(), number)
				.orElseThrow(() -> new NotFoundException("Issue " + project.key() + "-" + number));
		model.addAttribute("issue", toView(project, issue, userIndex()));
		return "issues/detail";
	}

	/** Blank means unassigned; anything that is not an existing user id is a validation error (ISS-4). */
	private OptionalLong parseAssignee(String raw, BindingResult errors) {
		if (raw == null || raw.isBlank()) {
			return OptionalLong.empty();
		}
		try {
			long id = Long.parseLong(raw.trim());
			if (identity.findUser(id).isPresent()) {
				return OptionalLong.of(id);
			}
		} catch (NumberFormatException e) {
			// falls through to the error below
		}
		errors.rejectValue("assigneeId", "assignee.unknown", "Unknown assignee");
		return OptionalLong.empty();
	}

	private Map<Long, UserRef> userIndex() {
		return identity.listUsers().stream().collect(Collectors.toMap(UserRef::id, Function.identity()));
	}

	private static IssueView toView(ProjectRef project, IssueDetail i, Map<Long, UserRef> users) {
		String assignee = i.assigneeId().isPresent() ? name(users, i.assigneeId().getAsLong()) : "Unassigned";
		return new IssueView(project.key() + "-" + i.number(), i.number(), i.title(), i.description(),
				i.type().name(), i.priority().name(), i.status().name(), assignee, name(users, i.reporterId()),
				TIMESTAMP.format(i.createdAt()), TIMESTAMP.format(i.updatedAt()));
	}

	private static String name(Map<Long, UserRef> users, long id) {
		UserRef user = users.get(id);
		return user == null ? "Unknown user" : user.displayName();
	}
}
