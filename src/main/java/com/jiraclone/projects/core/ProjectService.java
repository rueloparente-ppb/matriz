package com.jiraclone.projects.core;

import com.jiraclone.common.NotFoundException;
import com.jiraclone.projects.ProjectAccess;
import com.jiraclone.projects.ProjectMembership;
import com.jiraclone.projects.ProjectRef;
import com.jiraclone.projects.ProjectsApi;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService implements ProjectsApi {

	private final ProjectRepository projects;
	private final ObjectProvider<ProjectMembership> membership;

	ProjectService(ProjectRepository projects, ObjectProvider<ProjectMembership> membership) {
		this.projects = projects;
		this.membership = membership;
	}

	/**
	 * Deliberately not {@code @Transactional}: the save commits (or fails) in its own transaction, so the unique
	 * violation can be handled here without a rollback-only outer transaction. The database constraint, not a
	 * pre-check, decides concurrent creates with the same key (PRJ-6).
	 */
	public CreateProjectResult create(String key, String name, String description, long createdBy) {
		try {
			Project project = new Project(key, name, createdBy);
			project.edit(name, description == null || description.isEmpty() ? null : description);
			return new CreateProjectResult.Created(toRef(projects.saveAndFlush(project)));
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

	@Override
	public Optional<ProjectAccess> findAccessible(String key, long userId) {
		return projects.findByKey(key).flatMap(p -> {
			if (p.getCreatedBy() == userId) {
				return Optional.of(new ProjectAccess(toRef(p), ProjectAccess.Role.OWNER));
			}
			return membership.getObject().isMember(p.getId(), userId)
					? Optional.of(new ProjectAccess(toRef(p), ProjectAccess.Role.MEMBER)) : Optional.empty();
		});
	}

	/** All projects, sorted by key ascending. */
	public List<ProjectRef> list() {
		return projects.findAllByOrderByKeyAsc().stream().map(ProjectService::toRef).toList();
	}

	public List<ProjectRef> listAccessible(long userId) {
		var ids = membership.getObject().projectIds(userId);
		return projects.findAllByOrderByKeyAsc().stream()
				.filter(p -> p.getCreatedBy() == userId || ids.contains(p.getId()))
				.map(ProjectService::toRef).toList();
	}

	@Transactional
	public void edit(String key, String name, String description) {
		Project project = projects.findByKey(key).orElseThrow(() -> new NotFoundException("Project " + key));
		project.edit(name, description.isEmpty() ? null : description);
	}

	@Transactional
	public void delete(String key) {
		Project project = projects.findByKey(key).orElseThrow(() -> new NotFoundException("Project " + key));
		projects.delete(project);
	}

	private static ProjectRef toRef(Project p) {
		return new ProjectRef(p.getId(), p.getKey(), p.getName(), p.getDescription() == null ? "" : p.getDescription());
	}
}
