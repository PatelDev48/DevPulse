package com.devpulse.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/teams").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/teams/{teamId}/dashboard").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/teams").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/teams/{teamId}/projects").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/teams/{teamId}/projects").authenticated()
						.requestMatchers(HttpMethod.PUT, "/api/teams/{teamId}/projects/{projectId}").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/api/teams/{teamId}/projects/{projectId}/archive").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/teams/{teamId}/projects/{projectId}/tasks").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/teams/{teamId}/projects/{projectId}/tasks").authenticated()
						.requestMatchers(HttpMethod.PUT, "/api/teams/{teamId}/projects/{projectId}/tasks/{taskId}").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/api/teams/{teamId}/projects/{projectId}/tasks/{taskId}/status").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/teams/{teamId}/members").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/api/teams/{teamId}/members/{memberId}").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/teams/{teamId}/invitations", "/api/invitations/accept").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/api/teams/{teamId}/invitations/{invitationId}").authenticated()
						.anyRequest().denyAll())
				.oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
				.build();
	}
}