package com.devpulse.team;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TeamRepository {

	private static final RowMapper<Team> TEAM_ROW_MAPPER = (resultSet, rowNumber) -> new Team(
			resultSet.getObject("id", UUID.class),
			resultSet.getString("name"),
			resultSet.getObject("created_at", OffsetDateTime.class).toInstant());

	private final JdbcTemplate jdbcTemplate;

	public TeamRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Team create(String name) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO devpulse.teams (id, name)
				VALUES (?, ?)
				RETURNING id, name, created_at
				""", TEAM_ROW_MAPPER, UUID.randomUUID(), name);
	}

	public List<TeamMembership> findByMemberId(UUID userId) {
		return jdbcTemplate.query("""
				SELECT team.id, team.name, team.created_at, membership.role
				FROM devpulse.teams team
				JOIN devpulse.team_memberships membership ON membership.team_id = team.id
				WHERE membership.user_id = ?
				ORDER BY team.created_at DESC, team.id
				""", (resultSet, rowNumber) -> new TeamMembership(resultSet.getObject("id", UUID.class),
						resultSet.getString("name"), resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
						resultSet.getString("role")), userId);
	}

	public Optional<String> findRole(UUID teamId, UUID userId) {
		return DataAccessUtils.optionalResult(jdbcTemplate.query("""
				SELECT role FROM devpulse.team_memberships WHERE team_id = ? AND user_id = ?
				""", (resultSet, rowNumber) -> resultSet.getString("role"), teamId, userId));
	}

	public List<TeamMember> findMembers(UUID teamId, UUID requesterId) {
		return jdbcTemplate.query("""
				SELECT account.id, account.name, account.email, membership.role, membership.joined_at
				FROM devpulse.team_memberships membership
				JOIN devpulse.users account ON account.id = membership.user_id
				WHERE membership.team_id = ? AND EXISTS (
					SELECT 1 FROM devpulse.team_memberships requester
					WHERE requester.team_id = membership.team_id AND requester.user_id = ?)
				ORDER BY CASE WHEN membership.role = 'OWNER' THEN 0 ELSE 1 END, lower(account.name), account.id
				""", (resultSet, rowNumber) -> new TeamMember(resultSet.getObject("id", UUID.class),
						resultSet.getString("name"), resultSet.getString("email"), resultSet.getString("role"),
						resultSet.getObject("joined_at", OffsetDateTime.class).toInstant()), teamId, requesterId);
	}

	public void lockTaskMembership(UUID teamId) {
		jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))",
				(resultSet, rowNumber) -> 0, teamId);
	}

	public void clearTaskAssignments(UUID teamId, UUID memberId) {
		jdbcTemplate.update("""
				UPDATE devpulse.tasks SET assignee_id = NULL, updated_at = clock_timestamp()
				WHERE assignee_id = ? AND project_id IN (SELECT id FROM devpulse.projects WHERE team_id = ?)
				""", memberId, teamId);
	}

	public boolean removeMember(UUID teamId, UUID memberId, UUID ownerId) {
		return jdbcTemplate.update("""
				DELETE FROM devpulse.team_memberships target
				WHERE target.team_id = ? AND target.user_id = ? AND target.role = 'MEMBER'
				AND EXISTS (SELECT 1 FROM devpulse.team_memberships owner
					WHERE owner.team_id = target.team_id AND owner.user_id = ? AND owner.role = 'OWNER')
				""", teamId, memberId, ownerId) == 1;
	}

	public void addOwner(UUID teamId, UUID userId) {
		jdbcTemplate.update("""
				INSERT INTO devpulse.team_memberships (team_id, user_id, role)
				VALUES (?, ?, 'OWNER')
				""", teamId, userId);
	}
}