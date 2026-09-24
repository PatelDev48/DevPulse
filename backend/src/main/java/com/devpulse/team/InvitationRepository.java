package com.devpulse.team;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepository {

	private final JdbcTemplate jdbcTemplate;

	public InvitationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public boolean isOwner(UUID teamId, UUID userId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM devpulse.team_memberships
				WHERE team_id = ? AND user_id = ? AND role = 'OWNER')
				""", Boolean.class, teamId, userId));
	}

	public CreatedInvitation create(UUID teamId, UUID createdBy, String tokenHash) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO devpulse.team_invitations (id, team_id, created_by, token_hash, expires_at)
				VALUES (?, ?, ?, ?, clock_timestamp() + INTERVAL '24 hours')
				RETURNING id, expires_at
				""", (resultSet, rowNumber) -> new CreatedInvitation(resultSet.getObject("id", UUID.class),
						resultSet.getObject("expires_at", OffsetDateTime.class).toInstant()),
				UUID.randomUUID(), teamId, createdBy, tokenHash);
	}

	public Optional<UUID> claim(String tokenHash, UUID userId) {
		return DataAccessUtils.optionalResult(jdbcTemplate.query("""
				UPDATE devpulse.team_invitations
				SET accepted_at = clock_timestamp(), accepted_by = ?
				WHERE token_hash = ? AND accepted_at IS NULL AND revoked_at IS NULL
				AND expires_at > clock_timestamp()
				RETURNING team_id
				""", (resultSet, rowNumber) -> resultSet.getObject("team_id", UUID.class), userId, tokenHash));
	}

	public boolean addMember(UUID teamId, UUID userId) {
		return jdbcTemplate.update("""
				INSERT INTO devpulse.team_memberships (team_id, user_id, role)
				VALUES (?, ?, 'MEMBER')
				ON CONFLICT (team_id, user_id) DO NOTHING
				""", teamId, userId) == 1;
	}

	public boolean revoke(UUID teamId, UUID invitationId) {
		return jdbcTemplate.update("""
				UPDATE devpulse.team_invitations SET revoked_at = clock_timestamp()
				WHERE id = ? AND team_id = ? AND accepted_at IS NULL AND revoked_at IS NULL
				AND expires_at > clock_timestamp()
				""", invitationId, teamId) == 1;
	}

	public record CreatedInvitation(UUID id, Instant expiresAt) {
	}
}