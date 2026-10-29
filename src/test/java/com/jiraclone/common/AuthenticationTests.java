package com.jiraclone.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jiraclone.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthenticationTests extends AbstractIntegrationTest {

	@Autowired
	MockMvc mvc;

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = mvc.perform(post("/login").with(csrf()).param("username", username).param("password", password))
				.andExpect(status().isFound())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	@Test
	void auth1_validLoginRedirectsToSavedPage() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(get("/projects").session(session)).andExpect(status().isFound());

		mvc.perform(post("/login").with(csrf()).session(session).param("username", "alice").param("password", "password"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("http://localhost/projects?continue"));
	}

	@Test
	void auth1_validLoginWithoutSavedPageRedirectsToRoot() throws Exception {
		mvc.perform(post("/login").with(csrf()).param("username", "alice").param("password", "password"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void auth2_wrongPasswordAndUnknownUserAreIndistinguishable() throws Exception {
		MvcResult wrongPassword = mvc.perform(post("/login").with(csrf()).param("username", "alice").param("password", "nope"))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/login?error")).andReturn();
		MvcResult unknownUser = mvc.perform(post("/login").with(csrf()).param("username", "nobody").param("password", "nope"))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/login?error")).andReturn();

		for (MvcResult failed : new MvcResult[] {wrongPassword, unknownUser}) {
			mvc.perform(get("/projects").session((MockHttpSession) failed.getRequest().getSession()))
					.andExpect(status().isFound())
					.andExpect(redirectedUrl("/login"));
		}
		assertThat(wrongPassword.getResponse().getContentAsString()).isEqualTo(unknownUser.getResponse().getContentAsString());

		mvc.perform(get("/login").param("error"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid username or password.")));
	}

	@Test
	void auth3_unauthenticatedBrowserRequestRedirectsToLogin() throws Exception {
		mvc.perform(get("/projects"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"))
				.andExpect(content().string(""));
	}

	@Test
	void auth4_unauthenticatedHtmxRequestGets401WithHxRedirect() throws Exception {
		mvc.perform(get("/projects/fragment").header("HX-Request", "true"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("HX-Redirect", "/login"))
				.andExpect(content().string(""));
	}

	@Test
	void auth5_logoutInvalidatesSession() throws Exception {
		MockHttpSession session = login("alice", "password");
		// authenticated: the request passes security (404 as /projects does not exist yet, not a redirect to login)
		mvc.perform(get("/projects").session(session)).andExpect(status().isNotFound());

		mvc.perform(post("/logout").with(csrf()).session(session))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));

		mvc.perform(get("/projects").session(session))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void auth6_postWithoutCsrfTokenIsForbiddenAndKeepsSession() throws Exception {
		MockHttpSession session = login("alice", "password");

		mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
		mvc.perform(post("/login").param("username", "alice").param("password", "password"))
				.andExpect(status().isForbidden());

		mvc.perform(get("/projects").session(session)).andExpect(status().isNotFound());
	}

	@Test
	void auth7_seededUserCanLogInOnFreshContext() throws Exception {
		// Users come from Flyway migrations, so a fresh context/database can authenticate them.
		mvc.perform(post("/login").with(csrf()).param("username", "bob").param("password", "password"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void auth8_usernameMatchesCaseInsensitively() throws Exception {
		mvc.perform(post("/login").with(csrf()).param("username", "ALICE").param("password", "password"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void rootRedirectsToProjects() throws Exception {
		MockHttpSession session = login("alice", "password");

		mvc.perform(get("/").session(session))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/projects"));
	}

	@Test
	void loginPageIsPublicAndCarriesCsrfAndHtmxHeaders() throws Exception {
		mvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("hx-headers=")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("X-CSRF-TOKEN")));
	}

	@Test
	void navbarShowsDisplayNameAndLogoutWhenLoggedIn() throws Exception {
		MockHttpSession session = login("alice", "password");
		// /error is permitted; any rendered page will do, use the login page
		mvc.perform(get("/login").session(session))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Alice Anderson")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/logout\"")));
	}
}
