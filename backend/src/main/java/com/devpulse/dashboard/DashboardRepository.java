package com.devpulse.dashboard;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {
	private final JdbcTemplate jdbcTemplate;
	public DashboardRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	public record TaskGroup(UUID projectId, UUID assigneeId, String status, String priority, long count, long stale) { }

	public List<TaskGroup> groups(UUID teamId, UUID userId) {
		return jdbcTemplate.query("""
				SELECT task.project_id, task.assignee_id, task.status, task.priority, count(*) AS count,
				count(*) FILTER (WHERE task.status <> 'DONE' AND task.updated_at < CURRENT_TIMESTAMP - INTERVAL '7 days') AS stale
				FROM devpulse.tasks task
				JOIN devpulse.projects project ON project.id = task.project_id
				JOIN devpulse.team_memberships membership ON membership.team_id = project.team_id
				WHERE project.team_id = ? AND membership.user_id = ? AND project.archived_at IS NULL
				GROUP BY task.project_id, task.assignee_id, task.status, task.priority
				""", (result, row) -> new TaskGroup(result.getObject("project_id", UUID.class), result.getObject("assignee_id", UUID.class),
				result.getString("status"), result.getString("priority"), result.getLong("count"), result.getLong("stale")), teamId, userId);
	}

	public List<Dashboard.FocusTask> personalTasks(UUID teamId, UUID projectId, UUID userId) {
		return jdbcTemplate.query("""
				SELECT task.id, task.project_id, project.name AS project_name, task.title, task.status, task.priority, task.updated_at
				FROM devpulse.tasks task JOIN devpulse.projects project ON project.id = task.project_id
				JOIN devpulse.team_memberships membership ON membership.team_id = project.team_id
				WHERE project.team_id = ? AND membership.user_id = ? AND task.assignee_id = ?
				AND project.archived_at IS NULL AND task.status <> 'DONE'
				AND (CAST(? AS uuid) IS NULL OR project.id = ?)
				ORDER BY CASE task.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, task.updated_at, task.id
				LIMIT 20
				""", (result, row) -> new Dashboard.FocusTask(result.getObject("id", UUID.class), result.getObject("project_id", UUID.class),
				result.getString("project_name"), result.getString("title"), result.getString("status"), result.getString("priority"),
				result.getObject("updated_at", OffsetDateTime.class).toInstant()), teamId, userId, userId, projectId, projectId);
	}
}