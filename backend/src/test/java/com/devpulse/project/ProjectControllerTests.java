package com.devpulse.project;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.devpulse.team.TeamRepository;

@WebMvcTest(value = ProjectController.class, properties = "JWT_SECRET_BASE64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import({ ProjectService.class, ProjectExceptionHandler.class, SecurityConfiguration.class,
		TokenConfiguration.class, TokenService.class })
class ProjectControllerTests {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID TEAM_ID = UUID.randomUUID();
	private static final String PATH = "/api/teams/" + TEAM_ID + "/projects";
	private static final Project PROJECT = new Project(UUID.randomUUID(), TEAM_ID, "Portal", "Customer portal",
			USER_ID, Instant.parse("2026-09-20T00:00:00Z"));

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private TokenService tokenService;
	@MockitoBean
	private ProjectRepository projectRepository;
	@MockitoBean
	private TeamRepository teamRepository;

	@Test
	void ownerCreatesProjectUsingRouteTeamAndJwtIdentity() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("OWNER"));
		when(projectRepository.create(TEAM_ID, "Portal", "Customer portal", USER_ID)).thenReturn(Optional.of(PROJECT));
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" Portal \",\"description\":\" Customer portal \",\"teamId\":\"" + UUID.randomUUID()
						+ "\",\"createdBy\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.id").value(PROJECT.id().toString()))
				.andExpect(jsonPath("$.teamId").value(TEAM_ID.toString()))
				.andExpect(jsonPath("$.createdBy").value(USER_ID.toString()))
				.andExpect(jsonPath("$.name").value("Portal"))
				.andExpect(jsonPath("$.description").value("Customer portal"));
		verify(projectRepository).create(TEAM_ID, "Portal", "Customer portal", USER_ID);
	}

	@ParameterizedTest
	@ValueSource(strings = { "OWNER", "MEMBER" })
	void teamMembersCanListProjects(String role) throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of(role));
		when(projectRepository.findByTeamId(TEAM_ID, USER_ID)).thenReturn(List.of(PROJECT));
		mockMvc.perform(get(PATH).param("userId", UUID.randomUUID().toString()).header("Authorization", authorization()))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].teamId").value(TEAM_ID.toString()));
		verify(projectRepository).findByTeamId(TEAM_ID, USER_ID);
	}

	@Test
	void memberCannotCreateProject() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("MEMBER"));
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Portal\"}"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(projectRepository);
	}

	@Test
	void outsiderCannotCreateOrListProjects() throws Exception {
		mockMvc.perform(get(PATH).header("Authorization", authorization())).andExpect(status().isForbidden());
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Portal\"}")).andExpect(status().isForbidden());
		verifyNoInteractions(projectRepository);
	}

	@Test
	void bothEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Portal\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
		verifyNoInteractions(projectRepository, teamRepository);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"name\":null}", "{\"name\":\"   \"}" })
	void invalidNameIsRejected(String body) throws Exception {
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(projectRepository, teamRepository);
	}

	@Test
	void overlongFieldsAreRejected() throws Exception {
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + "n".repeat(101) + "\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Portal\",\"description\":\"" + "d".repeat(2001) + "\"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(projectRepository, teamRepository);
	}

	@Test
	void optionalDescriptionAndBoundaryLengthsAreAccepted() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("OWNER"));
		when(projectRepository.create(TEAM_ID, "Portal", null, USER_ID)).thenReturn(Optional.of(PROJECT));
		when(projectRepository.create(TEAM_ID, "n".repeat(100), "d".repeat(2000), USER_ID)).thenReturn(Optional.of(PROJECT));
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Portal\"}")).andExpect(status().isCreated());
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + "n".repeat(100) + "\",\"description\":\"" + "d".repeat(2000) + "\"}"))
				.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "[]", "{", "{\"name\":{}}" })
	void malformedBodiesAreRejected(String body) throws Exception {
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(projectRepository, teamRepository);
	}

	@Test
	void invalidTeamIdAndUnsupportedContentTypeAreRejected() throws Exception {
		mockMvc.perform(get("/api/teams/not-a-uuid/projects").header("Authorization", authorization()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.TEXT_PLAIN).content("Portal"))
				.andExpect(status().isUnsupportedMediaType());
		verifyNoInteractions(projectRepository, teamRepository);
	}

	@Test
	void emptyTeamReturnsEmptyList() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("MEMBER"));
		mockMvc.perform(get(PATH).header("Authorization", authorization()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void lostOwnerPermissionAtInsertIsDenied() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("OWNER"));
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Portal\"}")).andExpect(status().isForbidden());
	}

	@Test
	void databaseFailuresAreSanitized() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("OWNER"));
		when(projectRepository.create(TEAM_ID, "Portal", null, USER_ID))
				.thenThrow(new DataAccessResourceFailureException("private SQL details"));
		when(projectRepository.findByTeamId(TEAM_ID, USER_ID))
				.thenThrow(new DataAccessResourceFailureException("private SQL details"));
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Portal\"}")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Unable to complete project request."));
		mockMvc.perform(get(PATH).header("Authorization", authorization())).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Unable to complete project request."));
	}

	private String authorization() {
		return "Bearer " + tokenService.issue(new LoginService.LoginResult(USER_ID, "Project test",
				"project@example.test", Instant.now())).token();
	}

	@Test
	void ownerCanEditArchiveAndRestore() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("OWNER"));
		when(projectRepository.find(TEAM_ID, PROJECT.id())).thenReturn(Optional.of(PROJECT));
		when(projectRepository.update(TEAM_ID, PROJECT.id(), "Renamed", null)).thenReturn(PROJECT);
		when(projectRepository.setArchived(TEAM_ID, PROJECT.id(), true)).thenReturn(PROJECT);
		when(projectRepository.setArchived(TEAM_ID, PROJECT.id(), false)).thenReturn(PROJECT);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(PATH + "/" + PROJECT.id())
				.header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Renamed \"}"))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
		for (boolean archived : List.of(true, false)) {
			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(PATH + "/" + PROJECT.id() + "/archive")
					.header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":" + archived + "}"))
					.andExpect(status().isOk());
		}
		verify(projectRepository).update(TEAM_ID, PROJECT.id(), "Renamed", null);
	}

	@Test
	void archivedProjectsRejectEditsAndMissingProjectsReturn404() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("OWNER"));
		when(projectRepository.find(TEAM_ID, PROJECT.id())).thenReturn(Optional.of(new Project(PROJECT.id(), TEAM_ID,
				"Portal", null, USER_ID, Instant.now(), Instant.now(), Instant.now())));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(PATH + "/" + PROJECT.id())
				.header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
				.andExpect(status().isConflict());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(PATH + "/" + UUID.randomUUID() + "/archive")
				.header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void membersCannotManageProjects() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("MEMBER"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(PATH + "/" + PROJECT.id())
				.header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(PATH + "/" + PROJECT.id() + "/archive")
				.header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(projectRepository);
	}

}
