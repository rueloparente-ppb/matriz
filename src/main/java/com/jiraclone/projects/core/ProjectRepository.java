package com.jiraclone.projects.core;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

	Optional<Project> findByKey(String key);

	boolean existsByKey(String key);

	List<Project> findAllByOrderByKeyAsc();
}
