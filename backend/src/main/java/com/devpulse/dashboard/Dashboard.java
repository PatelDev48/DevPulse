package com.devpulse.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Dashboard(UUID teamId, UUID projectId, Instant generatedAt, int archivedProjects,
		Overview team, Overview personal, List<ProjectProgress> projects, List<MemberWorkload> workload,
		List<FocusTask> myOpenTasks) {
	public record Overview(long total, long open, long completed, long highPriorityOpen, long unassignedOpen,
			long staleOpen, Map<String, Long> byStatus, Map<String, Long> openByPriority) { }
	public record ProjectProgress(UUID id, String name, long total, long completed) { }
	public record MemberWorkload(UUID userId, String name, long open, long inProgress, long inReview, long completed) { }
	public record FocusTask(UUID id, UUID projectId, String projectName, String title, String status,
			String priority, Instant updatedAt) { }
}