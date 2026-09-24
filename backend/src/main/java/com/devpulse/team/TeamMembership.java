package com.devpulse.team;

import java.time.Instant;
import java.util.UUID;

public record TeamMembership(UUID id, String name, Instant createdAt, String role) {
}