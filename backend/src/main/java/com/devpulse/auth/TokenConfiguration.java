package com.devpulse.auth;

import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class TokenConfiguration {

	@Bean
	public SecretKey jwtSigningKey(@Value("${JWT_SECRET_BASE64}") String encodedKey) {
		byte[] key;
		try {
			key = Base64.getDecoder().decode(encodedKey);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("JWT_SECRET_BASE64 must be a Base64-encoded random key.");
		}
		if (key.length < 32) {
			throw new IllegalArgumentException("JWT_SECRET_BASE64 must decode to at least 32 bytes.");
		}
		return new SecretKeySpec(key, "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		var decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(TokenService.ISSUER), token -> {
					try {
						UUID.fromString(token.getSubject());
						if (token.getExpiresAt() != null && token.getIssuedAt() != null
								&& token.getAudience().contains(TokenService.AUDIENCE)) {
							return OAuth2TokenValidatorResult.success();
						}
					} catch (IllegalArgumentException | NullPointerException exception) {
						return invalidToken();
					}
					return invalidToken();
				}));
		return decoder;
	}

	@Bean
	public Clock tokenClock() {
		return Clock.systemUTC();
	}

	private static OAuth2TokenValidatorResult invalidToken() {
		return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token.", null));
	}
}