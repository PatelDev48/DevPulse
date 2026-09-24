package com.devpulse.team;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

	private final TeamService teamService;

	public TeamController(TeamService teamService) {
		this.teamService = teamService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public Team createTeam(@RequestBody CreateTeamRequest request, @AuthenticationPrincipal Jwt jwt) {
		return teamService.createTeam(request.name(), UUID.fromString(jwt.getSubject()));
	}

	@GetMapping
	public List<TeamMembership> listTeams(@AuthenticationPrincipal Jwt jwt) {
		return teamService.listTeams(UUID.fromString(jwt.getSubject()));
	}

	@GetMapping("/{teamId}/members")
	public ResponseEntity<List<TeamMember>> listMembers(@PathVariable UUID teamId, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(teamService.listMembers(teamId, UUID.fromString(jwt.getSubject())));
	}

	@DeleteMapping("/{teamId}/members/{memberId}")
	public ResponseEntity<Void> revokeMember(@PathVariable UUID teamId, @PathVariable UUID memberId,
			@AuthenticationPrincipal Jwt jwt) {
		teamService.revokeMember(teamId, memberId, UUID.fromString(jwt.getSubject()));
		return ResponseEntity.noContent().build();
	}

	public record CreateTeamRequest(String name) {
	}
}