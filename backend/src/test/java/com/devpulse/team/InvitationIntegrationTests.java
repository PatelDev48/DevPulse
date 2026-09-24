package com.devpulse.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import com.devpulse.user.UserRepository;

@SpringBootTest(properties = "JWT_SECRET_BASE64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
class InvitationIntegrationTests {

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private TeamService teamService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Flyway flyway;

	@Autowired
	private org.springframework.test.web.servlet.MockMvc mockMvc;

	@Autowired
	private tools.jackson.databind.json.JsonMapper jsonMapper;

	@Autowired
	private com.devpulse.auth.TokenService tokenService;

	private UUID ownerId;
	private UUID recipientId;
	private UUID otherRecipientId;
	private UUID teamId;

	@BeforeEach
	void setUp() {
		ownerId = createUser();
		recipientId = createUser();
		otherRecipientId = createUser();
		teamId = teamService.createTeam("Invitation integration test", ownerId).id();
	}

	@AfterEach
	void removeCommittedFixtures() {
		JdbcTemplate migrator = migrationJdbcTemplate();
		migrator.update("DELETE FROM devpulse.team_invitations WHERE team_id = ?", teamId);
		migrator.update("DELETE FROM devpulse.team_memberships WHERE team_id = ?", teamId);
		migrator.update("DELETE FROM devpulse.teams WHERE id = ?", teamId);
		migrator.update("DELETE FROM devpulse.users WHERE id IN (?, ?, ?)", ownerId, recipientId, otherRecipientId);
	}

	@Test
	void storesHashAndTwentyFourHourExpiryAndAcceptsOnce() {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		var stored = jdbcTemplate.queryForMap("SELECT * FROM devpulse.team_invitations WHERE id = ?", invitation.id());
		String hash = (String) stored.get("token_hash");
		assertTrue(hash.matches("[0-9a-f]{64}"));
		assertNotEquals(invitation.token(), hash);
		assertEquals(ownerId, stored.get("created_by"));
		var createdAt = jdbcTemplate.queryForObject(
				"SELECT created_at FROM devpulse.team_invitations WHERE id = ?",
				(resultSet, rowNumber) -> resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
				invitation.id());
		assertTrue(Math.abs(Duration.between(createdAt, invitation.expiresAt()).toSeconds() - 86400) <= 1);
		assertEquals(teamId, invitationService.acceptInvitation(invitation.token(), recipientId));
		assertEquals("MEMBER", jdbcTemplate.queryForObject(
				"SELECT role FROM devpulse.team_memberships WHERE team_id = ? AND user_id = ?",
				String.class, teamId, recipientId));
		assertEquals(recipientId, jdbcTemplate.queryForObject(
				"SELECT accepted_by FROM devpulse.team_invitations WHERE id = ?", UUID.class, invitation.id()));
		assertNotNull(jdbcTemplate.queryForObject("SELECT accepted_at FROM devpulse.team_invitations WHERE id = ?",
				OffsetDateTime.class, invitation.id()));
		assertThrows(InvitationService.InvalidInvitationException.class,
				() -> invitationService.acceptInvitation(invitation.token(), otherRecipientId));
		assertEquals(2, membershipCount());
	}

	@Test
	void membersAndOutsidersCannotManageInvitations() {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		invitationService.acceptInvitation(invitation.token(), recipientId);
		var active = invitationService.createInvitation(teamId, ownerId);
		for (UUID userId : List.of(recipientId, otherRecipientId)) {
			assertThrows(AccessDeniedException.class, () -> invitationService.createInvitation(teamId, userId));
			assertThrows(AccessDeniedException.class,
					() -> invitationService.revokeInvitation(teamId, active.id(), userId));
		}
		assertEquals(teamId, invitationService.acceptInvitation(active.token(), otherRecipientId));
	}

	@Test
	void revokedInvitationCannotBeAccepted() {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		invitationService.revokeInvitation(teamId, invitation.id(), ownerId);
		assertThrows(InvitationService.InvalidInvitationException.class,
				() -> invitationService.acceptInvitation(invitation.token(), recipientId));
		assertEquals(1, membershipCount());
	}

	@Test
	void expiredAndUnknownInvitationsCannotBeAccepted() {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		migrationJdbcTemplate().update("""
				UPDATE devpulse.team_invitations
				SET created_at = clock_timestamp() - INTERVAL '25 hours',
				expires_at = clock_timestamp() - INTERVAL '1 hour' WHERE id = ?
				""", invitation.id());
		for (String token : List.of(invitation.token(), "A".repeat(43))) {
			assertThrows(InvitationService.InvalidInvitationException.class,
					() -> invitationService.acceptInvitation(token, recipientId));
		}
		assertEquals(1, membershipCount());
	}

	@Test
	void existingMemberDoesNotConsumeInvitationOrChangeRole() {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		assertThrows(InvitationService.AlreadyTeamMemberException.class,
				() -> invitationService.acceptInvitation(invitation.token(), ownerId));
		assertNull(jdbcTemplate.queryForObject("SELECT accepted_at FROM devpulse.team_invitations WHERE id = ?",
				OffsetDateTime.class, invitation.id()));
		assertEquals("OWNER", jdbcTemplate.queryForObject(
				"SELECT role FROM devpulse.team_memberships WHERE team_id = ? AND user_id = ?",
				String.class, teamId, ownerId));
		assertEquals(teamId, invitationService.acceptInvitation(invitation.token(), recipientId));
	}

	@Test
	void missingUserDoesNotConsumeInvitation() {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		assertThrows(DataIntegrityViolationException.class,
				() -> invitationService.acceptInvitation(invitation.token(), UUID.randomUUID()));
		assertEquals(teamId, invitationService.acceptInvitation(invitation.token(), recipientId));
	}

	@Test
	void concurrentAcceptancesHaveExactlyOneWinner() throws Exception {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Callable<String> first = () -> acceptAfterBarrier(barrier, invitation.token(), recipientId);
			Callable<String> second = () -> acceptAfterBarrier(barrier, invitation.token(), otherRecipientId);
			var results = executor.invokeAll(List.of(first, second), 10, TimeUnit.SECONDS);
			assertEquals(java.util.Set.of("accepted", "rejected"),
					java.util.Set.of(results.get(0).get(), results.get(1).get()));
		}
		assertEquals(2, membershipCount());
	}

	@Test
	void acceptanceAndRevocationCannotBothSucceed() throws Exception {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		var barrier = new CyclicBarrier(2);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Callable<String> acceptance = () -> acceptAfterBarrier(barrier, invitation.token(), recipientId);
			Callable<String> revocation = () -> {
				barrier.await(5, TimeUnit.SECONDS);
				try {
					invitationService.revokeInvitation(teamId, invitation.id(), ownerId);
					return "revoked";
				} catch (InvitationService.InvalidInvitationException exception) {
					return "rejected";
				}
			};
			var results = executor.invokeAll(List.of(acceptance, revocation), 10, TimeUnit.SECONDS);
			String acceptResult = results.get(0).get();
			String revokeResult = results.get(1).get();
			assertTrue((acceptResult.equals("accepted") && revokeResult.equals("rejected"))
					|| (acceptResult.equals("rejected") && revokeResult.equals("revoked")));
			assertEquals(acceptResult.equals("accepted") ? 2 : 1, membershipCount());
		}
	}

	@Test
	void applicationCannotRewriteTokenOrExtendExpiry() {
		for (String column : List.of("id", "team_id", "created_by", "token_hash", "created_at", "expires_at")) {
			assertFalse(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
					"SELECT has_column_privilege(current_user, 'devpulse.team_invitations', ?, 'UPDATE')",
					Boolean.class, column)));
		}
		for (String column : List.of("accepted_at", "accepted_by", "revoked_at")) {
			assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
					"SELECT has_column_privilege(current_user, 'devpulse.team_invitations', ?, 'UPDATE')",
					Boolean.class, column));
		}
		assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
				"SELECT has_table_privilege(current_user, 'devpulse.team_invitations', 'DELETE')", Boolean.class));
	}

	@Test
	void httpInvitationAcceptanceAddsAuthenticatedMemberAndRejectsReuse() throws Exception {
		var response = mockMvc.perform(post("/api/teams/{teamId}/invitations", teamId)
				.header("Authorization", authorization(ownerId)))
				.andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse();
		var invitation = jsonMapper.readTree(response.getContentAsString());
		String body = jsonMapper.writeValueAsString(java.util.Map.of("token", invitation.get("token").asText(),
				"userId", otherRecipientId.toString(), "role", "OWNER"));
		mockMvc.perform(post("/api/invitations/accept").header("Authorization", authorization(recipientId))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andExpect(jsonPath("$.teamId").value(teamId.toString()));
		assertEquals("MEMBER", jdbcTemplate.queryForObject(
				"SELECT role FROM devpulse.team_memberships WHERE team_id = ? AND user_id = ?",
				String.class, teamId, recipientId));
		mockMvc.perform(get("/api/teams").header("Authorization", authorization(recipientId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(teamId.toString()))
				.andExpect(jsonPath("$[0].role").value("MEMBER"));
		mockMvc.perform(post("/api/invitations/accept").header("Authorization", authorization(otherRecipientId))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/teams").header("Authorization", authorization(otherRecipientId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		assertEquals(2, membershipCount());
	}

	@Test
	void httpRevocationRequiresOwnerAndPreventsAcceptance() throws Exception {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		mockMvc.perform(post("/api/teams/{teamId}/invitations", teamId)
				.header("Authorization", authorization(recipientId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/teams/{teamId}/invitations/{invitationId}", teamId, invitation.id())
				.header("Authorization", authorization(recipientId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/teams/{teamId}/invitations/{invitationId}", teamId, invitation.id())
				.header("Authorization", authorization(ownerId)))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/invitations/accept").header("Authorization", authorization(recipientId))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(java.util.Map.of("token", invitation.token()))))
				.andExpect(status().isBadRequest());
		assertEquals(1, membershipCount());
	}

	@Test
	void httpExistingMemberConflictPreservesInvitation() throws Exception {
		var invitation = invitationService.createInvitation(teamId, ownerId);
		String body = jsonMapper.writeValueAsString(java.util.Map.of("token", invitation.token()));
		mockMvc.perform(post("/api/invitations/accept").header("Authorization", authorization(ownerId))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/invitations/accept").header("Authorization", authorization(recipientId))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());
		assertEquals(2, membershipCount());
	}

	private String authorization(UUID userId) {
		return "Bearer " + tokenService.issue(new com.devpulse.auth.LoginService.LoginResult(userId,
				"Invitation test", "invitation@example.test", java.time.Instant.now())).token();
	}

	private String acceptAfterBarrier(CyclicBarrier barrier, String token, UUID userId) throws Exception {
		barrier.await(5, TimeUnit.SECONDS);
		try {
			invitationService.acceptInvitation(token, userId);
			return "accepted";
		} catch (InvitationService.InvalidInvitationException exception) {
			return "rejected";
		}
	}

	private UUID createUser() {
		return userRepository.create("Invitation test", UUID.randomUUID() + "@example.test", "test-only-placeholder").id();
	}

	private int membershipCount() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM devpulse.team_memberships WHERE team_id = ?",
				Integer.class, teamId);
	}

	private JdbcTemplate migrationJdbcTemplate() {
		return new JdbcTemplate(flyway.getConfiguration().getDataSource());
	}
}