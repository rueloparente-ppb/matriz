package com.jiraclone.identity.web;

import com.jiraclone.identity.IdentityApi;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes the logged-in user to every view as {@code currentUser} (used by the navbar). */
@ControllerAdvice
class CurrentUserAdvice {

	private final IdentityApi identity;

	CurrentUserAdvice(IdentityApi identity) {
		this.identity = identity;
	}

	@ModelAttribute
	void currentUser(Model model) {
		identity.currentUser().ifPresent(user -> model.addAttribute("currentUser", user));
	}
}
