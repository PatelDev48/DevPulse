package com.devpulse.auth;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devpulse.user.User;
import com.devpulse.user.UserRepository;

@WebMvcTest(value = AuthController.class, properties = "JWT_SECRET_BASE64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import({ SignupService.class, LoginService.class, PasswordConfiguration.class, AuthExceptionHandler.class,
		TokenService.class, TokenConfiguration.class, SecurityConfiguration.class })
class AuthControllerTests {

	private static final String PASSWORD = "a sufficiently long password";
	private static final String VALID_REQUEST = """
			{"name":"  Alex  ","email":" ALEX@EXAMPLE.COM ","password":"a sufficiently long password"}
			""";
	private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
	private static final Instant CREATED_AT = Instant.parse("2026-09-14T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;

	@Autowired
	private tools.jackson.databind.json.JsonMapper jsonMapper;

	@MockitoBean
	private UserRepository userRepository;

	@Test
	void signupReturnsCreatedUserWithoutCredentials() throws Exception {
		when(userRepository.create(eq("Alex"), eq("alex@example.com"), anyString())).thenAnswer(invocation -> {
			String hash = invocation.getArgument(2);
			assertTrue(passwordEncoder.matches(PASSWORD, hash));
			return new User(USER_ID, "Alex", "alex@example.com", hash, CREATED_AT);
		});

		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", aMapWithSize(4)))
				.andExpect(jsonPath("$.id").value(USER_ID.toString()))
				.andExpect(jsonPath("$.name").value("Alex"))
				.andExpect(jsonPath("$.email").value("alex@example.com"))
				.andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{}",
			"{\"name\":null,\"email\":null,\"password\":null}",
			"{\"name\":\" \",\"email\":\"alex@example.com\",\"password\":\"a sufficiently long password\"}",
			"{\"name\":\"Alex\",\"email\":\"invalid-email\",\"password\":\"a sufficiently long password\"}",
			"{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"short\"}"
	})
	void invalidFieldsReturnBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("Invalid signup input."))
				.andExpect(content().string(not(containsString(PASSWORD))));
		verifyNoInteractions(userRepository);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "[]", "{", "{\"password\":\"secret-marker\",", "{\"name\":{}}" })
	void unreadableBodyReturnsSanitizedBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("A valid JSON request body is required."))
				.andExpect(content().string(not(containsString("secret-marker"))));
		verifyNoInteractions(userRepository);
	}

	@Test
	void registeredEmailReturnsConflict() throws Exception {
		when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(existingUser()));
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("Email is already registered."))
				.andExpect(content().string(not(containsString("stored-hash"))));
	}

	@Test
	void concurrentDuplicateEmailReturnsConflict() throws Exception {
		when(userRepository.findByEmail("alex@example.com"))
				.thenReturn(Optional.empty()).thenReturn(Optional.of(existingUser()));
		when(userRepository.create(anyString(), anyString(), anyString()))
				.thenThrow(new DuplicateKeyException("internal-sql-marker"));
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isConflict())
				.andExpect(content().string(not(containsString("internal-sql-marker"))));
	}

	@Test
	void unexpectedFailureReturnsGenericServerError() throws Exception {
		when(userRepository.findByEmail(anyString()))
				.thenThrow(new DataAccessResourceFailureException("internal-sql-marker"));
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.detail").value("Unable to complete authentication request."))
				.andExpect(content().string(not(containsString("internal-sql-marker"))))
				.andExpect(content().string(not(containsString(PASSWORD))));
	}

	@Test
	void unsupportedContentTypeReturns415() throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.TEXT_PLAIN).content(VALID_REQUEST))
				.andExpect(status().isUnsupportedMediaType());
		verifyNoInteractions(userRepository);
	}

	@Test
	void unsupportedMethodReturns405() throws Exception {
		mockMvc.perform(get("/api/auth/signup")).andExpect(status().isMethodNotAllowed());
		verifyNoInteractions(userRepository);
	}

	@Test
	void requestToStringDoesNotExposeCredentials() {
		assertEquals("SignupRequest[redacted]", new AuthController.SignupRequest("Alex", "alex@example.com", PASSWORD).toString());
	}

	private User existingUser() {
		return new User(USER_ID, "Alex", "alex@example.com", "stored-hash", CREATED_AT);
	}

	@Test
	void loginIssuesTokenThatAuthenticatesProtectedRequest() throws Exception {
		User user = new User(USER_ID, "Alex", "alex@example.com", passwordEncoder.encode(PASSWORD), CREATED_AT);
		when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));
		var response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresAt").exists())
				.andExpect(jsonPath("$.user", aMapWithSize(4)))
				.andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
				.andReturn().getResponse();
		String token = jsonMapper.readTree(response.getContentAsString()).get("token").asText();
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(USER_ID.toString()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "unknown@example.com", "alex@example.com" })
	void incorrectCredentialsReturnSameUnauthorizedResponse(String email) throws Exception {
		User user = new User(USER_ID, "Alex", "alex@example.com", passwordEncoder.encode(PASSWORD), CREATED_AT);
		when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));
		String body = jsonMapper.writeValueAsString(java.util.Map.of("email", email, "password", "wrong password"));
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid email or password."))
				.andExpect(jsonPath("$.token").doesNotExist());
	}

	@Test
	void missingCredentialsReturnUnauthorized() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(userRepository);
	}

	@Test
	void protectedEndpointRequiresBearerToken() throws Exception {
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "expired", "issuer", "audience", "subject", "signature" })
	void invalidTokensAreRejected(String scenario) throws Exception {
		var now = Instant.now();
		var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
				.issuer(scenario.equals("issuer") ? "untrusted" : TokenService.ISSUER)
				.audience(java.util.List.of(scenario.equals("audience") ? "other-api" : TokenService.AUDIENCE))
				.subject(scenario.equals("subject") ? "not-a-uuid" : USER_ID.toString())
				.issuedAt(now.minusSeconds(1200))
				.expiresAt(scenario.equals("expired") ? now.minusSeconds(300) : now.plusSeconds(900)).build();
		var encoder = jwtEncoder;
		if (scenario.equals("signature")) {
			byte[] otherKey = new byte[32];
			new java.security.SecureRandom().nextBytes(otherKey);
			encoder = new TokenConfiguration().jwtEncoder(new javax.crypto.spec.SecretKeySpec(otherKey, "HmacSHA256"));
		}
		String token = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
				org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenLifetimeAndClaimsAreMinimal() {
		var fixedClock = java.time.Clock.fixed(CREATED_AT, java.time.ZoneOffset.UTC);
		var service = new TokenService(jwtEncoder, fixedClock);
		var result = service.issue(new LoginService.LoginResult(USER_ID, "Alex", "alex@example.com", CREATED_AT));
		assertEquals(CREATED_AT.plusSeconds(900), result.expiresAt());
		assertEquals("TokenResult[redacted]", result.toString());
		var claims = jsonMapper.readTree(java.util.Base64.getUrlDecoder().decode(result.token().split("\\.")[1]));
		assertEquals(USER_ID.toString(), claims.get("sub").asText());
		org.junit.jupiter.api.Assertions.assertFalse(claims.has("email"));
		org.junit.jupiter.api.Assertions.assertFalse(claims.has("passwordHash"));
	}

	@Test
	void signingKeyCannotBeMissingOrWeak() {
		var configuration = new TokenConfiguration();
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> configuration.jwtSigningKey("invalid!"));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> configuration.jwtSigningKey("c2hvcnQ="));
	}
}