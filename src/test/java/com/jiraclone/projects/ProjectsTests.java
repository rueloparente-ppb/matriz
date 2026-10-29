package com.jiraclone.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jiraclone.AbstractIntegrationTest;
import com.jiraclone.JiraCloneApplication;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class ProjectsTests extends AbstractIntegrationTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	DataSource dataSource;

	/** Random 6-letter key so tests sharing one database never collide. */
	private static String randomKey() {
		var sb = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			sb.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
		}
		return sb.toString();
	}

	private ResultActions create(String as, String key, String name) throws Exception {
		return mvc.perform(post("/projects").with(user(as)).with(csrf()).param("key", key).param("name", name));
	}

	private int count(String key) {
		return jdbc.queryForObject("select count(*) from project where key = ?", Integer.class, key);
	}

	@Test
	void prj1_validCreateRedirectsToIssueList() throws Exception {
		String key = randomKey();
		create("alice", key, "  Alpha  ").andExpect(status().isFound()).andExpect(redirectedUrl("/projects/" + key));

		assertThat(jdbc.queryForObject("select name from project where key = ?", String.class, key)).isEqualTo("Alpha");
		mvc.perform(get("/projects/" + key).with(user("alice")))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/projects/" + key + "/issues"));
	}

	@Test
	void prj2_listIsSortedByKeyAndShowsAllUsersProjects() throws Exception {
		String suffix = randomKey();
		String second = "ZZ" + suffix.substring(0, 4);
		String first = "AA" + suffix.substring(0, 4);
		create("alice", second, "Second").andExpect(status().isFound());
		create("bob", first, "First").andExpect(status().isFound());

		String html = mvc.perform(get("/projects").with(user("carol")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains(first, second, "First", "Second");
		assertThat(html.indexOf(">" + first + "<")).isLessThan(html.indexOf(">" + second + "<"));
	}

	@Test
	void prj3_invalidKeyReturns422PreservesInputAndCreatesNothing() throws Exception {
		for (String bad : List.of("abcd", "A", "ABCDEFGHIJK", "AB1", "ab", "A B", "")) {
			create("alice", bad, "Name")
					.andExpect(status().isUnprocessableEntity())
					.andExpect(content().string(containsString("value=\"" + bad + "\"")))
					.andExpect(content().string(containsString("value=\"Name\"")))
					.andExpect(content().string(containsString("Key must be 2 to 10 uppercase letters")
					)).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Key already in use"))));
		}
		assertThat(jdbc.queryForObject("select count(*) from project where key in ('abcd','A','AB1','ab')", Integer.class))
				.isZero();
	}

	@Test
	void prj4_invalidNameReturns422() throws Exception {
		String key = randomKey();
		create("alice", key, "   ").andExpect(status().isUnprocessableEntity())
				.andExpect(content().string(containsString("Name is required")));
		create("alice", key, "x".repeat(101)).andExpect(status().isUnprocessableEntity())
				.andExpect(content().string(containsString("at most 100")));
		assertThat(count(key)).isZero();

		create("alice", key, "x".repeat(100)).andExpect(status().isFound());
	}

	@Test
	void prj5_duplicateKeyReturns422AndLeavesExistingUnchanged() throws Exception {
		String key = randomKey();
		create("alice", key, "Original").andExpect(status().isFound());

		create("bob", key, "Other")
				.andExpect(status().isUnprocessableEntity())
				.andExpect(content().string(containsString("Key already in use")));
		assertThat(jdbc.queryForList("select name from project where key = ?", String.class, key)).containsExactly("Original");
	}

	@Test
	void prj6_parallelCreatesWithSameKeyYieldOneProjectAndOne422() throws Exception {
		for (int round = 0; round < 5; round++) {
			String key = randomKey();
			int threads = 2;
			var start = new CountDownLatch(1);
			ExecutorService pool = Executors.newFixedThreadPool(threads);
			try {
				List<Future<Integer>> results = new ArrayList<>();
				for (int i = 0; i < threads; i++) {
					String who = i == 0 ? "alice" : "bob";
					results.add(pool.submit(() -> {
						start.await();
						return create(who, key, "Racer").andReturn().getResponse().getStatus();
					}));
				}
				start.countDown();
				List<Integer> statuses = new ArrayList<>();
				for (var f : results) {
					statuses.add(f.get());
				}
				assertThat(statuses).containsExactlyInAnyOrder(302, 422);
				assertThat(count(key)).isEqualTo(1);
			} finally {
				pool.shutdownNow();
			}
		}
	}

	@Test
	void prj7_unknownKeyReturns404WithoutStackTrace() throws Exception {
		mvc.perform(get("/projects/NOPE").with(user("alice")))
				.andExpect(status().isNotFound())
				.andExpect(content().string(containsString("Not found")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Exception"))));

		mvc.perform(get("/projects/NOPE").with(user("alice")).header("HX-Request", "true"))
				.andExpect(status().isNotFound())
				.andExpect(content().string(containsString("Not found")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("<html"))));
	}

	@Test
	void prj8_unauthenticatedRequestsDiscloseNothing() throws Exception {
		create("alice", "SECRET", "Hidden"); // ensure data exists
		for (var request : List.of(get("/projects"), get("/projects/new"), get("/projects/SECRET"),
				post("/projects").with(csrf()).param("key", "ABC").param("name", "n"))) {
			mvc.perform(request).andExpect(status().isFound()).andExpect(redirectedUrl("/login"))
					.andExpect(content().string(""));
			mvc.perform(request.header("HX-Request", "true")).andExpect(status().isUnauthorized())
					.andExpect(header().string("HX-Redirect", "/login")).andExpect(content().string(""));
		}
	}

	@Test
	void newFormRenders() throws Exception {
		mvc.perform(get("/projects/new").with(user("alice")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"key\"")))
				.andExpect(content().string(containsString("name=\"_csrf\"")));
	}

	@Test
	void prj9_createdProjectsSurviveApplicationRestart() throws Exception {
		String key = randomKey();
		create("alice", key, "Durable").andExpect(status().isFound());

		HikariDataSource ds = dataSource.unwrap(HikariDataSource.class);
		try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(JiraCloneApplication.class)
				.run("--server.port=0",
						"--spring.datasource.url=" + ds.getJdbcUrl(),
						"--spring.datasource.username=" + ds.getUsername(),
						"--spring.datasource.password=" + ds.getPassword())) {
			assertThat(restarted.getBean(ProjectsApi.class).findByKey(key)).isPresent()
					.get().extracting(ProjectRef::name).isEqualTo("Durable");
		}
	}
}
