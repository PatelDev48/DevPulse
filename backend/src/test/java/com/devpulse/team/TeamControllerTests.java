package com.devpulse.team;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devpulse.auth.LoginService;
import com.devpulse.auth.SecurityConfiguration;
import com.devpulse.auth.TokenConfiguration;
import com.devpulse.auth.TokenService;

@WebMvcTest(value = TeamController.class, properties = "JWT_SECRET_BASE64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import({ TeamService.class, TeamExceptionHandler.class, SecurityConfiguration.class,
		TokenConfiguration.class, TokenService.class })
class TeamControllerTests {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
	private static final Team TEAM = new Team(UUID.randomUUID(), "Platform", Instant.parse("2026-09-20T00:00:00Z"));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokenService;

	@MockitoBean
	private TeamRepository teamRepository;

	@Test
	void createsTeamForTokenSubjectNotBodyOwner() throws Exception {
		when(teamRepository.create("Platform")).thenReturn(TEAM);
		mockMvc.perform(post("/api/teams").header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" Platform \",\"ownerId\":\"" + UUID.randomUUID() + "\",\"role\":\"MEMBER\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(TEAM.id().toString()))
				.andExpect(jsonPath("$.name").value("Platform"))
				.andExpect(jsonPath("$.createdAt").value(TEAM.createdAt().toString()));
		verify(teamRepository).addOwner(TEAM.id(), USER_ID);
	}

	@Test
	void listsTeamsForTokenSubjectNotQueryParameter() throws Exception {
		when(teamRepository.findByMemberId(USER_ID))
				.thenReturn(List.of(new TeamMembership(TEAM.id(), TEAM.name(), TEAM.createdAt(), "OWNER")));
		mockMvc.perform(get("/api/teams").param("userId", UUID.randomUUID().toString())
				.header("Authorization", authorization()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(TEAM.id().toString()))
				.andExpect(jsonPath("$[0].role").value("OWNER"));
		verify(teamRepository).findByMemberId(USER_ID);
	}

	@Test
	void returnsEmptyListForUserWithoutTeams() throws Exception {
		when(teamRepository.findByMemberId(USER_ID)).thenReturn(List.of());
		mockMvc.perform(get("/api/teams").header("Authorization", authorization()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void bothEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/teams")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Platform\"}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(teamRepository);
	}

	@Test
	void invalidBearerTokenIsRejected() throws Exception {
		mockMvc.perform(get("/api/teams").header("Authorization", "Bearer invalid"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(teamRepository);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"name\":null}", "{\"name\":\"   \"}" })
	void invalidNameReturnsBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/teams").header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("A team name of 1 to 100 nonblank characters is required."));
		verifyNoInteractions(teamRepository);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "[]", "{", "{\"name\":{}}" })
	void malformedBodyReturnsBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/teams").header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("A valid JSON request body is required."));
		verifyNoInteractions(teamRepository);
	}

	@Test
	void unsupportedContentTypeReturns415() throws Exception {
		mockMvc.perform(post("/api/teams").header("Authorization", authorization())
				.contentType(MediaType.TEXT_PLAIN).content("Platform"))
				.andExpect(status().isUnsupportedMediaType());
		verifyNoInteractions(teamRepository);
	}

	@Test
	void creationFailureDoesNotLeakDatabaseDetails() throws Exception {
		when(teamRepository.create("Platform")).thenThrow(new DataAccessResourceFailureException("private SQL details"));
		mockMvc.perform(post("/api/teams").header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Platform\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Unable to complete team request."));
	}

	@Test
	void listingFailureDoesNotLeakDatabaseDetails() throws Exception {
		when(teamRepository.findByMemberId(USER_ID)).thenThrow(new DataAccessResourceFailureException("private SQL details"));
		mockMvc.perform(get("/api/teams").header("Authorization", authorization()))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Unable to complete team request."));
	}

	@Test
	void membersCanViewRosterWithoutCredentials() throws Exception {
		when(teamRepository.findRole(TEAM.id(), USER_ID)).thenReturn(Optional.of("MEMBER"));
		when(teamRepository.findMembers(TEAM.id(), USER_ID)).thenReturn(List.of(
				new TeamMember(USER_ID, "Alex", "alex@example.test", "MEMBER", Instant.now())));
		mockMvc.perform(get("/api/teams/{teamId}/members", TEAM.id()).header("Authorization", authorization()))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].userId").value(USER_ID.toString()))
				.andExpect(jsonPath("$[0].role").value("MEMBER"))
				.andExpect(jsonPath("$[0].passwordHash").doesNotExist());
	}

	@Test
	void ownerCanRevokeMemberUsingJwtIdentity() throws Exception {
		UUID memberId = UUID.randomUUID();
		when(teamRepository.findRole(TEAM.id(), USER_ID)).thenReturn(Optional.of("OWNER"));
		when(teamRepository.removeMember(TEAM.id(), memberId, USER_ID)).thenReturn(true);
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", TEAM.id(), memberId)
				.param("ownerId", UUID.randomUUID().toString()).header("Authorization", authorization()))
				.andExpect(status().isNoContent());
		verify(teamRepository).removeMember(TEAM.id(), memberId, USER_ID);
	}

	@Test
	void outsiderCannotReadRosterAndMemberCannotRevoke() throws Exception {
		when(teamRepository.findRole(TEAM.id(), USER_ID)).thenReturn(Optional.empty());
		mockMvc.perform(get("/api/teams/{teamId}/members", TEAM.id()).header("Authorization", authorization()))
				.andExpect(status().isForbidden());
		when(teamRepository.findRole(TEAM.id(), USER_ID)).thenReturn(Optional.of("MEMBER"));
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", TEAM.id(), UUID.randomUUID())
				.header("Authorization", authorization())).andExpect(status().isForbidden());
	}

	@Test
	void ownerRemovalIsRejectedAndUnknownMemberReturns404() throws Exception {
		when(teamRepository.findRole(TEAM.id(), USER_ID)).thenReturn(Optional.of("OWNER"));
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", TEAM.id(), USER_ID)
				.header("Authorization", authorization())).andExpect(status().isConflict());
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", TEAM.id(), UUID.randomUUID())
				.header("Authorization", authorization())).andExpect(status().isNotFound());
	}

	@Test
	void rosterAndRevocationRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/teams/{teamId}/members", TEAM.id())).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", TEAM.id(), UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(teamRepository);
	}

	private String authorization() {
		return "Bearer " + tokenService.issue(new LoginService.LoginResult(USER_ID, "Team test",
				"team@example.test", Instant.now())).token();
	}
}