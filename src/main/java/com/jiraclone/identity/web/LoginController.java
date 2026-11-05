package com.jiraclone.identity.web;

import com.jiraclone.identity.IdentityApi;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LoginController {
	private final IdentityApi identity;

	LoginController(IdentityApi identity) {
		this.identity = identity;
	}

	@GetMapping("/login")
	String login() {
		return identity.currentUser().isPresent() ? "redirect:/" : "identity/login";
	}
}
