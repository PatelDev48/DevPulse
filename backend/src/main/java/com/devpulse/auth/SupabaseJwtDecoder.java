package com.devpulse.auth;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Primary
public class SupabaseJwtDecoder implements JwtDecoder {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String issuer;
    private final SupabaseUserProvisioningService provisioningService;
    
    public SupabaseJwtDecoder(
            @Value("${devpulse.supabase.url}") String url,
            @Value("${devpulse.supabase.publishable-key}") String publishableKey,
            ObjectMapper mapper,
            SupabaseUserProvisioningService provisioningService) {
        String baseUrl = url.replaceAll("/+$", "");
        this.issuer = baseUrl + "/auth/v1";
        this.client = RestClient.builder()
                .baseUrl(issuer)
                .defaultHeader("apikey", publishableKey)
                .build();
        this.mapper = mapper;
        this.provisioningService = provisioningService;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            JsonNode user = client.get()
                    .uri("/user")
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(JsonNode.class);

            String[] parts = token.split("\\.");
            if (user == null || parts.length != 3) {
                throw new BadJwtException("Invalid Supabase access token.");
            }

            JsonNode claims = mapper.readTree(
                    Base64.getUrlDecoder().decode(parts[1]));
            String subject = claims.path("sub").asText();

            if (!UUID.fromString(subject).toString().equals(user.path("id").asText())
                    || !issuer.equals(claims.path("iss").asText())
                    || !"authenticated".equals(claims.path("aud").asText())) {
                throw new BadJwtException("Invalid Supabase access token.");
            }

            if (user.path("email_confirmed_at").isMissingNode()
                    || user.path("email_confirmed_at").isNull()
                    || user.path("email_confirmed_at").asText().isBlank()) {
                throw new BadJwtException("Supabase email is not confirmed.");
            }

            var localUser = provisioningService.resolve(
                    UUID.fromString(subject),
                    user.path("email").asText(),
                    user.path("user_metadata").path("name").asText());
        
            return Jwt.withTokenValue(token)
                    .header("alg", "HS256")
                    .issuer(issuer)
                    .subject(localUser.id().toString())
                    .audience(List.of("authenticated"))
                    .issuedAt(Instant.ofEpochSecond(claims.path("iat").asLong()))
                    .expiresAt(Instant.ofEpochSecond(claims.path("exp").asLong()))
                    .claim("email", user.path("email").asText())
                    .claim("supabase_user_id", subject)
                    .claim("user_metadata", user.path("user_metadata"))
                    .build();
        } catch (JwtException exception) {
            throw exception;
        } catch (RestClientException | JacksonException | IllegalArgumentException exception) {
            throw new BadJwtException("Unable to validate Supabase access token.", exception);
        }
    }
}
