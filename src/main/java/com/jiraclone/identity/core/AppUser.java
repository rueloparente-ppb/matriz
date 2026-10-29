package com.jiraclone.identity.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String username;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	protected AppUser() {
	}

	Long getId() {
		return id;
	}

	String getUsername() {
		return username;
	}

	String getDisplayName() {
		return displayName;
	}

	String getPasswordHash() {
		return passwordHash;
	}
}
