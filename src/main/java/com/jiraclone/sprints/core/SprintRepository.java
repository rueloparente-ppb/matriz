package com.jiraclone.sprints.core;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SprintRepository extends JpaRepository<Sprint, Long> {
	Optional<Sprint> findByIdAndProjectId(long id, long projectId);
	List<Sprint> findByProjectIdOrderByIdAsc(long projectId);
	boolean existsByProjectIdAndNameIgnoreCase(long projectId, String name);
}
