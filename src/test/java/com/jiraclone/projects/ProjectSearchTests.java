package com.jiraclone.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jiraclone.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class ProjectSearchTests extends AbstractIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	private String key() {
		return UUID.randomUUID().toString().substring(0, 8).toUpperCase()
				.replace('0', 'G').replace('1', 'H').replace('2', 'I').replace('3', 'J')
				.replace('4', 'K').replace('5', 'L').replace('6', 'M').replace('7', 'N')
				.replace('8', 'O').replace('9', 'P');
	}

	private void create(String who, String key, String name) throws Exception {
		mvc.perform(post("/projects").with(user(who)).with(csrf()).param("key", key).param("name", name))
				.andExpect(status().isFound());
	}

	@Test
	void searchesAccessibleProjectsWithoutDisclosingOthers() throws Exception {
		String suffix = key();
		String owned = key(), member = key(), hidden = key();
		create("alice", owned, "Findable " + suffix);
		create("bob", member, "Findable " + suffix);
		create("bob", hidden, "Findable " + suffix);
		mvc.perform(post("/projects/" + member + "/issues").with(user("bob")).with(csrf())
				.param("title", "For Alice")).andExpect(status().isFound());
		long alice = jdbc.queryForObject("select id from app_user where username='alice'", Long.class);
		jdbc.update("insert into issue_assignee(issue_id,user_id) select i.id, ? from issue i "
				+ "join project p on p.id=i.project_id where p.key=?", alice, member);
		for (String q : new String[] {suffix.toLowerCase(), "  fInDaBlE " + suffix.toLowerCase() + "  "}) {
			String html = mvc.perform(get("/projects").with(user("alice")).param("q", q))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(html).contains(owned, member).doesNotContain(hidden);
		}
		mvc.perform(get("/projects").with(user("alice")).param("q", "  "))
				.andExpect(status().isOk()).andExpect(content().string(not(containsString(hidden))));
		mvc.perform(get("/projects").with(user("alice")).param("q", "%" + suffix))
				.andExpect(content().string(not(containsString(owned))));
		mvc.perform(get("/projects").with(user("bob")).param("q", suffix))
				.andExpect(content().string(not(containsString(owned))));
	}

	@Test
	void pagesAccessibleProjectsAndValidatesPage() throws Exception {
		String suffix = key();
		String first = "AA" + suffix.substring(0, 6), last = "ZZ" + suffix.substring(0, 6);
		create("alice", first, "Paging " + suffix);
		for (int i = 0; i < 24; i++) create("alice", "MM" + key().substring(0, 8), "Paging " + suffix);
		create("alice", last, "Paging " + suffix);
		create("bob", key(), "Paging " + suffix);
		String pageOne = mvc.perform(get("/projects").with(user("alice")).param("q", suffix))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(pageOne).contains(first, "page=2").doesNotContain(last);
		String pageTwo = mvc.perform(get("/projects").with(user("alice")).param("q", suffix).param("page", "2"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(pageTwo).contains(last, "Previous").doesNotContain(first);
		for (String invalid : new String[] {"abc", "0", "-2", "999999999999999999999"}) {
			mvc.perform(get("/projects").with(user("alice")).param("page", invalid))
					.andExpect(status().isUnprocessableEntity()).andExpect(content().string(containsString("Page must")));
		}
		mvc.perform(get("/projects").with(user("alice")).param("q", suffix).param("page", "3"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("No projects found.")))
				.andExpect(content().string(not(containsString(last))));
		mvc.perform(get("/projects").with(user("alice")).param("q", "missing-" + suffix))
				.andExpect(content().string(containsString("No projects found.")));
	}

	@Test
	void blankQueryListsAllAccessibleProjects() throws Exception {
		String username = "search" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("insert into app_user(username,display_name,password_hash) values (?, ?, ?)",
				username, "Search User", "not-used");
		String one = key(), two = key(), hidden = key();
		create(username, one, "First project");
		create(username, two, "Second project");
		create("bob", hidden, "Other project");
		for (String q : new String[] {"", "  "}) {
			String html = mvc.perform(get("/projects").with(user(username)).param("q", q))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			assertThat(html).contains(one, two).doesNotContain(hidden);
		}
	}
}
