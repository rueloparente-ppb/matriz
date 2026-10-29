package com.jiraclone.identity.core;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository users;

	AppUserDetailsService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		return users.findByUsernameIgnoreCase(username)
				.map(u -> User.withUsername(u.getUsername())
						.password(u.getPasswordHash())
						.authorities(AuthorityUtils.NO_AUTHORITIES)
						.build())
				.orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
	}
}
