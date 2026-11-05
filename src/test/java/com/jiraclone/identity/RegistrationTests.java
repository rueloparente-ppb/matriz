package com.jiraclone.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.jiraclone.AbstractIntegrationTest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindingResult;

class RegistrationTests extends AbstractIntegrationTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	UserDetailsService userDetailsService;

	@AfterEach
	void removeRegisteredUsers() {
		jdbc.update("delete from app_user where username not in ('alice', 'bob', 'carol')");
	}

	private MvcResult register(String username, String password, String confirmation) throws Exception {
		return mvc.perform(post("/register").with(csrf())
				.param("username", username).param("password", password).param("confirmPassword", confirmation))
				.andReturn();
	}

	private int count(String username) {
		return jdbc.queryForObject("select count(*) from app_user where lower(username) = lower(?)", Integer.class, username);
	}

	private String rejected(String username, String password, String confirmation) throws Exception {
		int before = count(username);
		MvcResult result = register(username, password, confirmation);
		assertThat(result.getResponse().getStatus()).isEqualTo(422);
		String html = result.getResponse().getContentAsString();
		if (!username.isBlank()) {
			assertThat(html).contains("value=\"" + username + "\"");
		}
		assertThat(html).doesNotContain(password, confirmation);
		assertThat(count(username)).isEqualTo(before);
		mvc.perform(get("/projects").session((MockHttpSession) result.getRequest().getSession()))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
		return html;
	}

	@Test
	void auth10_publicRegistrationPageHasOnlyUsernameAndTwoPasswordInputs() throws Exception {
		String html = mvc.perform(get("/register"))
				.andExpect(status().isOk()).andExpect(view().name("identity/register"))
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("name=\"username\"", "name=\"password\"", "name=\"confirmPassword\"")
				.doesNotContain("name=\"email\"");
	}

	@Test
	void auth11_registrationPersistsDisplayNameAndSignsUserIn() throws Exception {
		MvcResult result = register("new.user-1", "secretpass11", "secretpass11");
		assertThat(result.getResponse().getStatus()).isEqualTo(302);
		assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/");
		assertThat(count("new.user-1")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select display_name from app_user where username = ?", String.class, "new.user-1"))
				.isEqualTo("new.user-1");
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		mvc.perform(get("/projects").session(session)).andExpect(status().isOk());
	}

	@Test
	void auth12_storedBcryptHashAuthenticatesThroughFreshLookupAndLogin() throws Exception {
		register("fresh.user", "  secretpass12  ", "  secretpass12  ");
		String hash = jdbc.queryForObject("select password_hash from app_user where username = ?", String.class, "fresh.user");
		assertThat(hash).startsWith("$2").isNotEqualTo("  secretpass12  ");
		assertThat(passwordEncoder.matches("  secretpass12  ", hash)).isTrue();
		assertThat(userDetailsService.loadUserByUsername("fresh.user").getPassword()).isEqualTo(hash);
		mvc.perform(post("/login").with(csrf()).param("username", "fresh.user")
					.param("password", "  secretpass12  "))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/"));
	}

	@Test
	void auth13_invalidUsernamesShowErrorsWithoutCreatingAnAccount() throws Exception {
		for (String username : List.of("ab", "x".repeat(51), "bad!name")) {
			rejected(username, "secretpass13", "secretpass13");
			assertThat(count(username)).isZero();
		}
	}

	@Test
	void auth14_invalidPasswordLengthsShowErrorsWithoutEchoingEitherPassword() throws Exception {
		for (String password : List.of("short14", "x".repeat(73))) {
			rejected("invalid.pass14", password, password);
		}
	}

	@Test
	void auth15_mismatchedConfirmationShowsItsOwnError() throws Exception {
		assertThat(rejected("mismatch15", "secretpass15", "differentpass15"))
				.contains("Passwords do not match");
	}

	@Test
	void auth16_caseInsensitiveDuplicateLeavesSeededUserAndPasswordUnchanged() throws Exception {
		String oldHash = jdbc.queryForObject("select password_hash from app_user where username = 'alice'", String.class);
		assertThat(rejected("ALICE", "secretpass16", "secretpass16")).contains("Username already taken");
		assertThat(count("alice")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select password_hash from app_user where username = 'alice'", String.class))
				.isEqualTo(oldHash);
	}

	@Test
	void auth17_concurrentCaseVariantsYieldOneSuccessAndOneValidationFailure() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> first = pool.submit(() -> {
				start.await();
				return register("race.user17", "secretpass17", "secretpass17").getResponse().getStatus();
			});
			Future<Integer> second = pool.submit(() -> {
				start.await();
				return register("RACE.USER17", "otherpass17", "otherpass17").getResponse().getStatus();
			});
			start.countDown();
			assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(302, 422);
			assertThat(count("race.user17")).isEqualTo(1);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void auth18_registrationWithoutCsrfIsForbiddenAndCreatesNothing() throws Exception {
		mvc.perform(post("/register").param("username", "no.csrf18")
					.param("password", "secretpass18").param("confirmPassword", "secretpass18"))
				.andExpect(status().isForbidden());
		assertThat(count("no.csrf18")).isZero();
	}

	@Test
	void auth19_registeredUserCanLogOutAndLogInWithDifferentUsernameCasing() throws Exception {
		MvcResult registered = register("mixedCase19", "secretpass19", "secretpass19");
		MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
		mvc.perform(post("/logout").with(csrf()).session(session))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/login?logout"));
		mvc.perform(get("/projects").session(session)).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
		MvcResult loggedIn = mvc.perform(post("/login").with(csrf())
					.param("username", "MIXEDCASE19").param("password", "secretpass19"))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/"))
				.andReturn();
		mvc.perform(get("/projects").session((MockHttpSession) loggedIn.getRequest().getSession(false)))
				.andExpect(status().isOk());
	}

	@Test
	void auth20_authenticatedVisitorIsRedirectedAwayFromBothGuestForms() throws Exception {
		MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").with(csrf())
					.param("username", "alice").param("password", "password"))
				.andExpect(status().isFound()).andReturn().getRequest().getSession(false);
		for (String path : List.of("/register", "/login")) {
			mvc.perform(get(path).session(session)).andExpect(status().isFound()).andExpect(redirectedUrl("/"));
		}
	}

	@Test
	void auth21_surroundingWhitespaceIsTrimmedButBlankUsernameIsRejected() throws Exception {
		MvcResult result = register("  trimmed21  ", "secretpass21", "secretpass21");
		assertThat(result.getResponse().getStatus()).isEqualTo(302);
		assertThat(count("trimmed21")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select username from app_user where lower(username) = ?", String.class, "trimmed21"))
				.isEqualTo("trimmed21");
		assertThat(jdbc.queryForObject("select display_name from app_user where username = ?", String.class, "trimmed21"))
				.isEqualTo("trimmed21");
		assertThat(rejected("   ", "anotherpass21", "anotherpass21")).contains("username");
	}

	@Test
	void auth13_auth14_auth15_simultaneousValidationErrorsAreBoundToEachField() throws Exception {
		MvcResult result = register("bad!", "short22", "different22");
		assertThat(result.getResponse().getStatus()).isEqualTo(422);
		BindingResult errors = (BindingResult) result.getModelAndView().getModel()
				.get(BindingResult.MODEL_KEY_PREFIX + "form");
		assertThat(errors.hasFieldErrors("username")).isTrue();
		assertThat(errors.hasFieldErrors("password")).isTrue();
		assertThat(errors.hasFieldErrors("confirmPassword")).isTrue();
		assertThat(result.getResponse().getContentAsString()).contains("Passwords do not match")
				.doesNotContain("short22", "different22");
		assertThat(count("bad!")).isZero();
		mvc.perform(get("/projects").session((MockHttpSession) result.getRequest().getSession()))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
	}
}
