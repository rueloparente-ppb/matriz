package com.jiraclone.identity.core;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

	@Query("select u from AppUser u where lower(u.username) = lower(:username)")
	Optional<AppUser> findByUsernameIgnoreCase(String username);

	List<AppUser> findAllByOrderByDisplayNameAsc();
}
