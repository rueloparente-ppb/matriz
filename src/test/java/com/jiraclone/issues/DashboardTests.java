package com.jiraclone.issues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jiraclone.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class DashboardTests extends AbstractIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	private String key() {
		return UUID.randomUUID().toString().substring(0, 8).toUpperCase()
				.replace('0', 'G').replace('1', 'H').replace('2', 'I').replace('3', 'J')
				.replace('4', 'K').replace('5', 'L').replace('6', 'M').replace('7', 'N')
				.replace('8', 'O').replace('9', 'P');
	}

	private void project(String who, String key) throws Exception {
		mvc.perform(post("/projects").with(user(who)).with(csrf()).param("key", key).param("name", "Dashboard " + key))
				.andExpect(status().isFound());
	}

	private void issue(String key) throws Exception {
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf()).param("title", "An issue"))
				.andExpect(status().isFound());
	}

	private String dashboard(String who) throws Exception {
		return mvc.perform(get("/").with(user(who))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private void counts(String key, long todo, long inProgress, long done) throws Exception {
		assertThat(dashboard("alice")).containsPattern("(?s)href=\"/projects/" + key
				+ "\"[^>]*>[^<]*</a></td>\\s*<td>" + todo + "</td>\\s*<td>" + inProgress
				+ "</td>\\s*<td>" + done + "</td>");
	}

	@Test
	void countsEveryIssueAndRefreshesAfterStatusCreateAndDelete() throws Exception {
		String key = key();
		project("alice", key);
		counts(key, 0, 0, 0);
		for (int i = 0; i < 26; i++) issue(key);
		counts(key, 26, 0, 0);
		mvc.perform(post("/projects/" + key + "/issues/1/status").with(user("alice")).with(csrf())
				.param("value", "IN_PROGRESS")).andExpect(status().isOk());
		counts(key, 25, 1, 0);
		mvc.perform(post("/projects/" + key + "/issues/2/status").with(user("alice")).with(csrf())
				.param("value", "DONE")).andExpect(status().isOk());
		counts(key, 24, 1, 1);
		issue(key);
		counts(key, 25, 1, 1);
		mvc.perform(post("/projects/" + key + "/issues/1/delete").with(user("alice")).with(csrf()))
				.andExpect(status().isFound());
		counts(key, 25, 0, 1);
		mvc.perform(get("/projects/" + key + "/issues").with(user("alice")).param("page", "2"))
				.andExpect(status().isOk()).andExpect(result -> assertThat(result.getResponse().getContentAsString())
					.contains(key + "-2").doesNotContain(key + "-1</a>"));
	}

	@Test
	void memberSeesProjectButOthersDoNotAndEmptyStateHasNoLeakage() throws Exception {
		String owned = key(), member = key(), hidden = key();
		project("alice", owned);
		project("bob", member);
		project("bob", hidden);
		mvc.perform(post("/projects/" + member + "/issues").with(user("bob")).with(csrf())
				.param("title", "For Alice")).andExpect(status().isFound());
		long alice = jdbc.queryForObject("select id from app_user where username='alice'", Long.class);
		jdbc.update("insert into issue_assignee(issue_id,user_id) select i.id, ? from issue i "
				+ "join project p on p.id=i.project_id where p.key=?", alice, member);
		assertThat(dashboard("alice")).contains("Dashboard for Alice Anderson", "/projects/" + owned,
				"/projects/" + member).doesNotContain(hidden);
		String username = "empty" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("insert into app_user(username,display_name,password_hash) values (?, ?, ?)",
				username, "Empty User", "not-used");
		assertThat(dashboard(username)).contains("Dashboard for Empty User", "No projects found.")
				.doesNotContain(owned, member, hidden);
		assertThat(mvc.perform(get("/projects").with(user(username))).andReturn().getResponse().getContentAsString())
				.contains("No projects found.").doesNotContain(owned, member, hidden);
	}
}
