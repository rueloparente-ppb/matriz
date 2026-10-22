package com.jiraclone;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

class BaseLayoutTests extends AbstractIntegrationTest {

	@Autowired
	SpringTemplateEngine templateEngine;

	@Test
	void layoutLoadsBootstrapHtmxAndSortable() {
		String html = templateEngine.process("layout-test", new Context());

		assertThat(html)
				.contains("bootstrap@5")
				.contains("htmx.org")
				.contains("sortablejs")
				.contains("id=\"navbar-slot\"")
				.contains("<title>Test page</title>")
				.contains("<p>hello</p>");
	}
}
