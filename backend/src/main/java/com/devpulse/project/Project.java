package com.devpulse.project;

import java.time.Instant;
import java.util.UUID;

public record Project(UUID id, UUID teamId, String name, String description, UUID createdBy, Instant createdAt,
		Instant updatedAt, Instant archivedAt) {
	public Project(UUID id, UUID teamId, String name, String description, UUID createdBy, Instant createdAt) {
		this(id, teamId, name, description, createdBy, createdAt, createdAt, null);
	}
}
