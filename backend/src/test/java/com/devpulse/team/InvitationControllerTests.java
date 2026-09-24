package com.devpulse.team;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.devpulse.auth.LoginService;
import com.devpulse.auth.SecurityConfiguration;
import com.devpulse.auth.TokenConfiguration;
import com.devpulse.auth.TokenService;

@WebMvcTest(value = InvitationController.class, properties = "JWT_SECRET_BASE64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import({ InvitationService.class, InvitationExceptionHandler.class, SecurityConfiguration.class,
		TokenConfiguration.class, TokenService.class })
class InvitationControllerTests {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID TEAM_ID = UUID.randomUUID();
	private static final UUID INVITATION_ID = UUID.randomUUID();
	private static final String TOKEN = "A".repeat(43);
	private static final String CREATE_PATH = "/api/teams/" + TEAM_ID + "/invitations";
	private static final String REVOKE_PATH = CREATE_PATH + "/" + INVITATION_ID;
	private static final String ACCEPT_PATH = "/api/invitations/accept";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokenService;

	@MockitoBean
	private InvitationRepository repository;

	@Test
	void ownerCreatesNonCacheableInvitationUsingTokenIdentity() throws Exception {
		Instant expiresAt = Instant.now().plusSeconds(86400);
		when(repository.isOwner(TEAM_ID, USER_ID)).thenReturn(true);
		when(repository.create(eq(TEAM_ID), eq(USER_ID), anyString()))
				.thenReturn(new InvitationRepository.CreatedInvitation(INVITATION_ID, expiresAt));
		mockMvc.perform(post(CREATE_PATH).header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.id").value(INVITATION_ID.toString()))
				.andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.matchesPattern("[A-Za-z0-9_-]{43}")))
				.andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
				.andExpect(jsonPath("$.tokenHash").doesNotExist());
		verify(repository).isOwner(TEAM_ID, USER_ID);
	}

	@Test
	void nonOwnerCannotCreateOrRevoke() throws Exception {
		mockMvc.perform(post(CREATE_PATH).header("Authorization", authorization()))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
		mockMvc.perform(delete(REVOKE_PATH).header("Authorization", authorization()))
				.andExpect(status().isForbidden());
	}

	@Test
	void acceptsAsTokenUserIgnoringSuppliedUserAndRole() throws Exception {
		when(repository.claim(anyString(), eq(USER_ID))).thenReturn(Optional.of(TEAM_ID));
		when(repository.addMember(TEAM_ID, USER_ID)).thenReturn(true);
		mockMvc.perform(post(ACCEPT_PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + TOKEN + "\",\"userId\":\"" + UUID.randomUUID() + "\",\"role\":\"OWNER\"}"))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.teamId").value(TEAM_ID.toString()))
				.andExpect(content().string(not(containsString(TOKEN))));
		verify(repository).addMember(TEAM_ID, USER_ID);
	}

	@Test
	void revocationReturnsNoContent() throws Exception {
		when(repository.isOwner(TEAM_ID, USER_ID)).thenReturn(true);
		when(repository.revoke(TEAM_ID, INVITATION_ID)).thenReturn(true);
		mockMvc.perform(delete(REVOKE_PATH).header("Authorization", authorization()))
				.andExpect(status().isNoContent()).andExpect(content().string(""));
		verify(repository).revoke(TEAM_ID, INVITATION_ID);
	}

	@Test
	void unavailableInvitationReturnsGeneric400() throws Exception {
		when(repository.claim(anyString(), eq(USER_ID))).thenReturn(Optional.empty());
		mockMvc.perform(post(ACCEPT_PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + TOKEN + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Invitation is invalid, expired, revoked, or already used."))
				.andExpect(content().string(not(containsString(TOKEN))));
	}

	@Test
	void existingMemberReturns409() throws Exception {
		when(repository.claim(anyString(), eq(USER_ID))).thenReturn(Optional.of(TEAM_ID));
		mockMvc.perform(post(ACCEPT_PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + TOKEN + "\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("You are already a member of this team."));
	}

	@Test
	void allEndpointsRequireValidAuthentication() throws Exception {
		for (String bearer : new String[] { "", "Bearer invalid" }) {
			mockMvc.perform(post(CREATE_PATH).header("Authorization", bearer)).andExpect(status().isUnauthorized());
			mockMvc.perform(delete(REVOKE_PATH).header("Authorization", bearer)).andExpect(status().isUnauthorized());
			mockMvc.perform(post(ACCEPT_PATH).header("Authorization", bearer)
					.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + TOKEN + "\"}"))
					.andExpect(status().isUnauthorized());
		}
		verifyNoInteractions(repository);
	}

	@Test
	void getCannotAcceptInvitation() throws Exception {
		mockMvc.perform(get(ACCEPT_PATH).header("Authorization", authorization()))
				.andExpect(status().isForbidden());
		verifyNoInteractions(repository);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"token\":null}", "{\"token\":\"\"}", "{\"token\":\"private-marker\"}" })
	void invalidTokensReturn400WithoutDatabaseAccess(String body) throws Exception {
		mockMvc.perform(post(ACCEPT_PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isBadRequest())
				.andExpect(content().string(not(containsString("private-marker"))));
		verifyNoInteractions(repository);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "[]", "{", "{\"token\":{}}" })
	void unreadableBodiesReturn400(String body) throws Exception {
		mockMvc.perform(post(ACCEPT_PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("A valid JSON request body is required."));
		verifyNoInteractions(repository);
	}

	@Test
	void malformedIdentifiersReturn400() throws Exception {
		mockMvc.perform(post("/api/teams/not-a-uuid/invitations").header("Authorization", authorization()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("A valid UUID is required."));
		mockMvc.perform(delete(CREATE_PATH + "/not-a-uuid").header("Authorization", authorization()))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(repository);
	}

	@Test
	void unsupportedContentTypeReturns415() throws Exception {
		mockMvc.perform(post(ACCEPT_PATH).header("Authorization", authorization()).contentType(MediaType.TEXT_PLAIN)
				.content(TOKEN)).andExpect(status().isUnsupportedMediaType());
		verifyNoInteractions(repository);
	}

	@Test
	void databaseFailureIsSanitized() throws Exception {
		when(repository.claim(anyString(), eq(USER_ID))).thenThrow(new DataAccessResourceFailureException("private SQL details"));
		mockMvc.perform(post(ACCEPT_PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + TOKEN + "\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Unable to complete invitation request."))
				.andExpect(content().string(not(containsString("private SQL details"))))
				.andExpect(content().string(not(containsString(TOKEN))));
		assertFalse(new InvitationController.AcceptInvitationRequest(TOKEN).toString().contains(TOKEN));
	}

	private String authorization() {
		return "Bearer " + tokenService.issue(new LoginService.LoginResult(USER_ID, "Invitation test",
				"invitation@example.test", Instant.now())).token();
	}
}