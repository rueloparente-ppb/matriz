package com.jiraclone.projects.core;

import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.ProjectsApi;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ProjectService implements ProjectsApi {

	private final ProjectRepository projects;

	ProjectService(ProjectRepository projects) {
		this.projects = projects;
	}

	/**
	 * Deliberately not {@code @Transactional}: the save commits (or fails) in its own transaction, so the unique
	 * violation can be handled here without a rollback-only outer transaction. The database constraint, not a
	 * pre-check, decides concurrent creates with the same key (PRJ-6).
	 */
	public CreateProjectResult create(String key, String name, long createdBy) {
		try {
			return new CreateProjectResult.Created(toRef(projects.saveAndFlush(new Project(key, name, createdBy))));
		} catch (DataIntegrityViolationException e) {
			if (projects.existsByKey(key)) {
				return new CreateProjectResult.KeyInUse();
			}
			throw e;
		}
	}

	@Override
	public Optional<ProjectRef> findByKey(String key) {
		return projects.findByKey(key).map(ProjectService::toRef);
	}

	/** All projects, sorted by key ascending. */
	public List<ProjectRef> list() {
		return projects.findAllByOrderByKeyAsc().stream().map(ProjectService::toRef).toList();
	}

	private static ProjectRef toRef(Project p) {
		return new ProjectRef(p.getId(), p.getKey(), p.getName());
	}
}
