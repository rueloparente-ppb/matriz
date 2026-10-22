package com.jiraclone;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class JiraCloneApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void modulesAreValid() {
		ApplicationModules.of(JiraCloneApplication.class).verify();
	}
}
