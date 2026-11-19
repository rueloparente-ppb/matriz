package com.jiraclone.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class DeletionTests extends AbstractIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	private String project() throws Exception {
		StringBuilder key = new StringBuilder("D");
		for (int i = 0; i < 7; i++) key.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
		mvc.perform(post("/projects").with(user("alice")).with(csrf())
				.param("key", key.toString()).param("name", "Deletion test")).andExpect(status().isFound());
		return key.toString();
	}

	private String issue(String key, int number) throws Exception {
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf())
				.param("title", "Issue " + number)).andExpect(status().isFound());
		return "/projects/" + key + "/issues/" + number;
	}

	private long id(String table, String column, long parent) {
		return jdbc.queryForObject("select id from " + table + " where " + column + "=?", Long.class, parent);
	}

	private long projectId(String key) {
		return jdbc.queryForObject("select id from project where key=?", Long.class, key);
	}

	private long issueId(long projectId, int number) {
		return jdbc.queryForObject("select id from issue where project_id=? and number=?", Long.class, projectId, number);
	}

	private int count(String table, String column, long id) {
		return jdbc.queryForObject("select count(*) from " + table + " where " + column + "=?", Integer.class, id);
	}

	private void seedDependents(String key, int number) throws Exception {
		String base = "/projects/" + key;
		long bob = jdbc.queryForObject("select id from app_user where username='bob'", Long.class);
		mvc.perform(post(base + "/issues/" + number + "/assignees").with(user("alice")).with(csrf())
				.param("userId", String.valueOf(bob))).andExpect(status().isFound());
		mvc.perform(post(base + "/issues/" + number + "/comments").with(user("bob")).with(csrf())
				.param("body", "A comment")).andExpect(status().isOk());
		mvc.perform(post(base + "/sprints").with(user("alice")).with(csrf())
				.param("name", "Iteration")).andExpect(status().isFound());
		long sprint = id("sprint", "project_id", projectId(key));
		mvc.perform(post(base + "/issues/" + number + "/sprint").with(user("alice")).with(csrf())
				.param("sprint", String.valueOf(sprint))).andExpect(status().isFound());
	}

	@Test
	void issueDeleteCascadesRevokesMembershipAndNeverReusesNumbers() throws Exception {
		String key = project();
		issue(key, 1);
		issue(key, 2);
		String third = issue(key, 3);
		seedDependents(key, 3);
		long projectId = projectId(key);
		long deleted = issueId(projectId, 3);
		assertThat(count("issue_history", "issue_id", deleted)).isPositive();
		mvc.perform(get(third).with(user("alice"))).andExpect(content().string(containsString(third + "/delete")));
		mvc.perform(get(third + "/delete").with(user("alice"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("comments, history, assignments and sprint membership")));
		assertThat(count("issue", "id", deleted)).isEqualTo(1);
		mvc.perform(get(third).with(user("bob"))).andExpect(content().string(not(containsString("Delete issue"))));
		mvc.perform(post(third + "/delete").with(user("alice")).with(csrf()))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/projects/" + key + "/issues"));
		for (String table : List.of("issue", "comment", "issue_history", "issue_assignee", "sprint_issue")) {
			assertThat(count(table, table.equals("issue") ? "id" : "issue_id", deleted)).isZero();
		}
		mvc.perform(get(third).with(user("alice"))).andExpect(status().isNotFound());
		mvc.perform(get("/projects/" + key).with(user("bob"))).andExpect(status().isNotFound());
		mvc.perform(get("/projects/" + key + "/issues").with(user("alice")))
				.andExpect(content().string(not(containsString("Issue 3"))));
		issue(key, 4);
		assertThat(jdbc.queryForList("select number from issue where project_id=? order by number", Integer.class, projectId))
				.containsExactly(1, 2, 4);
	}

	@Test
	void issueDeleteChecksIssueBeforeOwnerAndRedirectsHtmx() throws Exception {
		String key = project();
		String base = issue(key, 1);
		long bob = jdbc.queryForObject("select id from app_user where username='bob'", Long.class);
		jdbc.update("insert into issue_assignee(issue_id,user_id) values (?,?)", issueId(projectId(key), 1), bob);
		for (String hx : List.of("", "true")) {
			for (String who : List.of("bob", "carol")) {
				var confirmation = get(base + "/delete").with(user(who));
				var request = post(base + "/delete").with(user(who)).with(csrf());
				if (!hx.isEmpty()) { confirmation.header("HX-Request", hx); request.header("HX-Request", hx); }
				mvc.perform(confirmation).andExpect(status().is(who.equals("bob") ? 403 : 404));
				mvc.perform(request).andExpect(status().is(who.equals("bob") ? 403 : 404));
			}
			var missing = post("/projects/" + key + "/issues/99/delete").with(user("bob")).with(csrf());
			if (!hx.isEmpty()) missing.header("HX-Request", hx);
			mvc.perform(missing).andExpect(status().isNotFound());
		}
		mvc.perform(post(base + "/delete").with(user("alice")).with(csrf()).header("HX-Request", "true"))
				.andExpect(status().isOk()).andExpect(header().string("HX-Redirect", "/projects/" + key + "/issues"));
	}

	@Test
	void projectConfirmationAndDeleteCascadeAndKeyReuse() throws Exception {
		String key = project();
		String base = issue(key, 1);
		seedDependents(key, 1);
		long projectId = projectId(key);
		long issueId = issueId(projectId, 1);
		long sprintId = id("sprint", "project_id", projectId);
		mvc.perform(get("/projects/" + key + "/issues").with(user("alice")))
				.andExpect(content().string(containsString("/projects/" + key + "/delete")));
		mvc.perform(get("/projects/" + key + "/delete").with(user("alice")))
				.andExpect(status().isOk()).andExpect(content().string(containsString(key)))
				.andExpect(content().string(containsString("comments, history, sprints, sprint memberships and assignments")));
		assertThat(count("project", "id", projectId)).isEqualTo(1);
		assertThat(count("issue", "id", issueId)).isEqualTo(1);
		mvc.perform(post("/projects/" + key + "/delete").with(user("alice")).with(csrf()))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/projects"));
		for (String table : List.of("project", "issue_counter", "issue", "sprint")) {
			assertThat(count(table, table.equals("project") ? "id" : "project_id", projectId)).isZero();
		}
		for (String table : List.of("comment", "issue_history", "issue_assignee", "sprint_issue")) {
			assertThat(count(table, "issue_id", issueId)).isZero();
		}
		assertThat(count("sprint_issue", "sprint_id", sprintId)).isZero();
		mvc.perform(get(base).with(user("alice"))).andExpect(status().isNotFound());
		mvc.perform(get("/projects/" + key).with(user("alice"))).andExpect(status().isNotFound());
		mvc.perform(post("/projects").with(user("alice")).with(csrf()).param("key", key).param("name", "Reused"))
				.andExpect(status().isFound());
		assertThat(projectId(key)).isNotEqualTo(projectId);
	}

	@Test
	void projectDeleteIsOwnerOnlyAndHtmxSafe() throws Exception {
		String key = project();
		issue(key, 1);
		long bob = jdbc.queryForObject("select id from app_user where username='bob'", Long.class);
		jdbc.update("insert into issue_assignee(issue_id,user_id) values (?,?)", issueId(projectId(key), 1), bob);
		String path = "/projects/" + key + "/delete";
		mvc.perform(get("/projects/" + key + "/issues").with(user("bob")))
				.andExpect(content().string(not(containsString(path))));
		for (String who : List.of("bob", "carol")) {
			for (String hx : List.of("", "true")) {
				var getRequest = get(path).with(user(who));
				var postRequest = post(path).with(user(who)).with(csrf());
				if (!hx.isEmpty()) { getRequest.header("HX-Request", hx); postRequest.header("HX-Request", hx); }
				int expected = who.equals("bob") ? 403 : 404;
				mvc.perform(getRequest).andExpect(status().is(expected));
				mvc.perform(postRequest).andExpect(status().is(expected));
			}
		}
		mvc.perform(post(path).with(user("alice")).with(csrf()).header("HX-Request", "true"))
				.andExpect(status().isOk()).andExpect(header().string("HX-Redirect", "/projects"));
	}

	@Test
	void failedProjectCascadeRollsBackProjectAndEveryChild() throws Exception {
		String key = project();
		issue(key, 1);
		seedDependents(key, 1);
		long projectId = projectId(key);
		long issueId = issueId(projectId, 1);
		long sprintId = id("sprint", "project_id", projectId);
		jdbc.execute("create function fail_project_cascade() returns trigger language plpgsql as 'begin raise exception ''test failure''; end'");
		jdbc.execute("create trigger fail_project_cascade before delete on issue for each row execute function fail_project_cascade()");
		try {
			assertThatThrownBy(() -> mvc.perform(post("/projects/" + key + "/delete")
					.with(user("alice")).with(csrf()))).isInstanceOf(Exception.class);
		} finally {
			jdbc.execute("drop trigger fail_project_cascade on issue");
			jdbc.execute("drop function fail_project_cascade()");
		}
		assertThat(count("project", "id", projectId)).isEqualTo(1);
		assertThat(count("issue_counter", "project_id", projectId)).isEqualTo(1);
		assertThat(count("issue", "id", issueId)).isEqualTo(1);
		assertThat(count("sprint", "id", sprintId)).isEqualTo(1);
		for (String table : List.of("comment", "issue_history", "issue_assignee", "sprint_issue")) {
			assertThat(count(table, "issue_id", issueId)).isPositive();
		}
	}
}
