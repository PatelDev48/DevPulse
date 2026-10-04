package com.devpulse.auth;

import java.time.Clock;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
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
	public Clock tokenClock() {
		return Clock.systemUTC();
	}
}
