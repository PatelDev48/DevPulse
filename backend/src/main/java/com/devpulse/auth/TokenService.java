package com.devpulse.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	static final String ISSUER = "devpulse";
	static final String AUDIENCE = "devpulse-api";
	private final JwtEncoder encoder;
	private final Clock clock;

	public TokenService(JwtEncoder encoder, Clock clock) {
		this.encoder = encoder;
		this.clock = clock;
	}

	public TokenResult issue(LoginService.LoginResult user) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(Duration.ofMinutes(15));
		var claims = JwtClaimsSet.builder().issuer(ISSUER).audience(List.of(AUDIENCE))
				.subject(user.id().toString()).issuedAt(now).expiresAt(expiresAt).build();
		String token = encoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		return new TokenResult(token, "Bearer", expiresAt, user);
	}

	public record TokenResult(String token, String tokenType, Instant expiresAt, LoginService.LoginResult user) {
		@Override
		public String toString() {
			return "TokenResult[redacted]";
		}
	}
}