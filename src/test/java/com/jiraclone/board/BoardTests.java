package com.jiraclone.board;

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
import com.jiraclone.issues.IssueFieldChanged;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@RecordApplicationEvents
class BoardTests extends AbstractIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ApplicationEvents events;

	private String project() throws Exception {
		StringBuilder keyBuilder = new StringBuilder("B");
		for (int i = 0; i < 6; i++) {
			keyBuilder.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
		}
		String key = keyBuilder.toString();
		mvc.perform(post("/projects").with(user("alice")).with(csrf()).param("key", key).param("name", "Board"))
				.andExpect(status().isFound());
		return key;
	}

	private void issue(String key, String title) throws Exception {
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf()).param("title", title))
				.andExpect(status().isFound());
	}

	private String path(String key) {
		return "/projects/" + key + "/board";
	}

	private MockHttpServletRequestBuilder move(String key, String issue, String status) {
		return post(path(key) + "/move").with(user("alice")).with(csrf()).header("HX-Request", "true")
				.param("issue", issue).param("status", status);
	}

	private String storedStatus(String key, int number) {
		return jdbc.queryForObject("select i.status from issue i join project p on p.id=i.project_id "
				+ "where p.key=? and i.number=?", String.class, key, number);
	}

	@Test
	void brd1_brd2_brd3_boardShowsOnlyProjectIssuesInOrderedColumnsAndLinks() throws Exception {
		String key = project();
		String other = project();
		issue(key, "First");
		issue(key, "Second");
		long bob = jdbc.queryForObject("select id from app_user where username='bob'", Long.class);
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf())
				.param("title", "Third <script>alert(1)</script>").param("type", "BUG")
				.param("priority", "HIGH").param("assigneeId", String.valueOf(bob)))
				.andExpect(status().isFound());
		issue(other, "Foreign");
		jdbc.update("update issue set status='IN_PROGRESS' where title='Second' and project_id="
				+ "(select id from project where key=?)", key);
		String html = mvc.perform(get(path(key)).with(user("alice"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("<html", "To Do (2)", "In Progress (1)", "Done (0)",
				"First", "Third", "BUG", "HIGH", "Bob Brown", "Unassigned", "/projects/" + key + "/issues/1",
				"&lt;script&gt;alert(1)&lt;/script&gt;")
				.doesNotContain("Foreign", "<script>alert(1)</script>")
				.containsPattern("(?s)data-status=\"TO_DO\".*?data-issue=\"1\".*?data-issue=\"3\".*?data-status=\"IN_PROGRESS\".*?data-issue=\"2\".*?data-status=\"DONE\"");
		mvc.perform(get(path(key)).with(user("alice")).header("HX-Request", "true"))
				.andExpect(status().isOk()).andExpect(content().string(not(containsString("<html"))))
				.andExpect(content().string(containsString("id=\"board\"")));
		mvc.perform(get(path(key)).with(user("alice")).header("HX-Request", "true")
				.header("HX-History-Restore-Request", "true"))
				.andExpect(content().string(containsString("<html")));
	}

	@Test
	void brd4_brd5_moveReturnsRefreshedBoardAndUpdatesDetailAndList() throws Exception {
		String key = project();
		issue(key, "Move me");
		mvc.perform(move(key, "1", "DONE")).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<html"))))
				.andExpect(content().string(containsString("Done (1)")))
				.andExpect(content().string(containsString("To Do (0)")));
		assertThat(storedStatus(key, 1)).isEqualTo("DONE");
		mvc.perform(get("/projects/" + key + "/issues/1").with(user("alice")))
				.andExpect(content().string(containsString("DONE")));
		mvc.perform(get("/projects/" + key + "/issues").with(user("alice")))
				.andExpect(content().string(containsString("DONE")));
		mvc.perform(get(path(key)).with(user("alice"))).andExpect(content().string(containsString("Done (1)")));
		mvc.perform(post(path(key) + "/move").with(user("alice")).with(csrf())
				.param("issue", "1").param("status", "IN_PROGRESS"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("<html")));
	}

	@Test
	void brd6_brd7_brd10_rejectionsPreserveBoardAndFollowPrecedence() throws Exception {
		String key = project();
		String other = project();
		issue(key, "Local");
		issue(other, "Other");
		for (String number : new String[] { "abc", "", "999999999999999999" }) {
			mvc.perform(move(key, number, "DONE")).andExpect(status().isUnprocessableEntity())
					.andExpect(content().string(containsString("To Do (1)")));
		}
		mvc.perform(move(key, "1", "BLOCKED")).andExpect(status().isUnprocessableEntity())
				.andExpect(content().string(containsString("id=\"board\"")));
		mvc.perform(move(other, "2", "BLOCKED")).andExpect(status().isNotFound())
				.andExpect(content().string(containsString("To Do (1)")));
		mvc.perform(move("MISSING", "abc", "BLOCKED")).andExpect(status().isNotFound());
		mvc.perform(get(path("MISSING")).with(user("alice"))).andExpect(status().isNotFound());
		assertThat(storedStatus(key, 1)).isEqualTo("TO_DO");
		assertThat(storedStatus(other, 1)).isEqualTo("TO_DO");
		assertThat(events.stream(IssueFieldChanged.class)).isEmpty();
	}

	@Test
	void brd8_sameStatusDoesNotTouchTimestampOrPublishEvent() throws Exception {
		String key = project();
		issue(key, "Still to do");
		OffsetDateTime before = jdbc.queryForObject("select i.updated_at from issue i join project p on p.id=i.project_id "
				+ "where p.key=?", OffsetDateTime.class, key);
		mvc.perform(move(key, "1", "TO_DO")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select i.updated_at from issue i join project p on p.id=i.project_id "
				+ "where p.key=?", OffsetDateTime.class, key)).isEqualTo(before);
		assertThat(events.stream(IssueFieldChanged.class)).isEmpty();
	}

	@Test
	void brd11_authenticationAndCsrfProtectBoardAndMoves() throws Exception {
		String key = project();
		issue(key, "Secret board item");
		for (var request : new MockHttpServletRequestBuilder[] { get(path(key)), post(path(key) + "/move")
				.with(csrf()).param("issue", "1").param("status", "DONE") }) {
			mvc.perform(request).andExpect(status().isFound()).andExpect(redirectedUrl("/login"))
					.andExpect(content().string(""));
			mvc.perform(request.header("HX-Request", "true")).andExpect(status().isUnauthorized())
					.andExpect(header().string("HX-Redirect", "/login")).andExpect(content().string(""));
		}
		mvc.perform(move(key, "1", "DONE").with(csrf().useInvalidToken()))
				.andExpect(status().isForbidden());
		assertThat(storedStatus(key, 1)).isEqualTo("TO_DO");
	}
}
