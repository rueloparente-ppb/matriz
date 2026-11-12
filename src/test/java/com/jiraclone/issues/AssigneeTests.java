package com.jiraclone.issues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jiraclone.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class AssigneeTests extends AbstractIntegrationTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	private String issue() throws Exception {
		String key = "A" + UUID.randomUUID().toString().substring(0, 7).toUpperCase().replaceAll("[0-9]", "A");
		mvc.perform(post("/projects").with(user("alice")).with(csrf()).param("key", key).param("name", "Private"))
				.andExpect(status().isFound());
		mvc.perform(post("/projects/" + key + "/issues").with(user("alice")).with(csrf()).param("title", "Assigned"))
				.andExpect(status().isFound());
		return "/projects/" + key + "/issues/1";
	}

	private long id(String username) {
		return jdbc.queryForObject("select id from app_user where username=?", Long.class, username);
	}

	private int count(String base) {
		return jdbc.queryForObject("select count(*) from issue_assignee a join issue i on i.id=a.issue_id "
				+ "join project p on p.id=i.project_id where p.key=?", Integer.class, base.split("/")[2]);
	}

	@Test
	void addRemoveEligibilityHistoryAndRevocation() throws Exception {
		String base = issue();
		String url = base + "/assignees";
		String bob = String.valueOf(id("bob"));
		String carol = String.valueOf(id("carol"));
		mvc.perform(get(url).with(user("alice"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Bob")));
		mvc.perform(post(url).with(user("alice")).with(csrf()).param("userId", bob))
				.andExpect(status().isFound()).andExpect(redirectedUrl(base));
		mvc.perform(post(url).with(user("alice")).with(csrf()).header("HX-Request", "true").param("userId", carol))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Bob")))
				.andExpect(content().string(containsString("Carol")))
				.andExpect(content().string(not(containsString("<html"))));
		assertThat(count(base)).isEqualTo(2);
		mvc.perform(get(base).with(user("alice"))).andExpect(content().string(containsString("Bob")))
				.andExpect(content().string(containsString("Carol")));
		mvc.perform(get(url).with(user("alice")).header("HX-Request", "true"))
				.andExpect(content().string(not(containsString("value=\"" + bob + "\""))));
		mvc.perform(post(url).with(user("alice")).with(csrf()).header("HX-Request", "true").param("userId", bob))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(content().string(containsString("value=\"" + bob + "\"")))
				.andExpect(content().string(not(containsString("<html"))));
		for (String bad : List.of("not-a-number", "999999999")) {
			mvc.perform(post(url).with(user("alice")).with(csrf()).param("userId", bad))
					.andExpect(status().isUnprocessableEntity()).andExpect(content().string(containsString("Unknown")));
		}
		mvc.perform(post(url + "/" + id("alice") + "/delete").with(user("alice")).with(csrf()))
				.andExpect(status().isNotFound());
		mvc.perform(post(url + "/" + carol + "/delete").with(user("alice")).with(csrf()))
				.andExpect(status().isFound());
		mvc.perform(post(base + "/comments").with(user("bob")).with(csrf()).param("body", "Still here"))
				.andExpect(status().isOk());
		mvc.perform(post(url + "/" + bob + "/delete").with(user("alice")).with(csrf()))
				.andExpect(status().isFound());
		mvc.perform(get(base).with(user("bob"))).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select count(*) from comment where body='Still here'", Integer.class)).isEqualTo(1);
		assertThat(count(base)).isZero();
		assertThat(jdbc.queryForList("select h.old_value || ':' || h.new_value from issue_history h "
				+ "join issue i on i.id=h.issue_id join project p on p.id=i.project_id "
				+ "where p.key=? and h.field='assignee' and h.actor_id=? order by h.id", String.class,
				base.split("/")[2], id("alice")))
				.containsExactly("None:Bob Brown", "None:Carol Clark", "Carol Clark:None", "Bob Brown:None");
	}

	@Test
	void ownerOnlyAndConcurrentDuplicates() throws Exception {
		String base = issue();
		String url = base + "/assignees";
		String bob = String.valueOf(id("bob"));
		mvc.perform(post(url).with(user("alice")).with(csrf()).param("userId", bob)).andExpect(status().isFound());
		for (String who : List.of("bob", "carol")) {
			int expected = who.equals("bob") ? 403 : 404;
			for (var request : List.of(get(url), post(url).param("userId", bob), post(url + "/" + bob + "/delete"))) {
				mvc.perform(request.with(user(who)).with(csrf())).andExpect(result ->
						assertThat(result.getResponse().getStatus()).isEqualTo(expected));
			}
		}
		mvc.perform(post(url + "/" + bob + "/delete").with(user("alice")).with(csrf())).andExpect(status().isFound());
		for (int round = 0; round < 3; round++) {
			var start = new CountDownLatch(1);
			try (var pool = Executors.newFixedThreadPool(2)) {
				var first = pool.submit(() -> { start.await(); return mvc.perform(post(url).with(user("alice")).with(csrf())
						.param("userId", bob)).andReturn().getResponse().getStatus(); });
				var second = pool.submit(() -> { start.await(); return mvc.perform(post(url).with(user("alice")).with(csrf())
						.param("userId", bob)).andReturn().getResponse().getStatus(); });
				start.countDown();
				assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(302, 422);
				assertThat(count(base)).isEqualTo(1);
			}
			mvc.perform(post(url + "/" + bob + "/delete").with(user("alice")).with(csrf())).andExpect(status().isFound());
		}
	}
}
