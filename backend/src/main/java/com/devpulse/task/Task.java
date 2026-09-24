package com.devpulse.task;

import java.time.Instant;
import java.util.UUID;

public record Task(UUID id, UUID projectId, String title, String description, Status status, Priority priority,
		UUID assigneeId, UUID createdBy, Instant createdAt, Instant updatedAt) {

	public enum Status { TODO, IN_PROGRESS, IN_REVIEW, DONE }
	public enum Priority { LOW, MEDIUM, HIGH }
}
