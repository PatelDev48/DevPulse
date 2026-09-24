package com.devpulse.task;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@WebMvcTest(value = TaskController.class, properties = "JWT_SECRET_BASE64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import({ TaskService.class, TaskExceptionHandler.class, SecurityConfiguration.class, TokenConfiguration.class, TokenService.class })
class TaskControllerTests {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID TEAM_ID = UUID.randomUUID();
	private static final UUID PROJECT_ID = UUID.randomUUID();
	private static final UUID TASK_ID = UUID.randomUUID();
	private static final String PATH = "/api/teams/" + TEAM_ID + "/projects/" + PROJECT_ID + "/tasks";
	private static final Task TASK = new Task(TASK_ID, PROJECT_ID, "Build portal", null, Task.Status.TODO,
			Task.Priority.MEDIUM, null, USER_ID, Instant.now(), Instant.now());

	@Autowired private MockMvc mockMvc;
	@Autowired private TokenService tokenService;
	@MockitoBean private TaskRepository taskRepository;
	@MockitoBean private TeamRepository teamRepository;

	@Test
	void createsTaskWithDefaultsAndIgnoresForgedIdentityAndProject() throws Exception {
		allowMember();
		when(taskRepository.create(PROJECT_ID, "Build portal", null, Task.Priority.MEDIUM, null, USER_ID)).thenReturn(TASK);
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\" Build portal \",\"projectId\":\"" + UUID.randomUUID()
						+ "\",\"createdBy\":\"" + UUID.randomUUID() + "\",\"status\":\"DONE\"}"))
				.andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
				.andExpect(jsonPath("$.createdBy").value(USER_ID.toString()))
				.andExpect(jsonPath("$.status").value("TODO")).andExpect(jsonPath("$.priority").value("MEDIUM"));
		var order = inOrder(teamRepository, taskRepository);
		order.verify(teamRepository).lockTaskMembership(TEAM_ID);
		order.verify(teamRepository).findRole(TEAM_ID, USER_ID);
		order.verify(taskRepository).projectBelongsToTeam(PROJECT_ID, TEAM_ID);
		order.verify(taskRepository).create(PROJECT_ID, "Build portal", null, Task.Priority.MEDIUM, null, USER_ID);
	}

	@Test
	void listsTasksForAuthenticatedMember() throws Exception {
		allowMember();
		when(taskRepository.findByProject(TEAM_ID, PROJECT_ID, USER_ID)).thenReturn(List.of(TASK));
		mockMvc.perform(get(PATH).header("Authorization", authorization()).param("userId", UUID.randomUUID().toString()))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(TASK_ID.toString()));
	}

	@Test
	void updatesAllEditableFieldsButNotParentOrAuthor() throws Exception {
		allowMember();
		when(taskRepository.update(PROJECT_ID, TASK_ID, "Edited", "Details", Task.Status.IN_REVIEW, Task.Priority.HIGH, USER_ID))
				.thenReturn(Optional.of(TASK));
		mockMvc.perform(put(PATH + "/" + TASK_ID).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\" Edited \",\"description\":\" Details \",\"status\":\"IN_REVIEW\",\"priority\":\"HIGH\",\"assigneeId\":\""
						+ USER_ID + "\",\"projectId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isOk());
		verify(taskRepository).update(PROJECT_ID, TASK_ID, "Edited", "Details", Task.Status.IN_REVIEW, Task.Priority.HIGH, USER_ID);
	}

	@Test
	void statusOnlyUpdateUsesScopedTask() throws Exception {
		allowMember();
		when(taskRepository.updateStatus(PROJECT_ID, TASK_ID, Task.Status.DONE)).thenReturn(Optional.of(TASK));
		mockMvc.perform(patch(PATH + "/" + TASK_ID + "/status").header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
		verify(taskRepository).updateStatus(PROJECT_ID, TASK_ID, Task.Status.DONE);
	}

	@Test
	void allEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Task\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put(PATH + "/" + TASK_ID).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch(PATH + "/" + TASK_ID + "/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
		verifyNoInteractions(teamRepository, taskRepository);
	}

	@Test
	void outsidersCannotReadOrWriteTasks() throws Exception {
		mockMvc.perform(get(PATH).header("Authorization", authorization())).andExpect(status().isForbidden());
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Task\"}")).andExpect(status().isForbidden());
		mockMvc.perform(put(PATH + "/" + TASK_ID).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Task\",\"status\":\"TODO\",\"priority\":\"LOW\"}")).andExpect(status().isForbidden());
		mockMvc.perform(patch(PATH + "/" + TASK_ID + "/status").header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isForbidden());
		verifyNoInteractions(taskRepository);
	}

	@Test
	void wrongProjectAndMissingTaskReturn404() throws Exception {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("MEMBER"));
		mockMvc.perform(get(PATH).header("Authorization", authorization())).andExpect(status().isNotFound());
		allowMember();
		mockMvc.perform(patch(PATH + "/" + TASK_ID + "/status").header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Task not found in this project."));
	}

	@Test
	void invalidAssigneeIsRejected() throws Exception {
		allowMember();
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Task\",\"assigneeId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("The assignee must be a current member of this team."));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"title\":null}", "{\"title\":\"   \"}", "{\"title\":\"Task\",\"priority\":\"URGENT\"}",
			"{\"title\":\"Task\",\"assigneeId\":\"invalid\"}", "null", "[]", "{", "" })
	void invalidCreateBodyReturns400(String body) throws Exception {
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(teamRepository, taskRepository);
	}

	@Test
	void lengthLimitsAndRequiredUpdateFieldsAreValidated() throws Exception {
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + "t".repeat(201) + "\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Task\",\"description\":\"" + "d".repeat(5001) + "\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(put(PATH + "/" + TASK_ID).header("Authorization", authorization()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Task\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(patch(PATH + "/" + TASK_ID + "/status").header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
		mockMvc.perform(patch(PATH + "/" + TASK_ID + "/status").header("Authorization", authorization())
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INVALID\"}" )).andExpect(status().isBadRequest());
		verifyNoInteractions(teamRepository, taskRepository);
	}

	@Test
	void invalidPathAndContentTypeAreRejected() throws Exception {
		mockMvc.perform(get(PATH.replace(PROJECT_ID.toString(), "invalid")).header("Authorization", authorization()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post(PATH).header("Authorization", authorization()).contentType(MediaType.TEXT_PLAIN).content("Task"))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void databaseErrorsAreSanitized() throws Exception {
		allowMember();
		when(taskRepository.findByProject(TEAM_ID, PROJECT_ID, USER_ID)).thenThrow(new DataAccessResourceFailureException("private SQL"));
		mockMvc.perform(get(PATH).header("Authorization", authorization())).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Unable to complete task request."));
	}

	private void allowMember() {
		when(teamRepository.findRole(TEAM_ID, USER_ID)).thenReturn(Optional.of("MEMBER"));
		when(taskRepository.projectBelongsToTeam(PROJECT_ID, TEAM_ID)).thenReturn(true);
	}

	private String authorization() {
		return "Bearer " + tokenService.issue(new LoginService.LoginResult(USER_ID, "Task test", "task@example.test", Instant.now())).token();
	}
}
