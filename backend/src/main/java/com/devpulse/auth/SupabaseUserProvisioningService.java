package com.devpulse.auth;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devpulse.user.User;
import com.devpulse.user.UserRepository;

@Service
public class SupabaseUserProvisioningService {
    private final UserRepository users;

    public SupabaseUserProvisioningService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public User resolve(UUID supabaseUserId, String email, String requestedName) {
        if (supabaseUserId == null || email == null || email.isBlank()) {
            throw new IllegalArgumentException("A verified Supabase user ID and email are required.");
        }

        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > 254) {
            throw new IllegalArgumentException("Email address is too long.");
        }

        User linked = users.findBySupabaseUserId(supabaseUserId).orElse(null);
        if (linked != null) return linked;

        User existing = users.findByEmail(normalizedEmail).orElse(null);
        if (existing != null) {
            User newlyLinked = users.linkSupabaseUserId(existing.id(), supabaseUserId).orElse(null);
            if (newlyLinked != null) return newlyLinked;

            return users.findBySupabaseUserId(supabaseUserId)
                    .orElseThrow(() -> new IllegalStateException("This account is already linked to another identity."));
        }

        String name = requestedName == null ? "" : requestedName.strip();
        if (name.isBlank()) name = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        if (name.length() > 100) name = name.substring(0, 100).strip();

        return users.createSupabaseUser(supabaseUserId, name, normalizedEmail);
    }
}