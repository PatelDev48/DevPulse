package com.devpulse.task;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams/{teamId}/projects/{projectId}/tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@GetMapping
	public ResponseEntity<List<Task>> listTasks(@PathVariable UUID teamId, @PathVariable UUID projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(taskService.listTasks(teamId, projectId, UUID.fromString(jwt.getSubject())));
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Task> createTask(@PathVariable UUID teamId, @PathVariable UUID projectId,
			@RequestBody CreateTaskRequest request, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
				.body(taskService.createTask(teamId, projectId, request.title(), request.description(), request.priority(),
						request.assigneeId(), UUID.fromString(jwt.getSubject())));
	}

	@PutMapping(path = "/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Task> updateTask(@PathVariable UUID teamId, @PathVariable UUID projectId, @PathVariable UUID taskId,
			@RequestBody UpdateTaskRequest request, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(taskService.updateTask(teamId, projectId, taskId, request.title(), request.description(), request.status(),
						request.priority(), request.assigneeId(), UUID.fromString(jwt.getSubject())));
	}

	@PatchMapping(path = "/{taskId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Task> updateStatus(@PathVariable UUID teamId, @PathVariable UUID projectId, @PathVariable UUID taskId,
			@RequestBody UpdateStatusRequest request, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(taskService.updateStatus(teamId, projectId, taskId, request.status(), UUID.fromString(jwt.getSubject())));
	}

	public record CreateTaskRequest(String title, String description, Task.Priority priority, UUID assigneeId) { }
	public record UpdateTaskRequest(String title, String description, Task.Status status, Task.Priority priority, UUID assigneeId) { }
	public record UpdateStatusRequest(Task.Status status) { }
}
