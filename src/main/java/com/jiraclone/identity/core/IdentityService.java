package com.jiraclone.identity.core;

import com.jiraclone.identity.IdentityApi;
import com.jiraclone.identity.UserRef;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class IdentityService implements IdentityApi {

	private final AppUserRepository users;

	IdentityService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public Optional<UserRef> currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return Optional.empty();
		}
		return users.findByUsernameIgnoreCase(authentication.getName()).map(IdentityService::toRef);
	}

	@Override
	public Optional<UserRef> findUser(long id) {
		return users.findById(id).map(IdentityService::toRef);
	}

	@Override
	public List<UserRef> listUsers() {
		return users.findAllByOrderByDisplayNameAsc().stream().map(IdentityService::toRef).toList();
	}

	private static UserRef toRef(AppUser u) {
		return new UserRef(u.getId(), u.getUsername(), u.getDisplayName());
	}
}
