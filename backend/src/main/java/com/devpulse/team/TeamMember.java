package com.devpulse.team;

import java.time.Instant;
import java.util.UUID;

public record TeamMember(UUID userId, String name, String email, String role, Instant joinedAt) {
}