package com.jiraclone.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import com.jiraclone.JiraCloneApplication;
import com.jiraclone.issues.IssueFieldChanged;
import com.zaxxer.hikari.HikariDataSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@RecordApplicationEvents
class CommentsTests extends AbstractIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;
	@Autowired ApplicationEvents events;

	private String projectWithIssue() throws Exception {
		StringBuilder keyBuilder = new StringBuilder("C");
		for (int i = 0; i < 7; i++) {
			keyBuilder.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
		}
		String key = keyBuilder.toString();
		mvc.perform(post("/projects").with(user("alice")).with(csrf())
				.param("key", key).param("name", "Comments test"))
				.andExpect(status().isFound());
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf())
				.param("title", "Issue"))
				.andExpect(status().isFound());
		return key;
	}

	private String path(String key) {
		return "/projects/" + key + "/issues/1/comments";
	}

	private MockHttpServletRequestBuilder add(String key, String body) {
		return post(path(key)).with(user("bob")).with(csrf()).header("HX-Request", "true").param("body", body);
	}

	private int count(String key) {
		return jdbc.queryForObject("select count(*) from comment c join issue i on i.id=c.issue_id "
				+ "join project p on p.id=i.project_id where p.key=?", Integer.class, key);
	}

	@Test
	void cmt1_cmt6_cmt8_authorAndTimeComeFromServerWithoutTouchingIssue() throws Exception {
		String key = projectWithIssue();
		OffsetDateTime before = jdbc.queryForObject("select updated_at from issue i join project p on p.id=i.project_id "
				+ "where p.key=?", OffsetDateTime.class, key);
		mvc.perform(add(key, "  Hello  ").param("authorId", "99999").param("createdAt", "1990-01-01"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Hello")))
				.andExpect(content().string(containsString("Bob Brown")))
				.andExpect(content().string(not(containsString("1990-01-01"))))
				.andExpect(content().string(not(containsString("<html"))));
		var row = jdbc.queryForMap("select c.body,c.author_id,c.created_at from comment c "
				+ "join issue i on i.id=c.issue_id join project p on p.id=i.project_id where p.key=?", key);
		assertThat(row).containsEntry("body", "Hello").containsEntry("author_id",
				jdbc.queryForObject("select id from app_user where username='bob'", Long.class));
		assertThat(jdbc.queryForObject("select c.created_at from comment c "
				+ "join issue i on i.id=c.issue_id join project p on p.id=i.project_id where p.key=?",
				OffsetDateTime.class, key)).isAfter(before.minusSeconds(1));
		assertThat(jdbc.queryForObject("select updated_at from issue i join project p on p.id=i.project_id "
				+ "where p.key=?", OffsetDateTime.class, key)).isEqualTo(before);
		assertThat(events.stream(IssueFieldChanged.class)).isEmpty();
	}

	@Test
	void cmt2_oldestFirstWithIdentityTieBreakAndProjectScope() throws Exception {
		String key = projectWithIssue();
		String other = projectWithIssue();
		mvc.perform(add(key, "First")).andExpect(status().isOk());
		mvc.perform(add(key, "Second")).andExpect(status().isOk());
		mvc.perform(add(key, "Earlier")).andExpect(status().isOk());
		mvc.perform(add(other, "Foreign")).andExpect(status().isOk());
		jdbc.update("update comment set created_at='2020-01-01T00:00:00Z' "
				+ "where body in ('First','Second') and issue_id=(select i.id from issue i "
				+ "join project p on p.id=i.project_id where p.key=?)", key);
		jdbc.update("update comment set created_at='2019-01-01T00:00:00Z' "
				+ "where body='Earlier' and issue_id=(select i.id from issue i "
				+ "join project p on p.id=i.project_id where p.key=?)", key);
		String html = mvc.perform(get(path(key)).with(user("alice"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("Earlier", "First", "Second").doesNotContain("Foreign");
		assertThat(html.indexOf("Earlier")).isLessThan(html.indexOf("First"));
		assertThat(html.indexOf("First")).isLessThan(html.indexOf("Second"));
	}

	@Test
	void cmt3_cmt4_invalidBodiesReturn422WithTypedTextAndNoInsert() throws Exception {
		String key = projectWithIssue();
		for (String body : List.of("", "   \n  ", "x".repeat(2001))) {
			mvc.perform(add(key, body)).andExpect(status().isUnprocessableEntity())
					.andExpect(content().string(containsString("Comment must be 1–2000 characters")))
					.andExpect(content().string(containsString("name=\"body\"")))
					.andExpect(content().string(containsString(body)));
		}
		assertThat(count(key)).isZero();
		mvc.perform(add(key, " " + "x".repeat(2000) + " ")).andExpect(status().isOk());
		assertThat(count(key)).isEqualTo(1);
	}

	@Test
	void cmt5_missingProjectOrIssueWinsOverInvalidBodyAndNeverCrossesProjects() throws Exception {
		String key = projectWithIssue();
		String other = projectWithIssue();
		for (String invalid : List.of(path("NOPE"), "/projects/" + key + "/issues/99/comments",
				"/projects/" + other + "/issues/2/comments")) {
			mvc.perform(get(invalid).with(user("alice"))).andExpect(status().isNotFound());
			mvc.perform(post(invalid).with(user("alice")).with(csrf()).param("body", ""))
					.andExpect(status().isNotFound());
		}
		assertThat(count(key)).isZero();
		assertThat(count(other)).isZero();
	}

	@Test
	void cmt7_escapedTextRetainsLineBreaksAndDetailLoadsFragment() throws Exception {
		String key = projectWithIssue();
		mvc.perform(add(key, "<script>alert(1)</script>\nNext line")).andExpect(status().isOk());
		mvc.perform(get(path(key)).with(user("alice"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;\nNext line")))
				.andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
				.andExpect(content().string(containsString("white-space: pre-wrap")))
				.andExpect(content().string(containsString("name=\"_csrf\"")));
		mvc.perform(get("/projects/" + key + "/issues/1").with(user("alice")))
				.andExpect(status().isOk()).andExpect(content().string(containsString("hx-get=\"" + path(key))));
	}

	@Test
	void cmt10_authenticationAndCsrfProtectComments() throws Exception {
		String key = projectWithIssue();
		for (var request : List.of(get(path(key)), post(path(key)).with(csrf()).param("body", "Secret"))) {
			mvc.perform(request).andExpect(status().isFound()).andExpect(redirectedUrl("/login"))
					.andExpect(content().string(""));
			mvc.perform(request.header("HX-Request", "true")).andExpect(status().isUnauthorized())
					.andExpect(header().string("HX-Redirect", "/login")).andExpect(content().string(""));
		}
		mvc.perform(post(path(key)).with(user("alice")).header("HX-Request", "true").param("body", "Secret"))
				.andExpect(status().isForbidden());
		assertThat(count(key)).isZero();
	}

	@Test
	void cmt9_commentsSurviveApplicationRestart() throws Exception {
		String key = projectWithIssue();
		mvc.perform(add(key, "Persisted")).andExpect(status().isOk());
		HikariDataSource ds = dataSource.unwrap(HikariDataSource.class);
		try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(JiraCloneApplication.class)
				.run("--server.port=0", "--spring.datasource.url=" + ds.getJdbcUrl(),
						"--spring.datasource.username=" + ds.getUsername(),
						"--spring.datasource.password=" + ds.getPassword())) {
			assertThat(restarted.getBean(JdbcTemplate.class).queryForObject(
					"select c.body from comment c join issue i on i.id=c.issue_id "
							+ "join project p on p.id=i.project_id where p.key=?", String.class, key))
					.isEqualTo("Persisted");
		}
	}
}
