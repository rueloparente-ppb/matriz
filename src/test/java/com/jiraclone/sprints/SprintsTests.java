package com.jiraclone.sprints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jiraclone.AbstractIntegrationTest;
import com.jiraclone.sprints.core.SprintService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class SprintsTests extends AbstractIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired SprintService sprints;

	private String project() throws Exception {
		StringBuilder key = new StringBuilder("SP");
		for (int i = 0; i < 6; i++) key.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
		mvc.perform(post("/projects").with(user("alice")).with(csrf()).param("key", key.toString()).param("name", "Sprints"))
				.andExpect(status().isFound());
		return key.toString();
	}

	private String url(String key) { return "/projects/" + key + "/sprints"; }

	private long id(String key, String name) {
		return jdbc.queryForObject("select s.id from sprint s join project p on p.id=s.project_id where p.key=? and s.name=?",
				Long.class, key, name);
	}

	private String state(long id) { return jdbc.queryForObject("select state from sprint where id=?", String.class, id); }

	private void create(String key, String name) throws Exception {
		mvc.perform(post(url(key)).with(user("alice")).with(csrf()).param("name", name))
				.andExpect(status().isFound()).andExpect(redirectedUrl(url(key)));
	}

	private int action(String key, long id, String action) throws Exception {
		return mvc.perform(post(url(key) + "/" + id + "/" + action).with(user("bob")).with(csrf()))
				.andReturn().getResponse().getStatus();
	}

	@Test
	void spr1_spr2_spr17_createValidateAndList() throws Exception {
		String key = project();
		String other = project();
		create(key, "  Alpha  ");
		create(other, "alpha");
		long id = id(key, "Alpha");
		assertThat(state(id)).isEqualTo("PLANNED");
		for (String name : List.of("  ", "x".repeat(101), "ALPHA")) {
			mvc.perform(post(url(key)).with(user("alice")).with(csrf()).param("name", name))
					.andExpect(status().isUnprocessableEntity());
		}
		assertThat(jdbc.queryForObject("select count(*) from sprint where project_id=(select id from project where key=?)",
				Integer.class, key)).isEqualTo(1);
		String html = mvc.perform(get(url(key)).with(user("alice"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("Alpha", "PLANNED", "<td>0</td>", "name=\"_csrf\"")
				.doesNotContain("alpha", "<td>1</td>");
		mvc.perform(get(url(key)).with(user("alice")).header("HX-Request", "true"))
				.andExpect(status().isOk()).andExpect(result -> assertThat(result.getResponse().getContentAsString())
						.contains("id=\"sprints\"").doesNotContain("<html"));
		mvc.perform(get(url(key)).with(user("alice")).header("HX-Request", "true")
				.header("HX-History-Restore-Request", "true")).andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("<html"));
		mvc.perform(get(url("NOPE")).with(user("alice"))).andExpect(status().isNotFound());
		mvc.perform(get("/projects/" + key + "/issues").with(user("alice")))
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains(url(key)));
	}

	@Test
	void spr3_spr4_spr6_transitionsAndProjectScope() throws Exception {
		String key = project(), other = project();
		create(key, "First"); create(key, "Second"); create(other, "Foreign");
		long first = id(key, "First"), second = id(key, "Second"), foreign = id(other, "Foreign");
		assertThat(action(key, first, "complete")).isEqualTo(409);
		assertThat(action(key, foreign, "start")).isEqualTo(404);
		assertThat(action(key, foreign, "complete")).isEqualTo(404);
		assertThat(action(key, 999999999L, "start")).isEqualTo(404);
		assertThat(action(key, first, "start")).isEqualTo(302);
		assertThat(action(key, second, "start")).isEqualTo(409);
		assertThat(action(key, first, "start")).isEqualTo(409);
		assertThat(state(second)).isEqualTo("PLANNED");
		assertThat(action(key, first, "complete")).isEqualTo(302);
		assertThat(action(key, first, "complete")).isEqualTo(409);
		assertThat(action(key, first, "start")).isEqualTo(409);
		assertThat(state(first)).isEqualTo("COMPLETED");
		assertThat(action(key, second, "start")).isEqualTo(302);
		assertThat(state(second)).isEqualTo("ACTIVE");
		mvc.perform(post(url(key) + "/" + second + "/complete").with(user("bob")).with(csrf())
				.header("HX-Request", "true")).andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("<html"));
	}

	@Test
	void spr5_parallelStartsYieldOneSuccessAndOneConflict() throws Exception {
		for (int round = 0; round < 4; round++) {
			String key = project();
			create(key, "One"); create(key, "Two");
			long one = id(key, "One"), two = id(key, "Two");
			CountDownLatch gate = new CountDownLatch(1);
			try (var pool = Executors.newFixedThreadPool(2)) {
				Future<Integer> a = pool.submit(() -> { gate.await(); return action(key, one, "start"); });
				Future<Integer> b = pool.submit(() -> { gate.await(); return action(key, two, "start"); });
				gate.countDown();
				assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(302, 409);
			}
			assertThat(jdbc.queryForObject("select count(*) from sprint where project_id=(select id from project where key=?) "
					+ "and state='ACTIVE'", Integer.class, key)).isEqualTo(1);
		}
	}

	@Test
	void databaseEnforcesBothSprintUniqueIndexes() throws Exception {
		String key = project();
		create(key, "One"); create(key, "Two");
		long one = id(key, "One"), two = id(key, "Two");
		long projectId = jdbc.queryForObject("select id from project where key=?", Long.class, key);
		assertThatThrownBy(() -> jdbc.update("insert into sprint(project_id,name,state) values(?, 'oNe', 'PLANNED')", projectId))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(action(key, one, "start")).isEqualTo(302);
		assertThatThrownBy(() -> jdbc.update("update sprint set state='ACTIVE' where id=?", two))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void spr7_spr8_completionMovesOnlyUnfinishedMembersWithAtomicHistory() throws Exception {
		String key = project();
		create(key, "Sprint A");
		long sprint = id(key, "Sprint A");
		for (String title : List.of("Todo", "Done")) {
			mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf())
					.param("title", title)).andExpect(status().isFound());
		}
		long todo = jdbc.queryForObject("select i.id from issue i join project p on p.id=i.project_id where p.key=? and i.number=1", Long.class, key);
		long done = jdbc.queryForObject("select i.id from issue i join project p on p.id=i.project_id where p.key=? and i.number=2", Long.class, key);
		jdbc.update("insert into sprint_issue(issue_id,sprint_id) values (?,?),(?,?)", todo, sprint, done, sprint);
		mvc.perform(post("/projects/" + key + "/issues/2/status").with(user("alice")).with(csrf())
				.param("value", "DONE")).andExpect(status().isOk());
		assertThat(mvc.perform(get(url(key)).with(user("alice"))).andReturn().getResponse().getContentAsString())
				.contains("<td>2</td>");
		assertThat(action(key, sprint, "start")).isEqualTo(302);
		assertThat(action(key, sprint, "complete")).isEqualTo(302);
		assertThat(jdbc.queryForList("select issue_id from sprint_issue where sprint_id=?", Long.class, sprint)).containsExactly(done);
		assertThat(jdbc.queryForList("select kind from issue_history where issue_id=? order by id", String.class, todo))
				.containsExactly("CREATED", "SPRINT_CHANGED");
		assertThat(jdbc.queryForList("select kind from issue_history where issue_id=? order by id", String.class, done))
				.containsExactly("CREATED", "FIELD_CHANGED");
		assertThat(jdbc.queryForObject("select old_value || ':' || new_value || ':' || actor_name from issue_history "
				+ "where issue_id=? and kind='SPRINT_CHANGED'", String.class, todo)).isEqualTo("Sprint A:None:Bob Brown");
		assertThat(jdbc.queryForObject("select i.updated_at > h.created_at from issue i join issue_history h "
				+ "on h.issue_id=i.id and h.kind='CREATED' where i.id=?", Boolean.class, todo)).isTrue();
		assertThat(mvc.perform(get("/projects/" + key + "/issues/1/history").with(user("alice")))
				.andReturn().getResponse().getContentAsString()).contains("changed sprint from", "Sprint A");
		assertThat(mvc.perform(get(url(key)).with(user("alice")))
				.andReturn().getResponse().getContentAsString())
				.contains("<td>1</td>");
	}

	@Test
	void completionAndMembershipRollBackIfHistoryCannotBeRecorded() throws Exception {
		String key = project(); create(key, "Rollback");
		long sprint = id(key, "Rollback");
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf())
				.param("title", "Not done")).andExpect(status().isFound());
		long issue = jdbc.queryForObject("select i.id from issue i join project p on p.id=i.project_id where p.key=?", Long.class, key);
		jdbc.update("insert into sprint_issue(issue_id,sprint_id) values (?,?)", issue, sprint);
		assertThat(action(key, sprint, "start")).isEqualTo(302);
		assertThatThrownBy(() -> sprints.complete(
				jdbc.queryForObject("select id from project where key=?", Long.class, key), sprint, -1))
				.isInstanceOf(RuntimeException.class);
		assertThat(state(sprint)).isEqualTo("ACTIVE");
		assertThat(jdbc.queryForObject("select count(*) from sprint_issue where issue_id=?", Integer.class, issue)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from issue_history where issue_id=? and kind='SPRINT_CHANGED'",
				Integer.class, issue)).isZero();
	}

	@Test
	void spr18_authenticationAndCsrfApplyToPageAndMutations() throws Exception {
		String key = project(); create(key, "Hidden"); long sprint = id(key, "Hidden");
		for (var request : List.of(get(url(key)), post(url(key)).with(csrf()).param("name", "Secret"),
				post(url(key) + "/" + sprint + "/start").with(csrf()),
				post(url(key) + "/" + sprint + "/complete").with(csrf()))) {
			mvc.perform(request).andExpect(status().isFound()).andExpect(redirectedUrl("/login"))
					.andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
			mvc.perform(request.header("HX-Request", "true")).andExpect(status().isUnauthorized())
					.andExpect(result -> assertThat(result.getResponse().getHeader("HX-Redirect")).isEqualTo("/login"));
		}
		mvc.perform(post(url(key)).with(user("alice")).param("name", "Not created")).andExpect(status().isForbidden());
		mvc.perform(post(url(key) + "/" + sprint + "/start").with(user("alice"))).andExpect(status().isForbidden());
		assertThat(state(sprint)).isEqualTo("PLANNED");
	}
}
