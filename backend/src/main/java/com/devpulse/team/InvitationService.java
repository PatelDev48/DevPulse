package com.devpulse.team;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

	private final InvitationRepository invitationRepository;
	private final SecureRandom secureRandom = new SecureRandom();

	public InvitationService(InvitationRepository invitationRepository) {
		this.invitationRepository = invitationRepository;
	}

	@Transactional
	public IssuedInvitation createInvitation(UUID teamId, UUID ownerId) {
		requireOwner(teamId, ownerId);
		byte[] randomBytes = new byte[32];
		secureRandom.nextBytes(randomBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		var invitation = invitationRepository.create(teamId, ownerId, hash(token));
		return new IssuedInvitation(invitation.id(), token, invitation.expiresAt());
	}

	@Transactional
	public UUID acceptInvitation(String token, UUID userId) {
		requireId(userId);
		if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
			throw new InvalidInvitationException();
		}
		UUID teamId = invitationRepository.claim(hash(token), userId)
				.orElseThrow(InvalidInvitationException::new);
		if (!invitationRepository.addMember(teamId, userId)) {
			throw new AlreadyTeamMemberException();
		}
		return teamId;
	}

	@Transactional
	public void revokeInvitation(UUID teamId, UUID invitationId, UUID ownerId) {
		requireId(invitationId);
		requireOwner(teamId, ownerId);
		if (!invitationRepository.revoke(teamId, invitationId)) {
			throw new InvalidInvitationException();
		}
	}

	private void requireOwner(UUID teamId, UUID userId) {
		requireId(teamId);
		requireId(userId);
		if (!invitationRepository.isOwner(teamId, userId)) {
			throw new AccessDeniedException("Only the team owner can manage invitations.");
		}
	}

	private static void requireId(UUID id) {
		if (id == null) {
			throw new IllegalArgumentException("An ID is required.");
		}
	}

	private static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.US_ASCII)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	public record IssuedInvitation(UUID id, String token, Instant expiresAt) {
		@Override
		public String toString() {
			return "IssuedInvitation[id=" + id + ", token=redacted, expiresAt=" + expiresAt + "]";
		}
	}

	public static class InvalidInvitationException extends RuntimeException {
		public InvalidInvitationException() {
			super("Invitation is invalid, expired, revoked, or already used.");
		}
	}

	public static class AlreadyTeamMemberException extends RuntimeException {
		public AlreadyTeamMemberException() {
			super("You are already a member of this team.");
		}
	}
}