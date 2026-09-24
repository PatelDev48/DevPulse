package com.devpulse.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.devpulse.user.User;
import com.devpulse.user.UserRepository;

@Service
public class SignupService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final Validator validator;

	public SignupService(UserRepository userRepository, PasswordEncoder passwordEncoder, Validator validator) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.validator = validator;
	}

	public SignupResult signup(String name, String email, String password) {
		String normalizedName = name == null ? null : name.strip();
		String normalizedEmail = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
		var input = new SignupInput(normalizedName, normalizedEmail, password);
		var violations = validator.validate(input);
		if (!violations.isEmpty()) {
			String message = violations.stream()
					.map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
					.sorted()
					.collect(Collectors.joining("; "));
			throw new IllegalArgumentException(message);
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
		}
		if (userRepository.findByEmail(normalizedEmail).isPresent()) {
			throw new EmailAlreadyRegisteredException();
		}

		String passwordHash = passwordEncoder.encode(password);
		User user;
		try {
			user = userRepository.create(normalizedName, normalizedEmail, passwordHash);
		} catch (DuplicateKeyException exception) {
			if (userRepository.findByEmail(normalizedEmail).isPresent()) {
				throw new EmailAlreadyRegisteredException();
			}
			throw exception;
		}
		return new SignupResult(user.id(), user.name(), user.email(), user.createdAt());
	}

	public record SignupResult(UUID id, String name, String email, Instant createdAt) {
	}

	private record SignupInput(
			@NotBlank @Size(max = 100) String name,
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 15, max = 72) String password) {

		@Override
		public String toString() {
			return "SignupInput[redacted]";
		}
	}
}