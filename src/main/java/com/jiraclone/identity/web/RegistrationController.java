package com.jiraclone.identity.web;

import com.jiraclone.common.ErrorViews;
import com.jiraclone.identity.IdentityApi;
import com.jiraclone.identity.core.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
class RegistrationController {

	private static final String FORM_VIEW = "identity/register";

	private final RegistrationService registration;
	private final IdentityApi identity;
	private final UserDetailsService userDetails;
	private final HttpSessionSecurityContextRepository securityContexts = new HttpSessionSecurityContextRepository();

	RegistrationController(RegistrationService registration, IdentityApi identity, UserDetailsService userDetails) {
		this.registration = registration;
		this.identity = identity;
		this.userDetails = userDetails;
	}

	@GetMapping("/register")
	String form(Model model) {
		if (identity.currentUser().isPresent()) {
			return "redirect:/";
		}
		model.addAttribute("form", new RegistrationForm());
		return FORM_VIEW;
	}

	@PostMapping("/register")
	Object register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult errors,
			HttpServletRequest request, HttpServletResponse response) {
		if (identity.currentUser().isPresent()) {
			return "redirect:/";
		}
		String username = form.getUsername() == null ? "" : form.getUsername().trim();
		if (username.isEmpty()) {
			errors.rejectValue("username", "username.required", "Username is required");
		} else {
			if (username.length() < 3 || username.length() > 50) {
				errors.rejectValue("username", "username.length", "Username must be 3 to 50 characters");
			}
			if (!username.matches("[A-Za-z0-9._-]+")) {
				errors.rejectValue("username", "username.characters",
						"Username may contain only letters, numbers, dots, underscores and hyphens");
			}
		}
		if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
			errors.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
		}
		if (form.getPassword() != null && form.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
			errors.rejectValue("password", "password.bytes", "Password must be at most 72 bytes");
		}
		if (username.matches("[A-Za-z0-9._-]{3,50}") && registration.isTaken(username)) {
			errors.rejectValue("username", "username.taken", "Username already taken");
		}
		if (errors.hasErrors()) {
			return ErrorViews.unprocessable(FORM_VIEW);
		}
		if (registration.register(username, form.getPassword()).isEmpty()) {
			errors.rejectValue("username", "username.taken", "Username already taken");
			return ErrorViews.unprocessable(FORM_VIEW);
		}

		UserDetails registered = userDetails.loadUserByUsername(username);
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				registered, null, registered.getAuthorities()));
		SecurityContextHolder.setContext(context);
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		securityContexts.saveContext(context, request, response);
		return "redirect:/";
	}
}
