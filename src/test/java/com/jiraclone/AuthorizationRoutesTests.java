package com.jiraclone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AuthorizationRoutesTests extends AbstractIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	record Route(String method, String path) {
		@Override public String toString() { return method + " " + path; }
	}

	static Stream<Route> routes() {
		return Stream.of(
				new Route("GET", "/"),
				new Route("GET", "/projects"),
				new Route("GET", "/projects/new"),
				new Route("POST", "/projects"),
				new Route("GET", "/projects/{key}"),
				new Route("GET", "/projects/{key}/edit"),
				new Route("POST", "/projects/{key}"),
				new Route("GET", "/projects/{key}/delete"),
				new Route("POST", "/projects/{key}/delete"),
				new Route("GET", "/projects/{key}/issues"),
				new Route("GET", "/projects/{key}/issues/new"),
				new Route("POST", "/projects/{key}/issues"),
				new Route("GET", "/projects/{key}/issues/{number}"),
				new Route("GET", "/projects/{key}/issues/{number}/delete"),
				new Route("POST", "/projects/{key}/issues/{number}/delete"),
				new Route("GET", "/projects/{key}/issues/{number}/{field}"),
				new Route("GET", "/projects/{key}/issues/{number}/{field}/edit"),
				new Route("POST", "/projects/{key}/issues/{number}/{field}"),
				new Route("GET", "/projects/{key}/issues/{number}/assignees"),
				new Route("POST", "/projects/{key}/issues/{number}/assignees"),
				new Route("POST", "/projects/{key}/issues/{number}/assignees/{userId}/delete"),
				new Route("GET", "/projects/{key}/issues/{number}/comments"),
				new Route("POST", "/projects/{key}/issues/{number}/comments"),
				new Route("GET", "/projects/{key}/issues/{number}/history"),
				new Route("GET", "/projects/{key}/issues/{number}/sprint"),
				new Route("POST", "/projects/{key}/issues/{number}/sprint"),
				new Route("GET", "/projects/{key}/board"),
				new Route("POST", "/projects/{key}/board/move"),
				new Route("GET", "/projects/{key}/sprints"),
				new Route("POST", "/projects/{key}/sprints"),
				new Route("POST", "/projects/{key}/sprints/{id}/start"),
				new Route("POST", "/projects/{key}/sprints/{id}/complete"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("routes")
	void projectRoutesRequireLoginAndHideInaccessibleProjects(Route route) throws Exception {
		StringBuilder keyBuilder = new StringBuilder("R");
		for (int i = 0; i < 7; i++) keyBuilder.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
		String key = keyBuilder.toString();
		mvc.perform(post("/projects").with(user("alice")).with(csrf()).param("key", key).param("name", "Private project"))
				.andExpect(status().isFound());
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf()).param("title", "Original"))
				.andExpect(status().isFound());
		long projectId = jdbc.queryForObject("select id from project where key=?", Long.class, key);
		long sprintId = jdbc.queryForObject("insert into sprint(project_id,name,state) values (?,'Iteration','PLANNED') returning id",
				Long.class, projectId);
		long bobId = jdbc.queryForObject("select id from app_user where username='bob'", Long.class);
		String path = route.path().replace("{key}", key).replace("{number}", "1")
				.replace("{field}", "title").replace("{userId}", String.valueOf(bobId))
				.replace("{id}", String.valueOf(sprintId));
		for (boolean hx : List.of(false, true)) {
			MockHttpServletRequestBuilder anonymous = request(route.method(), path);
			if (route.method().equals("POST")) anonymous.with(csrf());
			if (hx) {
				mvc.perform(anonymous.header("HX-Request", "true"))
						.andExpect(status().isUnauthorized()).andExpect(header().string("HX-Redirect", "/login"));
			} else {
				mvc.perform(anonymous).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
			}
		}
		if (!route.path().contains("{key}")) return; // Dashboard, list and creation are not scoped to a project.
		List<Integer> before = counts();
		for (boolean hx : List.of(false, true)) {
			MockHttpServletRequestBuilder outsider = request(route.method(), path).with(user("bob"))
					.param("name", "Changed project").param("title", "Changed issue")
					.param("body", "Unauthorized comment").param("value", "Changed issue")
					.param("userId", String.valueOf(bobId)).param("issue", "1")
					.param("status", "DONE").param("sprint", String.valueOf(sprintId));
			if (route.method().equals("POST")) outsider.with(csrf());
			if (hx) outsider.header("HX-Request", "true");
			mvc.perform(outsider).andExpect(status().isNotFound());
			assertThat(counts()).as("no rows changed by %s", route).isEqualTo(before);
			assertThat(jdbc.queryForObject("select name from project where id=?", String.class, projectId))
					.isEqualTo("Private project");
			assertThat(jdbc.queryForObject("select title || ':' || status from issue where project_id=?", String.class, projectId))
					.isEqualTo("Original:TO_DO");
			assertThat(jdbc.queryForObject("select state from sprint where id=?", String.class, sprintId)).isEqualTo("PLANNED");
		}
	}

	private MockHttpServletRequestBuilder request(String method, String path) {
		return method.equals("GET") ? get(path) : post(path);
	}

	private List<Integer> counts() {
		return Stream.of("project", "issue_counter", "issue", "issue_assignee", "comment", "issue_history",
				"sprint", "sprint_issue").map(table -> jdbc.queryForObject("select count(*) from " + table, Integer.class)).toList();
	}
}
