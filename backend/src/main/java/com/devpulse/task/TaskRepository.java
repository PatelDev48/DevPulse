package com.devpulse.task;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRepository {

	private static final RowMapper<Task> TASK_ROW_MAPPER = (resultSet, rowNumber) -> new Task(
			resultSet.getObject("id", UUID.class), resultSet.getObject("project_id", UUID.class),
			resultSet.getString("title"), resultSet.getString("description"),
			Task.Status.valueOf(resultSet.getString("status")), Task.Priority.valueOf(resultSet.getString("priority")),
			resultSet.getObject("assignee_id", UUID.class), resultSet.getObject("created_by", UUID.class),
			resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
			resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());

	private final JdbcTemplate jdbcTemplate;

	public TaskRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public boolean projectBelongsToTeam(UUID projectId, UUID teamId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM devpulse.projects WHERE id = ? AND team_id = ?)",
				Boolean.class, projectId, teamId));
	}

	public boolean isProjectArchived(UUID projectId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
				"SELECT archived_at IS NOT NULL FROM devpulse.projects WHERE id = ?", Boolean.class, projectId));
	}

	public List<Task> findByProject(UUID teamId, UUID projectId, UUID userId) {
		return jdbcTemplate.query("""
				SELECT task.* FROM devpulse.tasks task
				JOIN devpulse.projects project ON project.id = task.project_id
				JOIN devpulse.team_memberships membership ON membership.team_id = project.team_id
				WHERE project.team_id = ? AND task.project_id = ? AND membership.user_id = ?
				ORDER BY task.created_at DESC, task.id
				""", TASK_ROW_MAPPER, teamId, projectId, userId);
	}

	public Task create(UUID projectId, String title, String description, Task.Priority priority, UUID assigneeId, UUID userId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO devpulse.tasks (id, project_id, title, description, priority, assignee_id, created_by)
				VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *
				""", TASK_ROW_MAPPER, UUID.randomUUID(), projectId, title, description, priority.name(), assigneeId, userId);
	}

	public Optional<Task> update(UUID projectId, UUID taskId, String title, String description, Task.Status status,
			Task.Priority priority, UUID assigneeId) {
		return DataAccessUtils.optionalResult(jdbcTemplate.query("""
				UPDATE devpulse.tasks SET title = ?, description = ?, status = ?, priority = ?, assignee_id = ?,
				updated_at = clock_timestamp() WHERE id = ? AND project_id = ? RETURNING *
				""", TASK_ROW_MAPPER, title, description, status.name(), priority.name(), assigneeId, taskId, projectId));
	}

	public Optional<Task> updateStatus(UUID projectId, UUID taskId, Task.Status status) {
		return DataAccessUtils.optionalResult(jdbcTemplate.query("""
				UPDATE devpulse.tasks SET status = ?, updated_at = clock_timestamp()
				WHERE id = ? AND project_id = ? RETURNING *
				""", TASK_ROW_MAPPER, status.name(), taskId, projectId));
	}
}
