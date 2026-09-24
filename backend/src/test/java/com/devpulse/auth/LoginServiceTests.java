package com.devpulse.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.devpulse.user.User;
import com.devpulse.user.UserRepository;

class LoginServiceTests {

	private static final String PASSWORD = "A sufficiently long password!";
	private UserRepository userRepository;
	private BCryptPasswordEncoder passwordEncoder;
	private LoginService loginService;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		passwordEncoder = spy(new BCryptPasswordEncoder(4));
		loginService = new LoginService(userRepository, passwordEncoder);
		clearInvocations(passwordEncoder);
	}

	@Test
	void correctPasswordReturnsSafeUserDetails() {
		User user = account(PASSWORD);
		when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));

		var result = loginService.login("  ALEX@EXAMPLE.COM  ", PASSWORD);

		assertEquals(new LoginService.LoginResult(user.id(), user.name(), user.email(), user.createdAt()), result);
		assertEquals(List.of("id", "name", "email", "createdAt"),
				Arrays.stream(result.getClass().getRecordComponents()).map(component -> component.getName()).toList());
		verify(userRepository).findByEmail("alex@example.com");
		verifyNoMoreInteractions(userRepository);
	}

	@Test
	void unknownEmailAndWrongPasswordHaveSameError() {
		when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(account(PASSWORD)));
		var wrongPassword = assertThrows(InvalidCredentialsException.class,
				() -> loginService.login("alex@example.com", "a wrong password"));
		var unknownEmail = assertThrows(InvalidCredentialsException.class,
				() -> loginService.login("unknown@example.com", "a wrong password"));
		assertEquals("Invalid email or password.", wrongPassword.getMessage());
		assertEquals(wrongPassword.getMessage(), unknownEmail.getMessage());
	}

	@Test
	void unknownEmailStillRunsPasswordComparison() {
		assertThrows(InvalidCredentialsException.class, () -> loginService.login("unknown@example.com", PASSWORD));
		verify(passwordEncoder).matches(eq(PASSWORD), anyString());
	}

	@Test
	void passwordsAreNeitherTrimmedNorLowercased() {
		String password = " " + PASSWORD + " ";
		when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(account(password)));
		loginService.login("alex@example.com", password);
		assertThrows(InvalidCredentialsException.class, () -> loginService.login("alex@example.com", password.strip()));
		assertThrows(InvalidCredentialsException.class,
				() -> loginService.login("alex@example.com", password.toLowerCase(Locale.ROOT)));
	}

	@ParameterizedTest
	@MethodSource("invalidInputs")
	void invalidInputIsRejectedBeforeDatabaseOrHashWork(String email, String password) {
		var failure = assertThrows(InvalidCredentialsException.class, () -> loginService.login(email, password));
		assertEquals("Invalid email or password.", failure.getMessage());
		verifyNoInteractions(userRepository, passwordEncoder);
	}

	static Stream<Arguments> invalidInputs() {
		return Stream.of(
				Arguments.of(null, PASSWORD),
				Arguments.of("", PASSWORD),
				Arguments.of(" \t\n ", PASSWORD),
				Arguments.of("email".repeat(64), PASSWORD),
				Arguments.of("alex@example.com", null),
				Arguments.of("alex@example.com", ""),
				Arguments.of("alex@example.com", "   "),
				Arguments.of("alex@example.com", "p".repeat(73)),
				Arguments.of("alex@example.com", "\u00e9".repeat(37)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "short", "p" })
	void loginDoesNotApplyNewSignupMinimumToStoredPasswords(String password) {
		when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(account(password)));
		assertEquals("alex@example.com", loginService.login("alex@example.com", password).email());
	}

	@Test
	void acceptsSeventyTwoUtf8BytesButRejectsExtraBytes() {
		String password = "\u00e9".repeat(36);
		when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(account(password)));
		loginService.login("alex@example.com", password);
		assertThrows(InvalidCredentialsException.class, () -> loginService.login("alex@example.com", password + "x"));
	}

	@Test
	void emailNormalizationIsLocaleIndependent() {
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));
			assertThrows(InvalidCredentialsException.class, () -> loginService.login("IRIS@EXAMPLE.COM", PASSWORD));
			verify(userRepository).findByEmail("iris@example.com");
		} finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void databaseFailureIsNotDisguisedAsInvalidCredentials() {
		var failure = new DataAccessResourceFailureException("Database unavailable");
		when(userRepository.findByEmail("alex@example.com")).thenThrow(failure);
		assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
				() -> loginService.login("alex@example.com", PASSWORD)));
	}

	private User account(String password) {
		return new User(UUID.randomUUID(), "Alex", "alex@example.com",
				new BCryptPasswordEncoder(4).encode(password), Instant.now());
	}
}