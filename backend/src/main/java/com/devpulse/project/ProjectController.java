package com.devpulse.project;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams/{teamId}/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Project> createProject(@PathVariable UUID teamId, @RequestBody CreateProjectRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
				.body(projectService.createProject(teamId, request.name(), request.description(), UUID.fromString(jwt.getSubject())));
	}

	@GetMapping
	public ResponseEntity<List<Project>> listProjects(@PathVariable UUID teamId, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(projectService.listProjects(teamId, UUID.fromString(jwt.getSubject())));
	}

	public record CreateProjectRequest(String name, String description) {
	}

	@PutMapping(value = "/{projectId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Project> updateProject(@PathVariable UUID teamId, @PathVariable UUID projectId,
			@RequestBody CreateProjectRequest request, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(projectService.updateProject(
				teamId, projectId, request.name(), request.description(), UUID.fromString(jwt.getSubject())));
	}

	@PatchMapping(value = "/{projectId}/archive", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Project> archiveProject(@PathVariable UUID teamId, @PathVariable UUID projectId,
			@RequestBody ArchiveRequest request, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(projectService.setArchived(
				teamId, projectId, request.archived(), UUID.fromString(jwt.getSubject())));
	}

	public record ArchiveRequest(Boolean archived) { }
}
