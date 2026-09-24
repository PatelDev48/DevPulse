package com.devpulse.project;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devpulse.team.TeamRepository;

@Service
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final TeamRepository teamRepository;
	private final Validator validator;

	public ProjectService(ProjectRepository projectRepository, TeamRepository teamRepository, Validator validator) {
		this.projectRepository = projectRepository;
		this.teamRepository = teamRepository;
		this.validator = validator;
	}

	@Transactional
	public Project createProject(UUID teamId, String name, String description, UUID userId) {
		String normalizedName = name == null ? null : name.strip();
		String normalizedDescription = description == null ? null : description.strip();
		if (!validator.validate(new CreateProjectInput(teamId, normalizedName, normalizedDescription, userId)).isEmpty()) {
			throw new IllegalArgumentException("Invalid project details.");
		}
		if (teamRepository.findRole(teamId, userId).filter("OWNER"::equals).isEmpty()) {
			throw new AccessDeniedException("Only the team owner can create projects.");
		}
		return projectRepository.create(teamId, normalizedName, normalizedDescription, userId)
				.orElseThrow(() -> new AccessDeniedException("Only the team owner can create projects."));
	}

	public List<Project> listProjects(UUID teamId, UUID userId) {
		if (teamId == null || userId == null) {
			throw new IllegalArgumentException("Team and user IDs are required.");
		}
		if (teamRepository.findRole(teamId, userId).isEmpty()) {
			throw new AccessDeniedException("Team membership is required.");
		}
		return projectRepository.findByTeamId(teamId, userId);
	}

	private record CreateProjectInput(@NotNull UUID teamId, @NotBlank @Size(max = 100) String name,
			@Size(max = 2000) String description, @NotNull UUID userId) {
	}

	@Transactional
	public Project updateProject(UUID teamId, UUID projectId, String name, String description, UUID userId) {
		String normalizedName = name == null ? null : name.strip();
		String normalizedDescription = description == null ? null : description.strip();
		if (!validator.validate(new CreateProjectInput(teamId, normalizedName, normalizedDescription, userId)).isEmpty()) {
			throw new IllegalArgumentException("Invalid project details.");
		}
		Project project = requireManagedProject(teamId, projectId, userId);
		if (project.archivedAt() != null) throw new ArchivedProjectException();
		return projectRepository.update(teamId, projectId, normalizedName, normalizedDescription);
	}

	@Transactional
	public Project setArchived(UUID teamId, UUID projectId, Boolean archived, UUID userId) {
		if (archived == null) throw new IllegalArgumentException("Archive state is required.");
		requireManagedProject(teamId, projectId, userId);
		return projectRepository.setArchived(teamId, projectId, archived);
	}

	private Project requireManagedProject(UUID teamId, UUID projectId, UUID userId) {
		teamRepository.lockTaskMembership(teamId);
		if (teamRepository.findRole(teamId, userId).filter("OWNER"::equals).isEmpty()) {
			throw new AccessDeniedException("Only the team owner can manage projects.");
		}
		return projectRepository.find(teamId, projectId).orElseThrow(MissingProjectException::new);
	}

	public static class MissingProjectException extends RuntimeException { }
	public static class ArchivedProjectException extends RuntimeException { }
}
