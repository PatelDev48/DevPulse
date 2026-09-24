package com.devpulse.dashboard;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.devpulse.project.ProjectRepository;
import com.devpulse.project.ProjectService;
import com.devpulse.team.TeamRepository;

@Service
public class DashboardService {
	private final DashboardRepository repository;
	private final ProjectRepository projects;
	private final TeamRepository teams;
	public DashboardService(DashboardRepository repository, ProjectRepository projects, TeamRepository teams) {
		this.repository = repository; this.projects = projects; this.teams = teams;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public Dashboard getDashboard(UUID teamId, UUID projectId, UUID userId) {
		if (teams.findRole(teamId, userId).isEmpty()) throw new AccessDeniedException("Team membership is required.");
		var allProjects = projects.findByTeamId(teamId, userId);
		if (projectId != null) {
			var project = allProjects.stream().filter(item -> item.id().equals(projectId)).findFirst()
					.orElseThrow(ProjectService.MissingProjectException::new);
			if (project.archivedAt() != null) throw new ProjectService.ArchivedProjectException();
		}
		var groups = repository.groups(teamId, userId);
		var selected = groups.stream().filter(group -> projectId == null || group.projectId().equals(projectId)).toList();
		var personal = selected.stream().filter(group -> userId.equals(group.assigneeId())).toList();
		var progress = allProjects.stream().filter(project -> project.archivedAt() == null).map(project -> {
			var overview = summarize(groups.stream().filter(group -> group.projectId().equals(project.id())).toList());
			return new Dashboard.ProjectProgress(project.id(), project.name(), overview.total(), overview.completed());
		}).toList();
		var workload = teams.findMembers(teamId, userId).stream().map(member -> {
			var overview = summarize(selected.stream().filter(group -> member.userId().equals(group.assigneeId())).toList());
			return new Dashboard.MemberWorkload(member.userId(), member.name(), overview.open(),
					overview.byStatus().get("IN_PROGRESS"), overview.byStatus().get("IN_REVIEW"), overview.completed());
		}).sorted(java.util.Comparator.comparingLong(Dashboard.MemberWorkload::open).reversed()
				.thenComparing(Dashboard.MemberWorkload::name).thenComparing(Dashboard.MemberWorkload::userId)).toList();
		return new Dashboard(teamId, projectId, Instant.now(), (int) allProjects.stream().filter(project -> project.archivedAt() != null).count(),
				summarize(selected), summarize(personal), progress, workload, repository.personalTasks(teamId, projectId, userId));
	}

	private Dashboard.Overview summarize(List<DashboardRepository.TaskGroup> groups) {
		var statuses = new LinkedHashMap<String, Long>();
		for (String status : List.of("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE")) statuses.put(status, 0L);
		var priorities = new LinkedHashMap<String, Long>();
		for (String priority : List.of("HIGH", "MEDIUM", "LOW")) priorities.put(priority, 0L);
		long unassigned = 0, stale = 0;
		for (var group : groups) {
			statuses.merge(group.status(), group.count(), Long::sum);
			if (!group.status().equals("DONE")) {
				priorities.merge(group.priority(), group.count(), Long::sum);
				if (group.assigneeId() == null) unassigned += group.count();
				stale += group.stale();
			}
		}
		long total = statuses.values().stream().mapToLong(Long::longValue).sum();
		return new Dashboard.Overview(total, total - statuses.get("DONE"), statuses.get("DONE"), priorities.get("HIGH"),
				unassigned, stale, statuses, priorities);
	}
}