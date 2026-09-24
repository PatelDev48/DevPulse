package com.devpulse.project;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

	private static final RowMapper<Project> PROJECT_ROW_MAPPER = (resultSet, rowNumber) -> new Project(
			resultSet.getObject("id", UUID.class), resultSet.getObject("team_id", UUID.class),
			resultSet.getString("name"), resultSet.getString("description"),
			resultSet.getObject("created_by", UUID.class),
			resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
			resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
			resultSet.getObject("archived_at", OffsetDateTime.class) == null ? null
					: resultSet.getObject("archived_at", OffsetDateTime.class).toInstant());

	private final JdbcTemplate jdbcTemplate;

	public ProjectRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<Project> create(UUID teamId, String name, String description, UUID ownerId) {
		return DataAccessUtils.optionalResult(jdbcTemplate.query("""
				INSERT INTO devpulse.projects (id, team_id, name, description, created_by)
				SELECT ?, membership.team_id, ?, ?, membership.user_id
				FROM devpulse.team_memberships membership
				WHERE membership.team_id = ? AND membership.user_id = ? AND membership.role = 'OWNER'
				RETURNING *
				""", PROJECT_ROW_MAPPER, UUID.randomUUID(), name, description, teamId, ownerId));
	}

	public List<Project> findByTeamId(UUID teamId, UUID userId) {
		return jdbcTemplate.query("""
				SELECT project.*
				FROM devpulse.projects project
				JOIN devpulse.team_memberships membership ON membership.team_id = project.team_id
				WHERE project.team_id = ? AND membership.user_id = ?
				ORDER BY project.created_at DESC, project.id
				""", PROJECT_ROW_MAPPER, teamId, userId);
	}

	public Optional<Project> find(UUID teamId, UUID projectId) {
		return DataAccessUtils.optionalResult(jdbcTemplate.query(
				"SELECT * FROM devpulse.projects WHERE team_id = ? AND id = ?", PROJECT_ROW_MAPPER, teamId, projectId));
	}

	public Project update(UUID teamId, UUID projectId, String name, String description) {
		return jdbcTemplate.queryForObject("""
				UPDATE devpulse.projects SET name = ?, description = ?, updated_at = clock_timestamp()
				WHERE team_id = ? AND id = ? RETURNING *
				""", PROJECT_ROW_MAPPER, name, description, teamId, projectId);
	}

	public Project setArchived(UUID teamId, UUID projectId, boolean archived) {
		return jdbcTemplate.queryForObject("""
				UPDATE devpulse.projects SET archived_at = CASE WHEN ? THEN COALESCE(archived_at, clock_timestamp()) ELSE NULL END,
				updated_at = clock_timestamp() WHERE team_id = ? AND id = ? RETURNING *
				""", PROJECT_ROW_MAPPER, archived, teamId, projectId);
	}
}
