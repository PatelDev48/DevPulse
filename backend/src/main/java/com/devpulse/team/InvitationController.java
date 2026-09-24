package com.devpulse.team;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InvitationController {

	private final InvitationService invitationService;

	public InvitationController(InvitationService invitationService) {
		this.invitationService = invitationService;
	}

	@PostMapping("/teams/{teamId}/invitations")
	public ResponseEntity<InvitationService.IssuedInvitation> createInvitation(
			@PathVariable UUID teamId, @AuthenticationPrincipal Jwt jwt) {
		var invitation = invitationService.createInvitation(teamId, UUID.fromString(jwt.getSubject()));
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(invitation);
	}

	@DeleteMapping("/teams/{teamId}/invitations/{invitationId}")
	public ResponseEntity<Void> revokeInvitation(@PathVariable UUID teamId, @PathVariable UUID invitationId,
			@AuthenticationPrincipal Jwt jwt) {
		invitationService.revokeInvitation(teamId, invitationId, UUID.fromString(jwt.getSubject()));
		return ResponseEntity.noContent().build();
	}

	@PostMapping(value = "/invitations/accept", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AcceptedInvitation> acceptInvitation(@RequestBody AcceptInvitationRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		UUID teamId = invitationService.acceptInvitation(request.token(), UUID.fromString(jwt.getSubject()));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AcceptedInvitation(teamId));
	}

	public record AcceptedInvitation(UUID teamId) {
	}

	public record AcceptInvitationRequest(String token) {
		@Override
		public String toString() {
			return "AcceptInvitationRequest[token=redacted]";
		}
	}
}