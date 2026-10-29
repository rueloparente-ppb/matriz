package com.jiraclone.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiraclone.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

class IdentityTests extends AbstractIntegrationTest {

	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	UserDetailsService userDetailsService;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	IdentityApi identityApi;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void usernamesDifferingOnlyByCaseViolateUniqueIndex() {
		jdbc.update("INSERT INTO app_user (username, display_name, password_hash) VALUES ('dave', 'Dave', 'x')");
		try {
			assertThatThrownBy(() -> jdbc.update(
					"INSERT INTO app_user (username, display_name, password_hash) VALUES ('DAVE', 'Dave 2', 'x')"))
					.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			jdbc.update("DELETE FROM app_user WHERE lower(username) = 'dave'");
		}
	}

	@Test
	void seededPasswordsAreBcryptHashesNotPlainText() {
		List<String> hashes = jdbc.queryForList("SELECT password_hash FROM app_user", String.class);

		assertThat(hashes).hasSizeGreaterThanOrEqualTo(3).allSatisfy(hash -> {
			assertThat(hash).isNotEqualTo("password");
			assertThat(passwordEncoder.matches("password", hash)).isTrue();
		});
	}

	@Test
	void lookupIsCaseInsensitive() {
		assertThat(userDetailsService.loadUserByUsername("ALICE").getUsername()).isEqualTo("alice");
	}

	@Test
	void currentUserReturnsUserRef() {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", List.of()));

		UserRef ref = identityApi.currentUser().orElseThrow();

		assertThat(ref.username()).isEqualTo("alice");
		assertThat(ref.displayName()).isEqualTo("Alice Anderson");
		assertThat(identityApi.findUser(ref.id())).contains(ref);
	}

	@Test
	void currentUserIsEmptyWhenNotLoggedIn() {
		assertThat(identityApi.currentUser()).isEmpty();
	}

	@Test
	void listUsersReturnsSeededUsers() {
		assertThat(identityApi.listUsers()).extracting(UserRef::username).contains("alice", "bob", "carol");
	}
}
