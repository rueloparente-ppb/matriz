package com.jiraclone;

import org.springframework.boot.SpringApplication;

public class TestJiraCloneApplication {

	public static void main(String[] args) {
		SpringApplication.from(JiraCloneApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
