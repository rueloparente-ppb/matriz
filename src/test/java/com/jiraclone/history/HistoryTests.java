package com.jiraclone.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jiraclone.AbstractIntegrationTest;
import com.jiraclone.JiraCloneApplication;
import com.jiraclone.history.core.HistoryRecorder;
import com.jiraclone.issues.IssueCreated;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;

class HistoryTests extends AbstractIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;
	@Autowired HistoryRecorder recorder;

	private String project() throws Exception {
		StringBuilder key = new StringBuilder("H");
		for (int i = 0; i < 7; i++) {
			key.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
		}
		mvc.perform(post("/projects").with(user("alice")).with(csrf())
				.param("key", key.toString()).param("name", "History test")).andExpect(status().isFound());
		return key.toString();
	}

	private void create(String key, String title) throws Exception {
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf())
				.param("title", title)).andExpect(status().isFound());
	}

	private String path(String key, int number) {
		return "/projects/" + key + "/issues/" + number + "/history";
	}

	private void edit(String key, String field, String value) throws Exception {
		mvc.perform(post("/projects/" + key + "/issues/1/" + field).with(user("bob")).with(csrf())
				.param("value", value)).andExpect(status().isOk());
	}

	private List<String> values(String key, String column) {
		return jdbc.queryForList("select h." + column + " from issue_history h join issue i on i.id=h.issue_id "
				+ "join project p on p.id=i.project_id where p.key=? and i.number=1 "
				+ "order by h.created_at desc, h.id desc", String.class, key);
	}

	@Test
	void hst1_hst2_hst4_hst5_hst6_hst7_recordsRealChangesWithStableDisplayValues() throws Exception {
		String key = project();
		create(key, "Original");
		assertThat(values(key, "kind")).containsExactly("CREATED");
		assertThat(values(key, "actor_name")).containsExactly("Alice Anderson");
		assertThat(jdbc.queryForObject("select h.created_at from issue_history h join issue i on i.id=h.issue_id "
				+ "join project p on p.id=i.project_id where p.key=?", java.time.OffsetDateTime.class, key)).isNotNull();
		edit(key, "title", "Changed");
		long bob = jdbc.queryForObject("select id from app_user where username='bob'", Long.class);
		edit(key, "assignee", "" + bob);
		edit(key, "assignee", "none");
		edit(key, "assignee", "" + bob);
		mvc.perform(post("/projects/" + key + "/issues/1/status").with(user("bob")).with(csrf())
				.param("value", "INVALID")).andExpect(status().isUnprocessableEntity());
		edit(key, "title", "Changed");
		assertThat(values(key, "kind")).containsExactly("FIELD_CHANGED", "FIELD_CHANGED", "FIELD_CHANGED", "FIELD_CHANGED", "CREATED");
		assertThat(values(key, "field")).containsExactly("assignee", "assignee", "assignee", "title", null);
		assertThat(values(key, "old_value")).containsExactly("None", "Bob Brown", "None", "Original", null);
		assertThat(values(key, "new_value")).containsExactly("Bob Brown", "None", "Bob Brown", "Changed", null);
		assertThat(values(key, "actor_name")).containsExactly("Bob Brown", "Bob Brown", "Bob Brown", "Bob Brown", "Alice Anderson");
		String html = mvc.perform(get(path(key, 1)).with(user("alice"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("Bob Brown", "<span>assignee</span>", "Original", "Changed");
		assertThat(html.indexOf("<span>assignee</span>")).isLessThan(html.indexOf("<span>title</span>"));
	}

	@Test
	void hst3_brd9_boardMoveUsesSameStatusHistory() throws Exception {
		String key = project();
		create(key, "Board issue");
		mvc.perform(post("/projects/" + key + "/board/move").with(user("bob")).with(csrf())
				.param("issue", "1").param("status", "DONE")).andExpect(status().isOk());
		assertThat(values(key, "field")).containsExactly("status", null);
		assertThat(values(key, "old_value")).containsExactly("TO_DO", null);
		assertThat(values(key, "new_value")).containsExactly("DONE", null);
	}

	@Test
	void hst10_commentsDoNotWriteHistory() throws Exception {
		String key = project();
		create(key, "Commented");
		mvc.perform(post("/projects/" + key + "/issues/1/comments").with(user("bob")).with(csrf())
				.param("body", "A comment")).andExpect(status().isOk());
		assertThat(values(key, "kind")).containsExactly("CREATED");
	}

	@Test
	void hst8_hst9_hst11_hst12_hst14_fragmentIsScopedTruncatedAndEscaped() throws Exception {
		String key = project();
		String other = project();
		create(key, "First");
		create(key, "Second");
		create(other, "Foreign");
		mvc.perform(post("/projects/" + key + "/issues/2/title").with(user("bob")).with(csrf())
				.param("value", "Only on second issue")).andExpect(status().isOk());
		edit(other, "title", "Only in other project");
		String longValue = "x".repeat(101) + "<script>alert(1)</script>";
		edit(key, "description", longValue);
		String html = mvc.perform(get(path(key, 1)).with(user("alice"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("x".repeat(100) + "…", "History")
				.doesNotContain(longValue, "Foreign", "Second", "Only on second issue", "Only in other project", "<html");
		assertThat(values(key, "new_value")).contains(longValue);
		edit(key, "title", "<script>alert(1)</script>");
		mvc.perform(get(path(key, 1)).with(user("alice"))).andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString())
						.contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>"));
		mvc.perform(get("/projects/" + key + "/issues/1").with(user("alice")))
				.andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("/history"));
		for (String missing : List.of(path("NOPE", 1), path(key, 99), path(other, 2))) {
			mvc.perform(get(missing).with(user("alice"))).andExpect(status().isNotFound());
		}
		mvc.perform(get(path(key, 1))).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
		mvc.perform(get(path(key, 1)).header("HX-Request", "true")).andExpect(status().isUnauthorized());
	}

	@Test
	void recorderRequiresTransactionAndFailedRecorderRollsBackEdit() throws Exception {
		assertThatThrownBy(() -> recorder.created(new IssueCreated(1, 1)))
				.isInstanceOf(IllegalTransactionStateException.class);
		String key = project();
		create(key, "Before");
		jdbc.execute("alter table issue_history add constraint fail_history_write check (kind <> 'FIELD_CHANGED') not valid");
		try {
			assertThatThrownBy(() -> edit(key, "title", "After")).isInstanceOf(Exception.class);
		} finally {
			jdbc.execute("alter table issue_history drop constraint fail_history_write");
		}
		assertThat(jdbc.queryForObject("select title from issue i join project p on p.id=i.project_id where p.key=?",
				String.class, key)).isEqualTo("Before");
		assertThat(values(key, "kind")).containsExactly("CREATED");
	}

	@Test
	void hst13_historySurvivesRestart() throws Exception {
		String key = project();
		create(key, "Persisted");
		HikariDataSource ds = dataSource.unwrap(HikariDataSource.class);
		try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(JiraCloneApplication.class)
				.run("--server.port=0", "--spring.datasource.url=" + ds.getJdbcUrl(),
						"--spring.datasource.username=" + ds.getUsername(),
						"--spring.datasource.password=" + ds.getPassword())) {
			assertThat(restarted.getBean(JdbcTemplate.class).queryForObject("select h.kind from issue_history h "
					+ "join issue i on i.id=h.issue_id join project p on p.id=i.project_id where p.key=?",
					String.class, key)).isEqualTo("CREATED");
		}
	}
}
