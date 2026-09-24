package com.devpulse.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.devpulse.user.User;
import com.devpulse.user.UserRepository;

class SignupServiceTests {

	private static final String PASSWORD = "a sufficiently long password";
	private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

	private UserRepository userRepository;
	private BCryptPasswordEncoder passwordEncoder;
	private SignupService signupService;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		passwordEncoder = new BCryptPasswordEncoder(4);
		signupService = new SignupService(userRepository, passwordEncoder, VALIDATOR_FACTORY.getValidator());
	}

	@AfterAll
	static void closeValidatorFactory() {
		VALIDATOR_FACTORY.close();
	}

	@Test
	void signupNormalizesInputHashesPasswordAndReturnsSafeResult() {
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.now();
		String password = " " + PASSWORD + " ";
		when(userRepository.create(eq("Alex"), eq("alex@example.com"), anyString())).thenAnswer(invocation -> {
			String hash = invocation.getArgument(2);
			assertNotEquals(password, hash);
			assertTrue(passwordEncoder.matches(password, hash));
			assertFalse(passwordEncoder.matches(password.strip(), hash));
			return new User(userId, "Alex", "alex@example.com", hash, createdAt);
		});

		var result = signupService.signup("  Alex  ", "  ALEX@EXAMPLE.COM  ", password);

		assertEquals(new SignupService.SignupResult(userId, "Alex", "alex@example.com", createdAt), result);
		assertEquals(Arrays.asList("id", "name", "email", "createdAt"),
				Arrays.stream(result.getClass().getRecordComponents()).map(component -> component.getName()).toList());
		verify(userRepository).findByEmail("alex@example.com");
	}

	@ParameterizedTest
	@MethodSource("invalidInputs")
	void invalidInputIsRejectedBeforeDatabaseAccess(String name, String email, String password) {
		assertThrows(IllegalArgumentException.class, () -> signupService.signup(name, email, password));
		verifyNoInteractions(userRepository);
	}

	static Stream<Arguments> invalidInputs() {
		return Stream.of(
				Arguments.of(null, "alex@example.com", PASSWORD),
				Arguments.of(" \t\n ", "alex@example.com", PASSWORD),
				Arguments.of("name".repeat(26), "alex@example.com", PASSWORD),
				Arguments.of("Alex", null, PASSWORD),
				Arguments.of("Alex", " ", PASSWORD),
				Arguments.of("Alex", "not-an-email", PASSWORD),
				Arguments.of("Alex", "alex @example.com", PASSWORD),
				Arguments.of("Alex", "email".repeat(64) + "@example.com", PASSWORD),
				Arguments.of("Alex", "alex@example.com", null),
				Arguments.of("Alex", "alex@example.com", " ".repeat(15)),
				Arguments.of("Alex", "alex@example.com", "short-password"),
				Arguments.of("Alex", "alex@example.com", "p".repeat(73)),
				Arguments.of("Alex", "alex@example.com", "\u00e9".repeat(37)));
	}

	@ParameterizedTest
	@MethodSource("boundaryPasswords")
	void acceptsPasswordBoundaries(String password) {
		when(userRepository.create(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
			String hash = invocation.getArgument(2);
			assertTrue(passwordEncoder.matches(password, hash));
			return existingUser(hash);
		});
		signupService.signup("Alex", "alex@example.com", password);
		verify(userRepository).create(eq("Alex"), eq("alex@example.com"), anyString());
	}

	static Stream<String> boundaryPasswords() {
		return Stream.of("p".repeat(15), "p".repeat(72), "\u00e9".repeat(36));
	}

	@Test
	void existingEmailDoesNotInsertAnotherUser() {
		when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(existingUser("hash")));
		assertThrows(EmailAlreadyRegisteredException.class,
				() -> signupService.signup("Alex", "alex@example.com", PASSWORD));
		verify(userRepository, never()).create(anyString(), anyString(), anyString());
	}

	@Test
	void concurrentDuplicateEmailIsTranslated() {
		when(userRepository.findByEmail("alex@example.com"))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(existingUser("hash")));
		when(userRepository.create(anyString(), anyString(), anyString()))
				.thenThrow(new DuplicateKeyException("duplicate email"));
		assertThrows(EmailAlreadyRegisteredException.class,
				() -> signupService.signup("Alex", "alex@example.com", PASSWORD));
	}

	@Test
	void unrelatedDuplicateKeyIsNotReportedAsDuplicateEmail() {
		var failure = new DuplicateKeyException("duplicate id");
		when(userRepository.create(anyString(), anyString(), anyString())).thenThrow(failure);
		assertSame(failure, assertThrows(DuplicateKeyException.class,
				() -> signupService.signup("Alex", "alex@example.com", PASSWORD)));
	}

	@Test
	void databaseOutageIsNotReportedAsDuplicateEmail() {
		var failure = new DataAccessResourceFailureException("database unavailable");
		when(userRepository.create(anyString(), anyString(), anyString())).thenThrow(failure);
		assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
				() -> signupService.signup("Alex", "alex@example.com", PASSWORD)));
	}

	@Test
	void emailNormalizationDoesNotDependOnDefaultLocale() {
		Locale originalLocale = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			when(userRepository.findByEmail("iris@example.com")).thenReturn(Optional.of(existingUser("hash")));
			assertThrows(EmailAlreadyRegisteredException.class,
					() -> signupService.signup("Iris", "IRIS@EXAMPLE.COM", PASSWORD));
			verify(userRepository).findByEmail("iris@example.com");
		} finally {
			Locale.setDefault(originalLocale);
		}
	}

	@Test
	void productionEncoderUsesCostTwelveAndRandomSalts() {
		var encoder = new PasswordConfiguration().passwordEncoder();
		String firstHash = encoder.encode(PASSWORD);
		String secondHash = encoder.encode(PASSWORD);
		assertTrue(firstHash.startsWith("$2a$12$"));
		assertNotEquals(firstHash, secondHash);
		assertTrue(encoder.matches(PASSWORD, firstHash));
		assertTrue(encoder.matches(PASSWORD, secondHash));
	}

	private User existingUser(String hash) {
		return new User(UUID.randomUUID(), "Alex", "alex@example.com", hash, Instant.now());
	}
}