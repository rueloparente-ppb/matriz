package com.jiraclone.issues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jiraclone.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class IssuesTests extends AbstractIntegrationTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JdbcTemplate jdbc;

	private String newProject() throws Exception {
		var sb = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			sb.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
		}
		String key = sb.toString();
		mvc.perform(post("/projects").with(user("alice")).with(csrf()).param("key", key).param("name", "P"))
				.andExpect(status().isFound());
		return key;
	}

	private MockHttpServletRequestBuilder createRequest(String as, String key, String title) {
		return post("/projects/" + key + "/issues").with(user(as)).with(csrf()).param("title", title);
	}

	private ResultActions create(String as, String key, String title) throws Exception {
		return mvc.perform(createRequest(as, key, title));
	}

	private int issueCount(String key) {
		return jdbc.queryForObject(
				"select count(*) from issue i join project p on p.id = i.project_id where p.key = ?", Integer.class, key);
	}

	private long userId(String username) {
		return jdbc.queryForObject("select id from app_user where lower(username) = ?", Long.class, username);
	}

	@Test
	void iss1_iss2_validCreateRedirectsAndAppliesDefaults() throws Exception {
		String key = newProject();
		create("alice", key, "  First  ").andExpect(status().isFound())
				.andExpect(redirectedUrl("/projects/" + key + "/issues/1"));
		create("bob", key, "Second").andExpect(redirectedUrl("/projects/" + key + "/issues/2"));

		var row = jdbc.queryForMap("""
				select i.title, i.description, i.type, i.priority, i.status, i.assignee_id, i.reporter_id
				from issue i join project p on p.id = i.project_id where p.key = ? and i.number = 1
				""", key);
		assertThat(row).containsEntry("title", "First").containsEntry("type", "TASK")
				.containsEntry("priority", "MEDIUM").containsEntry("status", "TO_DO")
				.containsEntry("assignee_id", null).containsEntry("description", null)
				.containsEntry("reporter_id", userId("alice"));
	}

	@Test
	void iss1_explicitFieldsAreStored() throws Exception {
		String key = newProject();
		mvc.perform(createRequest("alice", key, "T").param("description", "D").param("type", "BUG")
				.param("priority", "HIGH").param("assigneeId", String.valueOf(userId("bob"))))
				.andExpect(status().isFound());
		var row = jdbc.queryForMap("select type, priority, assignee_id, description from issue i "
				+ "join project p on p.id = i.project_id where p.key = ?", key);
		assertThat(row).containsEntry("type", "BUG").containsEntry("priority", "HIGH")
				.containsEntry("assignee_id", userId("bob")).containsEntry("description", "D");
	}

	@Test
	void iss3_invalidTitleReturns422CreatesNothingAndConsumesNoNumber() throws Exception {
		String key = newProject();
		create("alice", key, "   ").andExpect(status().isUnprocessableEntity())
				.andExpect(content().string(containsString("Title is required")));
		create("alice", key, "x".repeat(201)).andExpect(status().isUnprocessableEntity())
				.andExpect(content().string(containsString("at most 200")));
		assertThat(issueCount(key)).isZero();

		create("alice", key, "x".repeat(200)).andExpect(redirectedUrl("/projects/" + key + "/issues/1"));
	}

	@Test
	void iss4_invalidFieldsReturn422AndCreateNothing() throws Exception {
		String key = newProject();
		create("alice", key, "T").andExpect(status().isFound());
		var bad = List.of(
				createRequest("alice", key, "T").param("description", "x".repeat(5001)),
				createRequest("alice", key, "T").param("type", "EPIC"),
				createRequest("alice", key, "T").param("priority", "URGENT"),
				createRequest("alice", key, "T").param("assigneeId", "999999999"),
				createRequest("alice", key, "T").param("assigneeId", "abc"));
		for (var request : bad) {
			mvc.perform(request).andExpect(status().isUnprocessableEntity())
					.andExpect(content().string(containsString("value=\"T\"")));
		}
		assertThat(issueCount(key)).isEqualTo(1);
		create("alice", key, "Next").andExpect(redirectedUrl("/projects/" + key + "/issues/2"));
	}

	@Test
	void iss5_parallelCreatesGetDistinctConsecutiveNumbers() throws Exception {
		String key = newProject();
		int n = 12;
		var start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(n);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				String title = "Racer " + i;
				results.add(pool.submit(() -> {
					start.await();
					return create("alice", key, title).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (var f : results) {
				assertThat(f.get()).isEqualTo(302);
			}
		} finally {
			pool.shutdownNow();
		}
		assertThat(jdbc.queryForList("select i.number from issue i join project p on p.id = i.project_id "
				+ "where p.key = ? order by i.number", Integer.class, key))
				.containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
	}

	@Test
	void iss6_numberingIsIndependentPerProject() throws Exception {
		String a = newProject();
		String b = newProject();
		create("alice", a, "A1").andExpect(redirectedUrl("/projects/" + a + "/issues/1"));
		create("alice", a, "A2").andExpect(redirectedUrl("/projects/" + a + "/issues/2"));
		create("alice", b, "B1").andExpect(redirectedUrl("/projects/" + b + "/issues/1"));
	}

	@Test
	void iss7_detailShowsAllFields() throws Exception {
		String key = newProject();
		mvc.perform(createRequest("alice", key, "Fix login").param("description", "It breaks").param("type", "BUG")
				.param("priority", "HIGH").param("assigneeId", String.valueOf(userId("bob"))))
				.andExpect(status().isFound());

		String html = mvc.perform(get("/projects/" + key + "/issues/1").with(user("carol")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(html).contains(key + "-1", "Fix login", "It breaks", "BUG", "HIGH", "TO_DO", "Bob", "Alice");
		assertThat(html).containsPattern("Created</dt><dd[^>]*>\\d{4}-\\d{2}-\\d{2}");
		assertThat(html).containsPattern("Updated</dt><dd[^>]*>\\d{4}-\\d{2}-\\d{2}");
	}

	@Test
	void iss8_unknownProjectOrNumberReturns404AndNeverCrossesProjects() throws Exception {
		String a = newProject();
		String b = newProject();
		create("alice", a, "Only in A").andExpect(status().isFound());

		mvc.perform(get("/projects/NOPE/issues/1").with(user("alice"))).andExpect(status().isNotFound());
		mvc.perform(get("/projects/" + a + "/issues/99").with(user("alice"))).andExpect(status().isNotFound());
		mvc.perform(get("/projects/" + b + "/issues/1").with(user("alice"))).andExpect(status().isNotFound());
		// unknown project wins over validation
		mvc.perform(createRequest("alice", "NOPE", "")).andExpect(status().isNotFound());
		mvc.perform(get("/projects/NOPE/issues").with(user("alice"))).andExpect(status().isNotFound());
		mvc.perform(get("/projects/NOPE/issues/new").with(user("alice"))).andExpect(status().isNotFound());
	}

	@Test
	void iss20_htmlInTitleAndDescriptionIsEscaped() throws Exception {
		String key = newProject();
		mvc.perform(createRequest("alice", key, "<script>alert(1)</script>")
				.param("description", "<img src=x onerror=alert(2)>")).andExpect(status().isFound());

		for (String path : List.of("/projects/" + key + "/issues/1", "/projects/" + key + "/issues")) {
			mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk())
					.andExpect(content().string(not(containsString("<script>alert(1)"))))
					.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
		}
		mvc.perform(get("/projects/" + key + "/issues/1").with(user("alice")))
				.andExpect(content().string(not(containsString("<img src=x"))))
				.andExpect(content().string(containsString("&lt;img src=x")));
	}

	@Test
	void listAndFormRender() throws Exception {
		String key = newProject();
		create("alice", key, "Listed").andExpect(status().isFound());
		mvc.perform(get("/projects/" + key + "/issues").with(user("alice"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Listed")))
				.andExpect(content().string(containsString("New issue")));
		mvc.perform(get("/projects/" + key + "/issues/new").with(user("alice"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"title\"")))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("Bob")));
	}

	@Test
	void iss21_unauthenticatedRequestsDiscloseNothing() throws Exception {
		String key = newProject();
		create("alice", key, "Secret").andExpect(status().isFound());
		String base = "/projects/" + key + "/issues";
		for (var request : List.of(get(base), get(base + "/new"), get(base + "/1"),
				post(base).with(csrf()).param("title", "x"))) {
			mvc.perform(request).andExpect(status().isFound()).andExpect(redirectedUrl("/login"))
					.andExpect(content().string(""));
			mvc.perform(request.header("HX-Request", "true")).andExpect(status().isUnauthorized())
					.andExpect(header().string("HX-Redirect", "/login")).andExpect(content().string(""));
		}
		assertThat(issueCount(key)).isEqualTo(1);
	}

	private ResultActions list(String key, String... params) throws Exception {
		var request = get("/projects/" + key + "/issues").with(user("alice"));
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return mvc.perform(request);
	}

	@Test
	void iss9_listIsScopedToProjectAndNewestFirst() throws Exception {
		String key = newProject();
		String other = newProject();
		create("alice", key, "Older").andExpect(status().isFound());
		create("alice", key, "Newer").andExpect(status().isFound());
		create("alice", other, "Foreign").andExpect(status().isFound());
		list(key).andExpect(status().isOk()).andExpect(content().string(not(containsString("Foreign"))))
				.andExpect(content().string(matchesPattern("(?s).*Newer.*Older.*")));
	}

	@Test
	void iss10_filtersCombineWithAnd() throws Exception {
		String key = newProject();
		long bob = userId("bob");
		mvc.perform(createRequest("alice", key, "Bug for bob").param("type", "BUG").param("assigneeId", "" + bob));
		mvc.perform(createRequest("alice", key, "Bug nobody").param("type", "BUG"));
		mvc.perform(createRequest("alice", key, "Task for bob").param("assigneeId", "" + bob));
		jdbc.update("update issue set status = 'DONE' where title = 'Task for bob'");

		list(key, "type", "BUG").andExpect(content().string(containsString("Bug for bob")))
				.andExpect(content().string(containsString("Bug nobody")))
				.andExpect(content().string(not(containsString("Task for bob"))));
		list(key, "assignee", "none").andExpect(content().string(containsString("Bug nobody")))
				.andExpect(content().string(not(containsString("Bug for bob"))));
		list(key, "type", "BUG", "assignee", "" + bob).andExpect(content().string(containsString("Bug for bob")))
				.andExpect(content().string(not(containsString("Bug nobody"))))
				.andExpect(content().string(not(containsString("Task for bob"))));
		list(key, "status", "DONE", "type", "BUG").andExpect(content().string(not(containsString("Bug for bob"))))
				.andExpect(content().string(containsString("No issues found")));
		list(key, "status", "DONE").andExpect(content().string(containsString("Task for bob")))
				.andExpect(content().string(not(containsString("Bug for bob"))));
	}

	@Test
	void iss11_searchMatchesTitleOrKeyCaseInsensitivelyAndCombinesWithFilters() throws Exception {
		String key = newProject();
		create("alice", key, "Login Page broken").andExpect(status().isFound());
		mvc.perform(createRequest("alice", key, "Other thing").param("type", "BUG"));
		list(key, "q", "login pAGE").andExpect(content().string(containsString("Login Page broken")))
				.andExpect(content().string(not(containsString("Other thing"))));
		list(key, "q", key.toLowerCase() + "-2").andExpect(content().string(containsString("Other thing")))
				.andExpect(content().string(not(containsString("Login Page broken"))));
		list(key, "q", "thing", "type", "TASK").andExpect(content().string(not(containsString("Other thing"))));
		list(key, "q", "%").andExpect(content().string(containsString("No issues found")));
	}

	@Test
	void iss12_invalidParametersReturn422WithoutAList() throws Exception {
		String key = newProject();
		create("alice", key, "Visible").andExpect(status().isFound());
		for (String[] bad : new String[][] { { "status", "NOPE" }, { "type", "NOPE" }, { "assignee", "abc" },
				{ "assignee", "999999" }, { "page", "0" }, { "page", "-1" }, { "page", "x" } }) {
			list(key, bad).andExpect(status().isUnprocessableEntity())
					.andExpect(content().string(not(containsString("Visible"))));
		}
	}

	@Test
	void iss13_pagingIs25PerPageAndOutOfRangeIsEmpty() throws Exception {
		String key = newProject();
		for (int i = 1; i <= 26; i++) {
			create("alice", key, "Item" + i + "x").andExpect(status().isFound());
		}
		list(key).andExpect(content().string(containsString("Item26x")))
				.andExpect(content().string(not(containsString("Item1x"))));
		list(key, "page", "2").andExpect(status().isOk()).andExpect(content().string(containsString("Item1x")))
				.andExpect(content().string(not(containsString("Item26x"))));
		list(key, "page", "3").andExpect(status().isOk()).andExpect(content().string(containsString("No issues found")));
	}

	@Test
	void iss14_htmxGetsOnlyTheFragment() throws Exception {
		String key = newProject();
		create("alice", key, "Frag").andExpect(status().isFound());
		var path = "/projects/" + key + "/issues";
		mvc.perform(get(path).with(user("alice")).header("HX-Request", "true")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Frag"))).andExpect(content().string(not(containsString("<html"))));
		mvc.perform(get(path).with(user("alice")).header("HX-Request", "true").param("status", "NOPE"))
				.andExpect(status().isUnprocessableEntity()).andExpect(content().string(not(containsString("<html"))))
				.andExpect(content().string(containsString("Unknown status")));
		mvc.perform(get(path).with(user("alice")).header("HX-Request", "true").header("HX-History-Restore-Request", "true"))
				.andExpect(content().string(containsString("<html")));
		list(key).andExpect(content().string(containsString("<html")));
	}
}
