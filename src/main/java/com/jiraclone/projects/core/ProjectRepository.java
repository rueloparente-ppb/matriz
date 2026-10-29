package com.jiraclone.projects.core;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectRepository extends JpaRepository<Project, Long> {

	Optional<Project> findByKey(String key);

	boolean existsByKey(String key);

	List<Project> findAllByOrderByKeyAsc();
}
