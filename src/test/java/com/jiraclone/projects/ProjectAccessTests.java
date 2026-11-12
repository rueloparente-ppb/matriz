package com.jiraclone.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jiraclone.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ProjectAccessTests extends AbstractIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ProjectsApi projects;

	private String project() throws Exception {
		String key = "A" + UUID.randomUUID().toString().substring(0, 7).toUpperCase().replaceAll("[0-9]", "A");
		mvc.perform(post("/projects").with(user("alice")).with(csrf()).param("key", key).param("name", "Private"))
				.andExpect(status().isFound());
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf()).param("title", "Restricted"))
				.andExpect(status().isFound());
		return key;
	}

	private long id(String username) {
		return jdbc.queryForObject("select id from app_user where username=?", Long.class, username);
	}

	private void assign(String key, String username) {
		jdbc.update("insert into issue_assignee(issue_id,user_id) select i.id, ? from issue i "
				+ "join project p on p.id=i.project_id where p.key=?", id(username), key);
	}

	@Test
	void inaccessibleAndUnknownProjectsHaveIdentical404AcrossModules() throws Exception {
		String key = project();
		for (String suffix : List.of("", "/issues", "/issues/1", "/issues/1/comments", "/issues/1/history",
				"/board", "/sprints", "/issues/1/sprint")) {
			String expected = mvc.perform(get("/projects/NOPE" + suffix).with(user("bob")).header("HX-Request", "true"))
					.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
			mvc.perform(get("/projects/" + key + suffix).with(user("bob")).header("HX-Request", "true"))
					.andExpect(status().isNotFound()).andExpect(content().string(expected));
		}
		mvc.perform(get("/projects/" + key + "/issues").with(user("bob")).header("HX-Request", "true"))
				.andExpect(status().isNotFound()).andExpect(content().string(org.hamcrest.Matchers.not(
					org.hamcrest.Matchers.containsString("<html"))));
	}

	@Test
	void assignedMemberCanViewAndCommentButCannotMutate() throws Exception {
		String key = project();
		assign(key, "bob");
		assertThat(projects.findAccessible(key, id("bob"))).get()
				.extracting(ProjectAccess::role).isEqualTo(ProjectAccess.Role.MEMBER);
		for (String suffix : List.of("", "/issues", "/issues/1", "/issues/1/comments", "/issues/1/history",
				"/board", "/sprints", "/issues/1/sprint")) {
			int expected = suffix.isEmpty() ? 302 : 200;
			mvc.perform(get("/projects/" + key + suffix).with(user("bob")))
					.andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(expected));
		}
		String base = "/projects/" + key;
		long sprint = jdbc.queryForObject("insert into sprint(project_id,name,state) "
				+ "select id,'Access sprint','PLANNED' from project where key=? returning id", Long.class, key);
		for (MockHttpServletRequestBuilder request : List.of(
				post(base + "/issues").param("title", "Denied"),
				post(base + "/issues/1/title").param("value", "Denied"),
				post(base + "/issues/1/status").param("value", "DONE"),
				post(base + "/board/move").param("issue", "1").param("status", "DONE"),
				post(base + "/sprints").param("name", "Denied"),
				post(base + "/sprints/" + sprint + "/start"),
				post(base + "/sprints/" + sprint + "/complete"),
				post(base + "/issues/1/sprint").param("sprint", String.valueOf(sprint)))) {
			mvc.perform(request.with(user("bob")).with(csrf()).header("HX-Request", "true"))
					.andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("<html"))));
		}
		assertThat(jdbc.queryForObject("select title || ':' || status from issue i join project p on p.id=i.project_id "
				+ "where p.key=?", String.class, key)).isEqualTo("Restricted:TO_DO");
		assertThat(jdbc.queryForObject("select count(*) from issue i join project p on p.id=i.project_id "
				+ "where p.key=?", Integer.class, key)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select state from sprint where id=?", String.class, sprint)).isEqualTo("PLANNED");
		mvc.perform(post(base + "/issues/1/comments").with(user("bob")).with(csrf())
				.param("body", "Member comment").param("authorId", String.valueOf(id("carol")))
				.param("createdAt", "1990-01-01")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select author_id from comment where body='Member comment'", Long.class)).isEqualTo(id("bob"));
		mvc.perform(get(base + "/issues/1/comments").with(user("bob"))).andExpect(status().isOk());
		jdbc.update("delete from issue_assignee where user_id=? and issue_id=(select i.id from issue i "
				+ "join project p on p.id=i.project_id where p.key=?)", id("bob"), key);
		mvc.perform(get(base + "/issues/1").with(user("bob"))).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select count(*) from comment where body='Member comment'", Integer.class)).isEqualTo(1);
	}

	@Test
	void nonMember404BeforeOwnerGuardAndCsrf403BeforeLookup() throws Exception {
		String key = project();
		String url = "/projects/" + key + "/issues/1/title";
		mvc.perform(post(url).with(user("bob")).with(csrf()).param("value", "Denied"))
				.andExpect(status().isNotFound());
		mvc.perform(post(url).with(user("bob")).param("value", "Denied"))
				.andExpect(status().isForbidden());
		assign(key, "bob");
		mvc.perform(post("/projects/" + key + "/issues/99/title").with(user("bob")).with(csrf()))
				.andExpect(status().isNotFound());
		mvc.perform(post("/projects/" + key + "/sprints/999999/start").with(user("bob")).with(csrf()))
				.andExpect(status().isNotFound());
	}
}
