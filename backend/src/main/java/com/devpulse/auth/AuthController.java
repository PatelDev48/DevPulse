package com.devpulse.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final SignupService signupService;
	private final LoginService loginService;
	private final TokenService tokenService;

	public AuthController(SignupService signupService, LoginService loginService, TokenService tokenService) {
		this.signupService = signupService;
		this.loginService = loginService;
		this.tokenService = tokenService;
	}

	@PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TokenService.TokenResult> login(@RequestBody LoginRequest request) {
		var user = loginService.login(request.email(), request.password());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokenService.issue(user));
	}

	@GetMapping("/me")
	public java.util.Map<String, String> currentUser(@AuthenticationPrincipal Jwt token) {
		return java.util.Map.of("id", token.getSubject());
	}

	public record LoginRequest(String email, String password) {
		@Override
		public String toString() {
			return "LoginRequest[redacted]";
		}
	}

	@PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public SignupService.SignupResult signup(@RequestBody SignupRequest request) {
		return signupService.signup(request.name(), request.email(), request.password());
	}

	public record SignupRequest(String name, String email, String password) {

		@Override
		public String toString() {
			return "SignupRequest[redacted]";
		}
	}
}