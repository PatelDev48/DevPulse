package com.devpulse.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

class InvitationServiceTests {

	private InvitationRepository repository;
	private InvitationService service;
	private final UUID teamId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		repository = mock(InvitationRepository.class);
		service = new InvitationService(repository);
	}

	@Test
	void issuesRandomTokenAndPersistsOnlyItsHash() throws Exception {
		when(repository.isOwner(teamId, userId)).thenReturn(true);
		var stored = new InvitationRepository.CreatedInvitation(UUID.randomUUID(), Instant.now().plusSeconds(86400));
		when(repository.create(eq(teamId), eq(userId), anyString())).thenReturn(stored);
		var invitation = service.createInvitation(teamId, userId);
		assertEquals(32, Base64.getUrlDecoder().decode(invitation.token()).length);
		assertEquals(stored.id(), invitation.id());
		assertEquals(stored.expiresAt(), invitation.expiresAt());
		String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(invitation.token().getBytes(StandardCharsets.US_ASCII)));
		verify(repository).create(teamId, userId, expectedHash);
		assertFalse(invitation.toString().contains(invitation.token()));
		assertNotEquals(invitation.token(), service.createInvitation(teamId, userId).token());
	}

	@Test
	void onlyOwnersCanCreateOrRevoke() {
		assertThrows(AccessDeniedException.class, () -> service.createInvitation(teamId, userId));
		assertThrows(AccessDeniedException.class, () -> service.revokeInvitation(teamId, UUID.randomUUID(), userId));
		verify(repository, org.mockito.Mockito.never()).create(eq(teamId), eq(userId), anyString());
		verify(repository, org.mockito.Mockito.never()).revoke(eq(teamId), org.mockito.ArgumentMatchers.any());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "short", "                                               ", "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" })
	void rejectsMalformedTokensBeforeDatabaseAccess(String token) {
		assertThrows(InvitationService.InvalidInvitationException.class, () -> service.acceptInvitation(token, userId));
		verifyNoInteractions(repository);
	}

	@Test
	void rejectsUnavailableInvitationWithoutAddingMember() {
		when(repository.claim(anyString(), eq(userId))).thenReturn(Optional.empty());
		assertThrows(InvitationService.InvalidInvitationException.class,
				() -> service.acceptInvitation("A".repeat(43), userId));
		verify(repository, org.mockito.Mockito.never()).addMember(org.mockito.ArgumentMatchers.any(), eq(userId));
	}

	@Test
	void acceptanceAddsMemberToClaimedTeam() {
		when(repository.claim(anyString(), eq(userId))).thenReturn(Optional.of(teamId));
		when(repository.addMember(teamId, userId)).thenReturn(true);
		assertEquals(teamId, service.acceptInvitation("A".repeat(43), userId));
		verify(repository).addMember(teamId, userId);
	}

	@Test
	void existingMembershipRaisesRollbackException() {
		when(repository.claim(anyString(), eq(userId))).thenReturn(Optional.of(teamId));
		assertThrows(InvitationService.AlreadyTeamMemberException.class,
				() -> service.acceptInvitation("A".repeat(43), userId));
	}

	@Test
	void ownerCanRevokeActiveInvitation() {
		UUID invitationId = UUID.randomUUID();
		when(repository.isOwner(teamId, userId)).thenReturn(true);
		when(repository.revoke(teamId, invitationId)).thenReturn(true);
		service.revokeInvitation(teamId, invitationId, userId);
		verify(repository).revoke(teamId, invitationId);
	}
}