package com.devpulse.dashboard;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
	private final DashboardService service;
	public DashboardController(DashboardService service) { this.service = service; }

	@GetMapping("/api/teams/{teamId}/dashboard")
	public ResponseEntity<Dashboard> dashboard(@PathVariable UUID teamId, @RequestParam(required = false) UUID projectId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(service.getDashboard(teamId, projectId, UUID.fromString(jwt.getSubject())));
	}
}