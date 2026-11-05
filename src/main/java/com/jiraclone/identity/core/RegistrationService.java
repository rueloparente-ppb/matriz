package com.jiraclone.identity.core;

import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;

	RegistrationService(AppUserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	public boolean isTaken(String username) {
		return users.findByUsernameIgnoreCase(username).isPresent();
	}

	/** The repository owns the write transaction, so a failed flush cannot mark an outer transaction rollback-only. */
	public Optional<String> register(String username, String password) {
		if (isTaken(username)) {
			return Optional.empty();
		}
		try {
			users.saveAndFlush(new AppUser(username, passwordEncoder.encode(password)));
			return Optional.of(username);
		} catch (DataIntegrityViolationException e) {
			if (isTaken(username)) {
				return Optional.empty();
			}
			throw e;
		}
	}
}
