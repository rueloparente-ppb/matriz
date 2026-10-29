package com.jiraclone.identity;

import java.util.List;
import java.util.Optional;

/** Public synchronous API of the identity module. */
public interface IdentityApi {

	/** The authenticated user of the current request, or empty when nobody is logged in. */
	Optional<UserRef> currentUser();

	Optional<UserRef> findUser(long id);

	/** All users, ordered by display name. */
	List<UserRef> listUsers();
}
