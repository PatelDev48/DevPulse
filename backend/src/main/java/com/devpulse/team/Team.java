package com.devpulse.team;

import java.time.Instant;
import java.util.UUID;

public record Team(UUID id, String name, Instant createdAt) {
}