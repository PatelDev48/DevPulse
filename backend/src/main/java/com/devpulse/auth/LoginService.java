package com.devpulse.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.devpulse.user.UserRepository;

@Service
public class LoginService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String dummyPasswordHash;

	public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	public LoginResult login(String email, String password) {
		String normalizedEmail = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
		if (normalizedEmail == null || normalizedEmail.isBlank() || normalizedEmail.length() > 254
				|| password == null || password.isBlank() || password.length() > 72
				|| password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new InvalidCredentialsException();
		}

		var user = userRepository.findByEmail(normalizedEmail);
		String passwordHash = user.map(account -> account.passwordHash()).orElse(dummyPasswordHash);
		boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
		if (user.isEmpty() || !passwordMatches) {
			throw new InvalidCredentialsException();
		}

		var account = user.orElseThrow();
		return new LoginResult(account.id(), account.name(), account.email(), account.createdAt());
	}

	public record LoginResult(UUID id, String name, String email, Instant createdAt) {
	}
}