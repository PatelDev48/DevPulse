package com.devpulse.task;

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
public class TaskService {

	private final TaskRepository taskRepository;
	private final TeamRepository teamRepository;
	private final Validator validator;

	public TaskService(TaskRepository taskRepository, TeamRepository teamRepository, Validator validator) {
		this.taskRepository = taskRepository;
		this.teamRepository = teamRepository;
		this.validator = validator;
	}

	public List<Task> listTasks(UUID teamId, UUID projectId, UUID userId) {
		requireAccess(teamId, projectId, userId);
		return taskRepository.findByProject(teamId, projectId, userId);
	}

	@Transactional
	public Task createTask(UUID teamId, UUID projectId, String title, String description, Task.Priority priority,
			UUID assigneeId, UUID userId) {
		var input = normalizedInput(title, description, Task.Status.TODO, priority == null ? Task.Priority.MEDIUM : priority);
		teamRepository.lockTaskMembership(teamId);
		requireAccess(teamId, projectId, userId);
		requireActiveProject(projectId);
		requireAssignee(teamId, assigneeId);
		return taskRepository.create(projectId, input.title(), input.description(), input.priority(), assigneeId, userId);
	}

	@Transactional
	public Task updateTask(UUID teamId, UUID projectId, UUID taskId, String title, String description, Task.Status status,
			Task.Priority priority, UUID assigneeId, UUID userId) {
		var input = normalizedInput(title, description, status, priority);
		teamRepository.lockTaskMembership(teamId);
		requireAccess(teamId, projectId, userId);
		requireActiveProject(projectId);
		requireAssignee(teamId, assigneeId);
		return taskRepository.update(projectId, taskId, input.title(), input.description(), input.status(), input.priority(), assigneeId)
				.orElseThrow(MissingTaskException::new);
	}

	@Transactional
	public Task updateStatus(UUID teamId, UUID projectId, UUID taskId, Task.Status status, UUID userId) {
		if (status == null) throw new IllegalArgumentException("A task status is required.");
		teamRepository.lockTaskMembership(teamId);
		requireAccess(teamId, projectId, userId);
		requireActiveProject(projectId);
		return taskRepository.updateStatus(projectId, taskId, status).orElseThrow(MissingTaskException::new);
	}

	private TaskInput normalizedInput(String title, String description, Task.Status status, Task.Priority priority) {
		var input = new TaskInput(title == null ? null : title.strip(), description == null ? null : description.strip(), status, priority);
		if (!validator.validate(input).isEmpty()) throw new IllegalArgumentException("Invalid task details.");
		return input;
	}

	private void requireAccess(UUID teamId, UUID projectId, UUID userId) {
		if (teamId == null || projectId == null || userId == null) throw new IllegalArgumentException("Team, project and user IDs are required.");
		if (teamRepository.findRole(teamId, userId).isEmpty()) throw new AccessDeniedException("Team membership is required.");
		if (!taskRepository.projectBelongsToTeam(projectId, teamId)) throw new MissingProjectException();
	}

	private void requireAssignee(UUID teamId, UUID assigneeId) {
		if (assigneeId != null && teamRepository.findRole(teamId, assigneeId).isEmpty()) throw new InvalidAssigneeException();
	}

	private void requireActiveProject(UUID projectId) {
		if (taskRepository.isProjectArchived(projectId)) throw new com.devpulse.project.ProjectService.ArchivedProjectException();
	}

	private record TaskInput(@NotBlank @Size(max = 200) String title, @Size(max = 5000) String description,
			@NotNull Task.Status status, @NotNull Task.Priority priority) {
	}

	public static class MissingProjectException extends RuntimeException { }
	public static class MissingTaskException extends RuntimeException { }
	public static class InvalidAssigneeException extends RuntimeException { }
}
