package com.devpulse.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.devpulse.auth.LoginService;
import com.devpulse.auth.TokenService;
import com.devpulse.project.ProjectService;
import com.devpulse.team.TeamRepository;
import com.devpulse.team.TeamService;
import com.devpulse.user.UserRepository;

@SpringBootTest(properties = "JWT_SECRET_BASE64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
class TaskIntegrationTests {

	@Autowired private TaskService taskService;
	@Autowired private TaskRepository taskRepository;
	@Autowired private TeamService teamService;
	@Autowired private ProjectService projectService;
	@Autowired private UserRepository userRepository;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private Flyway flyway;
	@Autowired private MockMvc mockMvc;
	@Autowired private tools.jackson.databind.json.JsonMapper jsonMapper;
	@Autowired private TokenService tokenService;
	@Autowired private PlatformTransactionManager transactionManager;
	@MockitoSpyBean private TeamRepository teamRepository;

	private UUID ownerId;
	private UUID memberId;
	private UUID outsiderId;
	private UUID teamId;
	private UUID otherTeamId;
	private UUID projectId;
	private UUID siblingProjectId;
	private UUID otherProjectId;

	@BeforeEach
	void setUp() {
		ownerId = createUser();
		memberId = createUser();
		outsiderId = createUser();
		teamId = teamService.createTeam("Task team", ownerId).id();
		otherTeamId = teamService.createTeam("Other task team", outsiderId).id();
		addMember(teamId, memberId);
		addMember(otherTeamId, memberId);
		projectId = projectService.createProject(teamId, "Portal", null, ownerId).id();
		siblingProjectId = projectService.createProject(teamId, "Internal", null, ownerId).id();
		otherProjectId = projectService.createProject(otherTeamId, "Other portal", null, outsiderId).id();
	}

	@AfterEach
	void cleanup() {
		JdbcTemplate migrator = new JdbcTemplate(flyway.getConfiguration().getDataSource());
		migrator.update("DELETE FROM devpulse.tasks WHERE project_id IN (SELECT id FROM devpulse.projects WHERE team_id IN (?, ?))", teamId, otherTeamId);
		migrator.update("DELETE FROM devpulse.projects WHERE team_id IN (?, ?)", teamId, otherTeamId);
		migrator.update("DELETE FROM devpulse.team_memberships WHERE team_id IN (?, ?)", teamId, otherTeamId);
		migrator.update("DELETE FROM devpulse.teams WHERE id IN (?, ?)", teamId, otherTeamId);
		migrator.update("DELETE FROM devpulse.users WHERE id IN (?, ?, ?)", ownerId, memberId, outsiderId);
	}

	@Test
	void httpLifecyclePersistsScopedTasksAndPreservesImmutableFields() throws Exception {
		String memberAuth = authorization(memberId);
		String title = "O'Connor's task'); DROP TABLE devpulse.tasks; --";
		var response = mockMvc.perform(post(path(teamId, projectId)).header("Authorization", memberAuth)
				.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of(
						"title", "  " + title + "  ", "description", "  Details  ", "assigneeId", ownerId,
						"projectId", otherProjectId, "createdBy", outsiderId))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.title").value(title))
				.andExpect(jsonPath("$.description").value("Details"))
				.andExpect(jsonPath("$.status").value("TODO")).andExpect(jsonPath("$.priority").value("MEDIUM"))
				.andExpect(jsonPath("$.projectId").value(projectId.toString()))
				.andExpect(jsonPath("$.createdBy").value(memberId.toString())).andReturn().getResponse();
		UUID taskId = UUID.fromString(jsonMapper.readTree(response.getContentAsString()).get("id").asText());
		Task original = taskService.listTasks(teamId, projectId, ownerId).getFirst();
		assertEquals(4, taskId.version());
		assertNotNull(original.createdAt());
		mockMvc.perform(put(path(teamId, projectId) + "/" + taskId).header("Authorization", authorization(ownerId))
				.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of(
						"title", "  Edited task  ", "description", "  Edited details  ", "status", "IN_PROGRESS",
						"priority", "HIGH", "assigneeId", memberId, "projectId", otherProjectId, "createdBy", ownerId))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Edited task"))
				.andExpect(jsonPath("$.assigneeId").value(memberId.toString()))
				.andExpect(jsonPath("$.createdBy").value(memberId.toString()))
				.andExpect(jsonPath("$.projectId").value(projectId.toString()));
		for (Task.Status taskStatus : Task.Status.values()) {
			mockMvc.perform(patch(path(teamId, projectId) + "/" + taskId + "/status").header("Authorization", memberAuth)
					.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of("status", taskStatus))))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value(taskStatus.name()))
					.andExpect(jsonPath("$.assigneeId").value(memberId.toString()))
					.andExpect(jsonPath("$.priority").value("HIGH"));
		}
		Task changed = taskService.listTasks(teamId, projectId, memberId).getFirst();
		assertEquals(original.createdAt(), changed.createdAt());
		assertTrue(changed.updatedAt().isAfter(original.updatedAt()));
		mockMvc.perform(get(path(teamId, projectId)).header("Authorization", memberAuth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get(path(teamId, siblingProjectId)).header("Authorization", memberAuth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		Task cleared = taskService.updateTask(teamId, projectId, taskId, "Edited task", null, Task.Status.TODO,
				Task.Priority.LOW, null, memberId);
		assertNull(cleared.assigneeId());
		assertNull(cleared.description());
	}

	@Test
	void outsidersAndMismatchedParentsCannotAccessTasks() throws Exception {
		Task task = createTask(projectId, memberId);
		Task otherTask = taskService.createTask(otherTeamId, otherProjectId, "Other", null, null, null, outsiderId);
		mockMvc.perform(get(path(teamId, projectId)).header("Authorization", authorization(outsiderId))).andExpect(status().isForbidden());
		mockMvc.perform(get(path(teamId, otherProjectId)).header("Authorization", authorization(memberId))).andExpect(status().isNotFound());
		mockMvc.perform(post(path(teamId, otherProjectId)).header("Authorization", authorization(memberId))
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Wrong project\"}")).andExpect(status().isNotFound());
		for (UUID wrongTask : List.of(otherTask.id(), UUID.randomUUID())) {
			mockMvc.perform(patch(path(teamId, projectId) + "/" + wrongTask + "/status").header("Authorization", authorization(memberId))
					.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isNotFound());
			mockMvc.perform(put(path(teamId, projectId) + "/" + wrongTask).header("Authorization", authorization(memberId))
					.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Wrong\",\"status\":\"DONE\",\"priority\":\"LOW\"}"))
					.andExpect(status().isNotFound());
		}
		assertThrows(TaskService.MissingTaskException.class,
				() -> taskService.updateStatus(teamId, siblingProjectId, task.id(), Task.Status.DONE, memberId));
		assertEquals(List.of(), taskRepository.findByProject(teamId, projectId, outsiderId));
		assertEquals(Task.Status.TODO, taskService.listTasks(teamId, projectId, memberId).getFirst().status());
	}

	@Test
	void assignmentsRequireCurrentTeamMembershipAndInvalidUpdateDoesNotMutateTask() {
		Task original = createTask(projectId, memberId);
		for (UUID invalidAssignee : List.of(outsiderId, UUID.randomUUID())) {
			assertThrows(TaskService.InvalidAssigneeException.class,
					() -> taskService.createTask(teamId, projectId, "Invalid", null, null, invalidAssignee, ownerId));
			assertThrows(TaskService.InvalidAssigneeException.class,
					() -> taskService.updateTask(teamId, projectId, original.id(), "Invalid", null, Task.Status.DONE,
							Task.Priority.HIGH, invalidAssignee, ownerId));
		}
		assertEquals(List.of(original), taskService.listTasks(teamId, projectId, ownerId));
	}

	@Test
	void removalClearsOnlyThisTeamsAssignmentsAndImmediatelyDeniesExistingToken() throws Exception {
		Task task = createTask(projectId, memberId);
		Task sibling = createTask(siblingProjectId, memberId);
		Task other = taskService.createTask(otherTeamId, otherProjectId, "Other", null, null, memberId, memberId);
		String memberAuth = authorization(memberId);
		teamService.revokeMember(teamId, memberId, ownerId);
		for (UUID selectedProject : List.of(projectId, siblingProjectId)) {
			Task remaining = taskService.listTasks(teamId, selectedProject, ownerId).getFirst();
			assertNull(remaining.assigneeId());
			assertEquals(memberId, remaining.createdBy());
		}
		assertEquals(task.id(), taskService.listTasks(teamId, projectId, ownerId).getFirst().id());
		assertEquals(sibling.id(), taskService.listTasks(teamId, siblingProjectId, ownerId).getFirst().id());
		assertEquals(other, taskService.listTasks(otherTeamId, otherProjectId, memberId).getFirst());
		mockMvc.perform(get(path(teamId, projectId)).header("Authorization", memberAuth)).andExpect(status().isForbidden());
		mockMvc.perform(post(path(teamId, projectId)).header("Authorization", memberAuth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No access\"}")).andExpect(status().isForbidden());
		mockMvc.perform(put(path(teamId, projectId) + "/" + task.id()).header("Authorization", memberAuth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No access\",\"status\":\"TODO\",\"priority\":\"LOW\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch(path(teamId, projectId) + "/" + task.id() + "/status").header("Authorization", memberAuth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isForbidden());
	}

	@Test
	void cleanupFailureRollsBackMembershipDeletion() {
		Task original = createTask(projectId, memberId);
		doThrow(new DataAccessResourceFailureException("Simulated cleanup failure"))
				.when(teamRepository).clearTaskAssignments(teamId, memberId);
		try {
			assertThrows(DataAccessResourceFailureException.class, () -> teamService.revokeMember(teamId, memberId, ownerId));
		} finally {
			doCallRealMethod().when(teamRepository).clearTaskAssignments(teamId, memberId);
		}
		assertEquals("MEMBER", teamRepository.findRole(teamId, memberId).orElseThrow());
		assertEquals(List.of(original), taskService.listTasks(teamId, projectId, memberId));
	}

	@ParameterizedTest
	@ValueSource(strings = { "create-first", "remove-before-create", "update-first", "remove-before-update" })
	void assignmentAndRemovalSerializeInEitherOrder(String scenario) throws Exception {
		boolean assignmentFirst = scenario.endsWith("first");
		boolean create = scenario.contains("create");
		Task existing = taskService.createTask(teamId, projectId, "Race", null, null, null, ownerId);
		Runnable assign = () -> {
			if (create) taskService.createTask(teamId, projectId, "Assigned", null, null, memberId, ownerId);
			else taskService.updateTask(teamId, projectId, existing.id(), "Assigned", null, Task.Status.TODO, Task.Priority.MEDIUM, memberId, ownerId);
		};
		Runnable remove = () -> teamService.revokeMember(teamId, memberId, ownerId);
		CountDownLatch firstApplied = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
				if (assignmentFirst) assign.run(); else remove.run();
				firstApplied.countDown();
				await(releaseFirst);
			}));
			assertTrue(firstApplied.await(10, TimeUnit.SECONDS));
			var second = executor.submit(() -> {
				secondStarted.countDown();
				if (assignmentFirst) remove.run();
				else assertThrows(TaskService.InvalidAssigneeException.class, assign::run);
			});
			assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
			assertThrows(TimeoutException.class, () -> second.get(150, TimeUnit.MILLISECONDS));
			releaseFirst.countDown();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
		assertTrue(teamRepository.findRole(teamId, memberId).isEmpty());
		assertTrue(taskService.listTasks(teamId, projectId, ownerId).stream().allMatch(task -> task.assigneeId() == null));
	}

	@Test
	void boundaryLengthsAndAllPrioritiesPersist() {
		for (Task.Priority priority : Task.Priority.values()) {
			Task task = taskService.createTask(teamId, projectId, "t".repeat(200), "d".repeat(5000), priority, null, ownerId);
			assertEquals(200, task.title().length());
			assertEquals(5000, task.description().length());
			assertEquals(priority, task.priority());
		}
		var tasks = taskService.listTasks(teamId, projectId, ownerId);
		assertEquals(3, tasks.size());
		assertFalse(tasks.getFirst().createdAt().isBefore(tasks.getLast().createdAt()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "null-project", "missing-project", "null-title", "blank-title", "long-title", "long-description",
			"null-status", "bad-status", "null-priority", "bad-priority", "missing-assignee", "null-creator", "missing-creator" })
	void schemaRejectsInvalidTaskFields(String scenario) {
		UUID selectedProject = scenario.equals("null-project") ? null : scenario.equals("missing-project") ? UUID.randomUUID() : projectId;
		String title = switch (scenario) {
			case "null-title" -> null;
			case "blank-title" -> "   ";
			case "long-title" -> "t".repeat(201);
			default -> "Task";
		};
		String taskStatus = scenario.equals("null-status") ? null : scenario.equals("bad-status") ? "INVALID" : "TODO";
		String priority = scenario.equals("null-priority") ? null : scenario.equals("bad-priority") ? "INVALID" : "MEDIUM";
		UUID assignee = scenario.equals("missing-assignee") ? UUID.randomUUID() : null;
		UUID creator = scenario.equals("null-creator") ? null : scenario.equals("missing-creator") ? UUID.randomUUID() : ownerId;
		String description = scenario.equals("long-description") ? "d".repeat(5001) : null;
		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
				INSERT INTO devpulse.tasks (id, project_id, title, description, status, priority, assignee_id, created_by)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), selectedProject, title, description, taskStatus, priority, assignee, creator));
	}

	@Test
	void taskPrivilegesPreventDeletingOrChangingIdentityParentAndAuthorship() {
		for (String privilege : List.of("SELECT", "INSERT")) {
			assertTrue(jdbcTemplate.queryForObject("SELECT has_table_privilege(current_user, 'devpulse.tasks', ?)", Boolean.class, privilege));
		}
		for (String privilege : List.of("DELETE", "TRUNCATE", "UPDATE", "REFERENCES", "TRIGGER")) {
			assertFalse(jdbcTemplate.queryForObject("SELECT has_table_privilege(current_user, 'devpulse.tasks', ?)", Boolean.class, privilege));
		}
		for (String column : List.of("title", "description", "status", "priority", "assignee_id", "updated_at")) {
			assertTrue(jdbcTemplate.queryForObject("SELECT has_column_privilege(current_user, 'devpulse.tasks', ?, 'UPDATE')", Boolean.class, column));
		}
		for (String column : List.of("id", "project_id", "created_by", "created_at")) {
			assertFalse(jdbcTemplate.queryForObject("SELECT has_column_privilege(current_user, 'devpulse.tasks', ?, 'UPDATE')", Boolean.class, column));
		}
	}

	private Task createTask(UUID selectedProject, UUID assigneeId) {
		return taskService.createTask(teamId, selectedProject, "Build portal", null, null, assigneeId, memberId);
	}

	@Test
	void projectLifecyclePreservesHistoryAndBlocksAllTaskWritesUntilRestored() throws Exception {
		Task original = createTask(projectId, memberId);
		String projectPath = "/api/teams/" + teamId + "/projects/" + projectId;
		mockMvc.perform(put(projectPath).header("Authorization", authorization(ownerId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" Renamed portal \",\"description\":\" Details \"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed portal"));
		mockMvc.perform(patch(projectPath + "/archive").header("Authorization", authorization(ownerId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"archived\":true}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").isNotEmpty());
		var archived = projectService.listProjects(teamId, memberId).stream().filter(project -> project.id().equals(projectId)).findFirst().orElseThrow();
		assertNotNull(archived.archivedAt());
		assertEquals(archived.archivedAt(), projectService.setArchived(teamId, projectId, true, ownerId).archivedAt());
		mockMvc.perform(get(path(teamId, projectId)).header("Authorization", authorization(memberId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(post(path(teamId, projectId)).header("Authorization", authorization(memberId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Blocked\"}")).andExpect(status().isConflict());
		mockMvc.perform(put(path(teamId, projectId) + "/" + original.id()).header("Authorization", authorization(ownerId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Blocked\",\"status\":\"DONE\",\"priority\":\"HIGH\"}")).andExpect(status().isConflict());
		mockMvc.perform(patch(path(teamId, projectId) + "/" + original.id() + "/status").header("Authorization", authorization(memberId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"DONE\"}")).andExpect(status().isConflict());
		assertEquals(List.of(original), taskService.listTasks(teamId, projectId, memberId));
		assertThrows(ProjectService.ArchivedProjectException.class, () -> projectService.updateProject(teamId, projectId, "Blocked", null, ownerId));
		assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> projectService.setArchived(teamId, projectId, false, memberId));
		assertThrows(ProjectService.MissingProjectException.class, () -> projectService.setArchived(teamId, otherProjectId, false, ownerId));
		assertNull(projectService.setArchived(teamId, projectId, false, ownerId).archivedAt());
		assertEquals(Task.Status.DONE, taskService.updateStatus(teamId, projectId, original.id(), Task.Status.DONE, memberId).status());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void archiveAndTaskWritesSerialize(boolean archiveFirst) throws Exception {
		Task original = createTask(projectId, memberId);
		Runnable archive = () -> projectService.setArchived(teamId, projectId, true, ownerId);
		Runnable update = () -> taskService.updateStatus(teamId, projectId, original.id(), Task.Status.DONE, memberId);
		CountDownLatch firstApplied = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
				if (archiveFirst) archive.run(); else update.run();
				firstApplied.countDown(); await(releaseFirst);
			}));
			assertTrue(firstApplied.await(10, TimeUnit.SECONDS));
			var second = executor.submit(() -> {
				secondStarted.countDown();
				if (archiveFirst) assertThrows(ProjectService.ArchivedProjectException.class, update::run); else archive.run();
			});
			assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
			assertThrows(TimeoutException.class, () -> second.get(150, TimeUnit.MILLISECONDS));
			releaseFirst.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
		} finally {
			releaseFirst.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
		assertEquals(archiveFirst ? Task.Status.TODO : Task.Status.DONE, taskService.listTasks(teamId, projectId, ownerId).getFirst().status());
	}

	@Test
	void projectLifecyclePrivilegesRemainColumnScoped() {
		for (String column : List.of("name", "description", "updated_at", "archived_at")) {
			assertTrue(jdbcTemplate.queryForObject("SELECT has_column_privilege(current_user, 'devpulse.projects', ?, 'UPDATE')", Boolean.class, column));
		}
		for (String column : List.of("id", "team_id", "created_by", "created_at")) {
			assertFalse(jdbcTemplate.queryForObject("SELECT has_column_privilege(current_user, 'devpulse.projects', ?, 'UPDATE')", Boolean.class, column));
		}
		assertFalse(jdbcTemplate.queryForObject("SELECT has_table_privilege(current_user, 'devpulse.projects', 'DELETE')", Boolean.class));
	}

	@Test
	void dashboardCountsUseCurrentUserAndExcludeArchivedAndOtherTeams() throws Exception {
		Task mine = taskService.createTask(teamId, projectId, "Priority work", null, Task.Priority.HIGH, memberId, ownerId);
		taskService.updateStatus(teamId, projectId, mine.id(), Task.Status.IN_PROGRESS, ownerId);
		Task done = createTask(projectId, memberId);
		taskService.updateStatus(teamId, projectId, done.id(), Task.Status.DONE, ownerId);
		taskService.createTask(teamId, projectId, "Unassigned", null, null, null, ownerId);
		Task stale = taskService.createTask(teamId, siblingProjectId, "Old work", null, null, ownerId, ownerId);
		jdbcTemplate.update("UPDATE devpulse.tasks SET updated_at = CURRENT_TIMESTAMP - INTERVAL '8 days' WHERE id = ?", stale.id());
		taskService.createTask(otherTeamId, otherProjectId, "Other team", null, null, memberId, outsiderId);
		String dashboardPath = "/api/teams/" + teamId + "/dashboard";
		mockMvc.perform(get(dashboardPath).header("Authorization", authorization(memberId)).param("userId", ownerId.toString()))
				.andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.team.total").value(4)).andExpect(jsonPath("$.team.open").value(3))
				.andExpect(jsonPath("$.team.unassignedOpen").value(1)).andExpect(jsonPath("$.team.staleOpen").value(1))
				.andExpect(jsonPath("$.personal.total").value(2)).andExpect(jsonPath("$.personal.open").value(1))
				.andExpect(jsonPath("$.personal.completed").value(1)).andExpect(jsonPath("$.personal.highPriorityOpen").value(1))
				.andExpect(jsonPath("$.personal.byStatus.IN_PROGRESS").value(1)).andExpect(jsonPath("$.personal.byStatus.IN_REVIEW").value(0))
				.andExpect(jsonPath("$.myOpenTasks.length()").value(1)).andExpect(jsonPath("$.myOpenTasks[0].id").value(mine.id().toString()))
				.andExpect(jsonPath("$.projects.length()").value(2)).andExpect(jsonPath("$.workload.length()").value(2));
		mockMvc.perform(get(dashboardPath).param("projectId", siblingProjectId.toString()).header("Authorization", authorization(memberId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.team.total").value(1)).andExpect(jsonPath("$.personal.total").value(0));
		projectService.setArchived(teamId, projectId, true, ownerId);
		mockMvc.perform(get(dashboardPath).header("Authorization", authorization(memberId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.team.total").value(1)).andExpect(jsonPath("$.personal.total").value(0))
				.andExpect(jsonPath("$.archivedProjects").value(1)).andExpect(jsonPath("$.projects.length()").value(1))
				.andExpect(jsonPath("$.myOpenTasks.length()").value(0));
		mockMvc.perform(get(dashboardPath).param("projectId", projectId.toString()).header("Authorization", authorization(memberId))).andExpect(status().isConflict());
		projectService.setArchived(teamId, projectId, false, ownerId);
		mockMvc.perform(get(dashboardPath).header("Authorization", authorization(memberId))).andExpect(status().isOk()).andExpect(jsonPath("$.team.total").value(4));
	}

	@Test
	void dashboardDeniesOutsidersRemovedMembersAndWrongProjects() throws Exception {
		String dashboardPath = "/api/teams/" + teamId + "/dashboard";
		mockMvc.perform(get(dashboardPath)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(dashboardPath).header("Authorization", authorization(outsiderId))).andExpect(status().isForbidden());
		mockMvc.perform(get(dashboardPath).header("Authorization", authorization(memberId)).param("projectId", otherProjectId.toString())).andExpect(status().isNotFound());
		mockMvc.perform(get(dashboardPath).header("Authorization", authorization(memberId)).param("projectId", "bad")).andExpect(status().isBadRequest());
		mockMvc.perform(get(dashboardPath).header("Authorization", authorization(ownerId))).andExpect(status().isOk())
				.andExpect(jsonPath("$.team.total").value(0)).andExpect(jsonPath("$.personal.open").value(0))
				.andExpect(jsonPath("$.team.byStatus.DONE").value(0)).andExpect(jsonPath("$.projects[0].total").value(0));
		String existingToken = authorization(memberId);
		teamService.revokeMember(teamId, memberId, ownerId);
		mockMvc.perform(get(dashboardPath).header("Authorization", existingToken)).andExpect(status().isForbidden());
	}

	private UUID createUser() {
		return userRepository.create("Task test", UUID.randomUUID() + "@example.test", "test-only-placeholder").id();
	}

	@Test
	void dashboardQueueIsBoundedPriorityOrderedAndDoesNotTruncateTotals() throws Exception {
		for (int index = 0; index < 21; index++) {
			taskService.createTask(teamId, projectId, "Low priority " + index, null, Task.Priority.LOW, memberId, ownerId);
		}
		Task urgent = taskService.createTask(teamId, projectId, "Urgent", null, Task.Priority.HIGH, memberId, ownerId);
		mockMvc.perform(get("/api/teams/" + teamId + "/dashboard").header("Authorization", authorization(memberId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.personal.open").value(22))
				.andExpect(jsonPath("$.myOpenTasks.length()").value(20))
				.andExpect(jsonPath("$.myOpenTasks[0].id").value(urgent.id().toString()))
				.andExpect(jsonPath("$.personal.openByPriority.HIGH").value(1))
				.andExpect(jsonPath("$.personal.openByPriority.LOW").value(21));
	}

	private void addMember(UUID selectedTeam, UUID userId) {
		jdbcTemplate.update("INSERT INTO devpulse.team_memberships (team_id, user_id, role) VALUES (?, ?, 'MEMBER')", selectedTeam, userId);
	}

	private String authorization(UUID userId) {
		return "Bearer " + tokenService.issue(new LoginService.LoginResult(userId, "Task test", "task@example.test", Instant.now())).token();
	}

	private String path(UUID selectedTeam, UUID selectedProject) {
		return "/api/teams/" + selectedTeam + "/projects/" + selectedProject + "/tasks";
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not release transaction.");
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}
}
