package com.devpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.devpulse.auth.InvalidCredentialsException;
import com.devpulse.auth.LoginService;
import com.devpulse.auth.SignupService;
import com.devpulse.project.ProjectRepository;
import com.devpulse.project.ProjectService;
import com.devpulse.team.TeamService;
import com.devpulse.user.User;
import com.devpulse.user.UserRepository;

@SpringBootTest(properties = "JWT_SECRET_BASE64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class DevPulseApplicationTests {

	@Autowired
	private org.springframework.test.web.servlet.MockMvc mockMvc;

	@Autowired
	private tools.jackson.databind.json.JsonMapper jsonMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Flyway flyway;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SignupService signupService;

	@Autowired
	private LoginService loginService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TeamService teamService;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private com.devpulse.auth.TokenService tokenService;

	@Value("${spring.flyway.user}")
	private String migrationUsername;

	@Test
	void contextLoads() {
	}

	@Test
	void databaseConnectionWorks() {
		assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Integer.class));
	}

	@Test
	void flywayUsesSeparateAccountAndExistingSchema() {
		JdbcTemplate migrationJdbcTemplate = new JdbcTemplate(flyway.getConfiguration().getDataSource());
		String applicationUser = jdbcTemplate.queryForObject("SELECT current_user", String.class);
		String migrationUser = migrationJdbcTemplate.queryForObject("SELECT current_user", String.class);

		assertEquals(migrationUsername, migrationUser);
		assertNotEquals(applicationUser, migrationUser);
		assertEquals("devpulse", flyway.getConfiguration().getDefaultSchema());
		assertFalse(flyway.getConfiguration().isCreateSchemas());
		assertEquals(0, flyway.info().pending().length);
	}

	@Test
	@Transactional
	void applicationCanInsertAndReadUsers() {
		UUID userId = UUID.randomUUID();
		String email = userId + "@example.test";

		assertEquals(1, jdbcTemplate.update(
				"INSERT INTO devpulse.users (id, name, email, password_hash) VALUES (?, ?, ?, ?)",
				userId, "Schema test", email, "test-only-placeholder"));

		var user = jdbcTemplate.queryForMap(
				"SELECT id, name, email, password_hash, created_at FROM devpulse.users WHERE id = ?", userId);
		assertEquals(userId, user.get("id"));
		assertEquals("Schema test", user.get("name"));
		assertEquals(email, user.get("email"));
		assertEquals("test-only-placeholder", user.get("password_hash"));
		assertNotNull(user.get("created_at"));
	}

	@ParameterizedTest
	@MethodSource("invalidUsers")
	@Transactional
	void usersRejectInvalidFields(String name, String email, String passwordHash) {
		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
				"INSERT INTO devpulse.users (id, name, email, password_hash) VALUES (?, ?, ?, ?)",
				UUID.randomUUID(), name, email, passwordHash));
	}

	static Stream<Arguments> invalidUsers() {
		return Stream.of(
				Arguments.of(null, "valid@example.test", "test-only-placeholder"),
				Arguments.of("   ", "valid@example.test", "test-only-placeholder"),
				Arguments.of("Name".repeat(26), "valid@example.test", "test-only-placeholder"),
				Arguments.of("Schema test", null, "test-only-placeholder"),
				Arguments.of("Schema test", "", "test-only-placeholder"),
				Arguments.of("Schema test", "MixedCase@example.test", "test-only-placeholder"),
				Arguments.of("Schema test", " padded@example.test ", "test-only-placeholder"),
				Arguments.of("Schema test", "email".repeat(64), "test-only-placeholder"),
				Arguments.of("Schema test", "valid@example.test", null),
				Arguments.of("Schema test", "valid@example.test", "hash".repeat(64)));
	}

	@Test
	@Transactional
	void usersRejectDuplicateEmail() {
		String email = UUID.randomUUID() + "@example.test";
		String insertUser = "INSERT INTO devpulse.users (id, name, email, password_hash) VALUES (?, ?, ?, ?)";
		jdbcTemplate.update(insertUser, UUID.randomUUID(), "First user", email, "test-only-placeholder");

		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
				insertUser, UUID.randomUUID(), "Second user", email, "test-only-placeholder"));
	}

	@Test
	@Transactional
	void repositoryCreatesAndFindsUser() {
		String email = UUID.randomUUID() + "@example.test";
		User createdUser = userRepository.create("O'Connor", email, "test-only-placeholder");

		assertNotNull(createdUser.id());
		assertEquals(4, createdUser.id().version());
		assertEquals("O'Connor", createdUser.name());
		assertEquals(email, createdUser.email());
		assertEquals("test-only-placeholder", createdUser.passwordHash());
		assertNotNull(createdUser.createdAt());
		assertEquals("User[id=" + createdUser.id() + "]", createdUser.toString());
		assertEquals(createdUser, userRepository.findByEmail(email).orElseThrow());
	}

	@Test
	@Transactional
	void repositoryReturnsEmptyForUnknownEmail() {
		assertEquals(java.util.Optional.empty(), userRepository.findByEmail(UUID.randomUUID() + "@example.test"));
	}

	@Test
	@Transactional
	void repositoryTreatsEmailAsData() {
		String email = UUID.randomUUID() + "@example.test";
		User createdUser = userRepository.create("Schema test", email, "test-only-placeholder");

		assertEquals(java.util.Optional.empty(), userRepository.findByEmail("' OR '1'='1"));
		assertEquals(createdUser, userRepository.findByEmail(email).orElseThrow());
	}

	@Test
	@Transactional
	void repositoryRejectsDuplicateEmail() {
		String email = UUID.randomUUID() + "@example.test";
		userRepository.create("First user", email, "test-only-placeholder");

		assertThrows(DuplicateKeyException.class,
				() -> userRepository.create("Second user", email, "test-only-placeholder"));
	}

	@Test
	void applicationHasOnlyRequiredUserTablePrivileges() {
		assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
				"SELECT has_schema_privilege(current_user, 'devpulse', 'USAGE')", Boolean.class));
		assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
				"SELECT has_schema_privilege(current_user, 'devpulse', 'CREATE')", Boolean.class));
		for (String privilege : new String[] { "SELECT", "INSERT" }) {
			assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
					"SELECT has_table_privilege(current_user, 'devpulse.users', ?)", Boolean.class, privilege));
		}
		for (String privilege : new String[] { "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER" }) {
			assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
					"SELECT has_table_privilege(current_user, 'devpulse.users', ?)", Boolean.class, privilege));
		}
	}

	@Test
	@Transactional
	void applicationCanCreateTeamsAndMemberships() {
		UUID ownerId = createTeamTestUser();
		UUID memberId = createTeamTestUser();
		UUID firstTeamId = createTestTeam("Platform");
		UUID secondTeamId = createTestTeam("Platform");
		insertMembership(firstTeamId, ownerId, "OWNER");
		insertMembership(firstTeamId, memberId, "MEMBER");
		insertMembership(secondTeamId, memberId, "OWNER");

		var team = jdbcTemplate.queryForMap("SELECT name, created_at FROM devpulse.teams WHERE id = ?", firstTeamId);
		assertEquals("Platform", team.get("name"));
		assertNotNull(team.get("created_at"));
		var membership = jdbcTemplate.queryForMap(
				"SELECT role, joined_at FROM devpulse.team_memberships WHERE team_id = ? AND user_id = ?",
				firstTeamId, ownerId);
		assertEquals("OWNER", membership.get("role"));
		assertNotNull(membership.get("joined_at"));
		assertEquals(2, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM devpulse.team_memberships WHERE user_id = ?", Integer.class, memberId));
	}

	@ParameterizedTest
	@MethodSource("invalidTeamNames")
	@Transactional
	void teamsRejectInvalidNames(String name) {
		assertThrows(DataIntegrityViolationException.class, () -> createTestTeam(name));
	}

	static Stream<Arguments> invalidTeamNames() {
		return Stream.of(Arguments.of((String) null), Arguments.of(""), Arguments.of("   "),
				Arguments.of("Name".repeat(26)));
	}

	@ParameterizedTest
	@MethodSource("invalidMembershipRoles")
	@Transactional
	void membershipsRejectInvalidRoles(String role) {
		UUID teamId = createTestTeam("Platform");
		UUID userId = createTeamTestUser();
		assertThrows(DataIntegrityViolationException.class, () -> insertMembership(teamId, userId, role));
	}

	static Stream<Arguments> invalidMembershipRoles() {
		return Stream.of(Arguments.of((String) null), Arguments.of(""), Arguments.of("ADMIN"),
				Arguments.of("owner"));
	}

	@Test
	@Transactional
	void membershipsRejectDuplicateUserInTeam() {
		UUID teamId = createTestTeam("Platform");
		UUID userId = createTeamTestUser();
		insertMembership(teamId, userId, "OWNER");
		assertThrows(DuplicateKeyException.class, () -> insertMembership(teamId, userId, "MEMBER"));
	}

	@Test
	@Transactional
	void membershipsRejectSecondOwner() {
		UUID teamId = createTestTeam("Platform");
		UUID ownerId = createTeamTestUser();
		UUID secondUserId = createTeamTestUser();
		insertMembership(teamId, ownerId, "OWNER");
		assertThrows(DuplicateKeyException.class, () -> insertMembership(teamId, secondUserId, "OWNER"));
	}

	@Test
	@Transactional
	void membershipsRequireExistingTeam() {
		UUID userId = createTeamTestUser();
		assertThrows(DataIntegrityViolationException.class,
				() -> insertMembership(UUID.randomUUID(), userId, "MEMBER"));
	}

	@Test
	@Transactional
	void membershipsRequireExistingUser() {
		UUID teamId = createTestTeam("Platform");
		assertThrows(DataIntegrityViolationException.class,
				() -> insertMembership(teamId, UUID.randomUUID(), "MEMBER"));
	}

	@Test
	void applicationHasOnlyRequiredTeamTablePrivileges() {
		for (String table : new String[] { "devpulse.teams", "devpulse.team_memberships" }) {
			for (String privilege : new String[] { "SELECT", "INSERT" }) {
				assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
						"SELECT has_table_privilege(current_user, ?, ?)", Boolean.class, table, privilege));
			}
			assertEquals(table.equals("devpulse.team_memberships"), jdbcTemplate.queryForObject(
					"SELECT has_table_privilege(current_user, ?, 'DELETE')", Boolean.class, table));
			for (String privilege : new String[] { "UPDATE", "TRUNCATE", "REFERENCES", "TRIGGER" }) {
				assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
						"SELECT has_table_privilege(current_user, ?, ?)", Boolean.class, table, privilege));
			}
		}
	}

	@ParameterizedTest
	@MethodSource("persistedTeamNames")
	@Transactional
	void teamServicePersistsTeamWithOwner(String name) {
		UUID ownerId = createTeamTestUser();
		var team = teamService.createTeam("  " + name + "  ", ownerId);

		assertEquals(4, team.id().version());
		assertEquals(name, team.name());
		assertNotNull(team.createdAt());
		assertEquals(name, jdbcTemplate.queryForObject(
				"SELECT name FROM devpulse.teams WHERE id = ?", String.class, team.id()));
		assertEquals(team.createdAt(), jdbcTemplate.queryForObject(
				"SELECT created_at FROM devpulse.teams WHERE id = ?", java.time.OffsetDateTime.class,
				team.id()).toInstant());
		var memberships = jdbcTemplate.queryForList(
				"SELECT user_id, role FROM devpulse.team_memberships WHERE team_id = ?", team.id());
		assertEquals(1, memberships.size());
		assertEquals(ownerId, memberships.getFirst().get("user_id"));
		assertEquals("OWNER", memberships.getFirst().get("role"));
	}

	static Stream<String> persistedTeamNames() {
		return Stream.of("O'Connor's team'); DROP TABLE devpulse.teams; --", "a".repeat(100));
	}

	@Test
	void teamServiceRollsBackTeamWhenOwnerMembershipFails() {
		String name = "Rollback " + UUID.randomUUID();
		assertThrows(DataIntegrityViolationException.class,
				() -> teamService.createTeam(name, UUID.randomUUID()));
		assertEquals(0, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM devpulse.teams WHERE name = ?", Integer.class, name));
	}

	@Test
	@Transactional
	void teamHttpCreationUsesAuthenticatedOwner() throws Exception {
		UUID ownerId = createTeamTestUser();
		UUID otherUserId = createTeamTestUser();
		String body = jsonMapper.writeValueAsString(java.util.Map.of(
				"name", "  Platform  ", "ownerId", otherUserId.toString(), "role", "MEMBER"));
		var response = mockMvc.perform(post("/api/teams").header("Authorization", teamAuthorization(ownerId))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Platform"))
				.andReturn().getResponse();
		UUID teamId = UUID.fromString(jsonMapper.readTree(response.getContentAsString()).get("id").asText());
		var memberships = jdbcTemplate.queryForList(
				"SELECT user_id, role FROM devpulse.team_memberships WHERE team_id = ?", teamId);
		assertEquals(1, memberships.size());
		assertEquals(ownerId, memberships.getFirst().get("user_id"));
		assertEquals("OWNER", memberships.getFirst().get("role"));
		mockMvc.perform(get("/api/teams").header("Authorization", teamAuthorization(ownerId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(teamId.toString()));
		mockMvc.perform(get("/api/teams").header("Authorization", teamAuthorization(otherUserId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@Transactional
	void teamHttpListingIncludesMembershipsButExcludesOtherTeams() throws Exception {
		UUID firstUserId = createTeamTestUser();
		UUID secondUserId = createTeamTestUser();
		UUID outsiderId = createTeamTestUser();
		var sharedTeam = teamService.createTeam("Shared", firstUserId);
		var privateTeam = teamService.createTeam("Private", firstUserId);
		var secondTeam = teamService.createTeam("Second", secondUserId);
		insertMembership(sharedTeam.id(), secondUserId, "MEMBER");

		mockMvc.perform(get("/api/teams").header("Authorization", teamAuthorization(firstUserId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.containsInAnyOrder(
						sharedTeam.id().toString(), privateTeam.id().toString())));
		mockMvc.perform(get("/api/teams").param("userId", firstUserId.toString())
				.header("Authorization", teamAuthorization(secondUserId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.containsInAnyOrder(
						sharedTeam.id().toString(), secondTeam.id().toString())));
		mockMvc.perform(get("/api/teams").param("userId", firstUserId.toString())
				.header("Authorization", teamAuthorization(outsiderId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@Transactional
	void ownerRevocationRemovesAccessWithExistingTokenButPreservesOtherTeams() throws Exception {
		UUID ownerId = createTeamTestUser();
		UUID memberId = createTeamTestUser();
		var team = teamService.createTeam("Revocation test", ownerId);
		var otherTeam = teamService.createTeam("Other team", memberId);
		insertMembership(team.id(), memberId, "MEMBER");
		String memberAuthorization = teamAuthorization(memberId);
		mockMvc.perform(get("/api/teams/{teamId}/members", team.id()).header("Authorization", memberAuthorization))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].userId").value(ownerId.toString()))
				.andExpect(jsonPath("$[0].role").value("OWNER"))
				.andExpect(jsonPath("$[1].userId").value(memberId.toString()))
				.andExpect(jsonPath("$[1].email").isNotEmpty())
				.andExpect(jsonPath("$[1].passwordHash").doesNotExist());
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", team.id(), memberId)
				.header("Authorization", teamAuthorization(ownerId))).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/teams/{teamId}/members", team.id()).header("Authorization", memberAuthorization))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/teams").header("Authorization", memberAuthorization))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(otherTeam.id().toString()));
		mockMvc.perform(get("/api/teams/{teamId}/members", team.id()).header("Authorization", teamAuthorization(ownerId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM devpulse.users WHERE id = ?", Integer.class, memberId));
	}

	@Test
	@Transactional
	void rosterAndRemovalStayScopedToOwnerAndTeam() throws Exception {
		UUID ownerId = createTeamTestUser();
		UUID memberId = createTeamTestUser();
		UUID outsiderId = createTeamTestUser();
		var team = teamService.createTeam("Protected team", ownerId);
		var otherTeam = teamService.createTeam("Another team", outsiderId);
		insertMembership(team.id(), memberId, "MEMBER");
		insertMembership(otherTeam.id(), memberId, "MEMBER");
		mockMvc.perform(get("/api/teams/{teamId}/members", team.id()).header("Authorization", teamAuthorization(outsiderId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", team.id(), memberId)
				.header("Authorization", teamAuthorization(memberId))).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", otherTeam.id(), memberId)
				.header("Authorization", teamAuthorization(ownerId))).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", team.id(), ownerId)
				.header("Authorization", teamAuthorization(ownerId))).andExpect(status().isConflict());
		mockMvc.perform(delete("/api/teams/{teamId}/members/{memberId}", team.id(), UUID.randomUUID())
				.header("Authorization", teamAuthorization(ownerId))).andExpect(status().isNotFound());
		assertEquals(2, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM devpulse.team_memberships WHERE user_id = ?", Integer.class, memberId));
	}

	@Test
	@Transactional
	void projectHttpCreationPersistsRouteTeamAndAuthenticatedCreator() throws Exception {
		UUID ownerId = createTeamTestUser();
		UUID otherOwnerId = createTeamTestUser();
		var team = teamService.createTeam("Project team", ownerId);
		var otherTeam = teamService.createTeam("Other team", otherOwnerId);
		String name = "O'Connor's portal'); DROP TABLE devpulse.projects; --";
		String body = jsonMapper.writeValueAsString(java.util.Map.of("name", "  " + name + "  ",
				"description", "  Customer portal  ", "teamId", otherTeam.id(), "createdBy", otherOwnerId));
		var response = mockMvc.perform(post("/api/teams/{teamId}/projects", team.id())
				.header("Authorization", teamAuthorization(ownerId))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.name").value(name))
				.andExpect(jsonPath("$.description").value("Customer portal"))
				.andExpect(jsonPath("$.teamId").value(team.id().toString()))
				.andExpect(jsonPath("$.createdBy").value(ownerId.toString()))
				.andReturn().getResponse();
		UUID projectId = UUID.fromString(jsonMapper.readTree(response.getContentAsString()).get("id").asText());
		var projects = projectService.listProjects(team.id(), ownerId);
		assertEquals(1, projects.size());
		assertEquals(projectId, projects.getFirst().id());
		assertEquals(4, projectId.version());
		assertNotNull(projects.getFirst().createdAt());
		assertEquals(ownerId, projects.getFirst().createdBy());
		assertEquals(0, projectService.listProjects(otherTeam.id(), otherOwnerId).size());
	}

	@Test
	@Transactional
	void projectAccessFollowsMembershipAndTeamScopeWithExistingToken() throws Exception {
		UUID ownerId = createTeamTestUser();
		UUID memberId = createTeamTestUser();
		UUID outsiderId = createTeamTestUser();
		var team = teamService.createTeam("Shared projects", ownerId);
		var otherTeam = teamService.createTeam("Other projects", memberId);
		insertMembership(team.id(), memberId, "MEMBER");
		var project = projectService.createProject(team.id(), "Portal", null, ownerId);
		var otherProject = projectService.createProject(otherTeam.id(), "Internal tools", null, memberId);
		String memberAuthorization = teamAuthorization(memberId);
		mockMvc.perform(get("/api/teams/{teamId}/projects", team.id()).header("Authorization", memberAuthorization))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(project.id().toString()));
		mockMvc.perform(post("/api/teams/{teamId}/projects", team.id()).header("Authorization", memberAuthorization)
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"name\":\"Forbidden\"}"))
				.andExpect(status().isForbidden());
		for (UUID requester : new UUID[] { ownerId, outsiderId }) {
			mockMvc.perform(get("/api/teams/{teamId}/projects", otherTeam.id())
					.header("Authorization", teamAuthorization(requester))).andExpect(status().isForbidden());
			mockMvc.perform(post("/api/teams/{teamId}/projects", otherTeam.id())
					.header("Authorization", teamAuthorization(requester))
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"name\":\"Forbidden\"}"))
					.andExpect(status().isForbidden());
		}
		teamService.revokeMember(team.id(), memberId, ownerId);
		mockMvc.perform(get("/api/teams/{teamId}/projects", team.id()).header("Authorization", memberAuthorization))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/teams/{teamId}/projects", otherTeam.id()).header("Authorization", memberAuthorization))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(otherProject.id().toString()));
		assertEquals(java.util.List.of(project), projectService.listProjects(team.id(), ownerId));
	}

	@Test
	@Transactional
	void projectRepositoryRechecksPermissionsEvenWithoutServiceChecks() {
		UUID ownerId = createTeamTestUser();
		UUID memberId = createTeamTestUser();
		var team = teamService.createTeam("Repository permissions", ownerId);
		insertMembership(team.id(), memberId, "MEMBER");
		projectService.createProject(team.id(), "Portal", null, ownerId);
		assertEquals(java.util.Optional.empty(), projectRepository.create(team.id(), "Forbidden", null, memberId));
		teamService.revokeMember(team.id(), memberId, ownerId);
		assertEquals(java.util.List.of(), projectRepository.findByTeamId(team.id(), memberId));
		assertEquals(java.util.Optional.empty(), projectRepository.create(team.id(), "Forbidden", null, memberId));
		assertEquals(1, projectRepository.findByTeamId(team.id(), ownerId).size());
	}

	@Test
	@Transactional
	void projectBoundaryFieldsPersistAndListsHaveStableOrdering() {
		UUID ownerId = createTeamTestUser();
		var team = teamService.createTeam("Ordered projects", ownerId);
		var first = projectService.createProject(team.id(), "n".repeat(100), "d".repeat(2000), ownerId);
		var second = projectService.createProject(team.id(), "Another", null, ownerId);
		assertEquals("n".repeat(100), first.name());
		assertEquals("d".repeat(2000), first.description());
		var expected = java.util.stream.Stream.of(first, second)
				.sorted(java.util.Comparator.comparing(com.devpulse.project.Project::createdAt).reversed()
						.thenComparing(project -> project.id().toString())).toList();
		assertEquals(expected, projectService.listProjects(team.id(), ownerId));
	}

	@ParameterizedTest
	@MethodSource("invalidProjectFields")
	@Transactional
	void projectsRejectInvalidFields(String name, String description) {
		UUID ownerId = createTeamTestUser();
		UUID teamId = createTestTeam("Project constraints");
		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
				"INSERT INTO devpulse.projects (id, team_id, name, description, created_by) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), teamId, name, description, ownerId));
	}

	static Stream<Arguments> invalidProjectFields() {
		return Stream.of(Arguments.of(null, null), Arguments.of("", null), Arguments.of("   ", null),
				Arguments.of("n".repeat(101), null), Arguments.of("Portal", "d".repeat(2001)));
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { true, false })
	@Transactional
	void projectsRequireOneExistingTeam(boolean nullTeam) {
		UUID ownerId = createTeamTestUser();
		UUID teamId = nullTeam ? null : UUID.randomUUID();
		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
				"INSERT INTO devpulse.projects (id, team_id, name, created_by) VALUES (?, ?, ?, ?)",
				UUID.randomUUID(), teamId, "Portal", ownerId));
	}

	@Test
	void applicationHasOnlyRequiredProjectTablePrivileges() {
		for (String privilege : new String[] { "SELECT", "INSERT" }) {
			assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
					"SELECT has_table_privilege(current_user, 'devpulse.projects', ?)", Boolean.class, privilege));
		}
		for (String privilege : new String[] { "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER" }) {
			assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
					"SELECT has_table_privilege(current_user, 'devpulse.projects', ?)", Boolean.class, privilege));
		}
	}

	private String teamAuthorization(UUID userId) {
		return "Bearer " + tokenService.issue(new LoginService.LoginResult(userId, "Team test",
				"team@example.test", java.time.Instant.now())).token();
	}

	private UUID createTeamTestUser() {
		return userRepository.create("Team test", UUID.randomUUID() + "@example.test", "test-only-placeholder").id();
	}

	private UUID createTestTeam(String name) {
		UUID teamId = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO devpulse.teams (id, name) VALUES (?, ?)", teamId, name);
		return teamId;
	}

	private void insertMembership(UUID teamId, UUID userId, String role) {
		jdbcTemplate.update("INSERT INTO devpulse.team_memberships (team_id, user_id, role) VALUES (?, ?, ?)",
				teamId, userId, role);
	}

	@Test
	@Transactional
	void signupServicePersistsNormalizedUserWithHashedPassword() {
		String email = UUID.randomUUID() + "@example.test";
		String password = "a sufficiently long password";
		var result = signupService.signup("  Alex  ", "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", password);
		User storedUser = userRepository.findByEmail(email).orElseThrow();

		assertEquals(result.id(), storedUser.id());
		assertEquals("Alex", result.name());
		assertEquals(email, result.email());
		assertEquals(result.createdAt(), storedUser.createdAt());
		assertNotEquals(password, storedUser.passwordHash());
		assertEquals(true, passwordEncoder.matches(password, storedUser.passwordHash()));
	}

	@Test
	@Transactional
	void loginVerifiesPasswordStoredBySignup() {
		String email = UUID.randomUUID() + "@example.test";
		String password = "A sufficiently long password!";
		var signup = signupService.signup("Alex", email, password);
		var login = loginService.login("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", password);

		assertEquals(signup.id(), login.id());
		assertEquals(signup.name(), login.name());
		assertEquals(signup.email(), login.email());
		assertEquals(signup.createdAt(), login.createdAt());
		assertThrows(InvalidCredentialsException.class, () -> loginService.login(email, "Wrong password"));
		assertThrows(InvalidCredentialsException.class,
				() -> loginService.login(UUID.randomUUID() + "@example.test", password));
	}

	@Test
	@Transactional
	void signupLoginAndProtectedRequestWorkThroughHttp() throws Exception {
		String email = UUID.randomUUID() + "@example.test";
		String password = "Disposable integration password!";
		String signupBody = jsonMapper.writeValueAsString(java.util.Map.of("name", "HTTP Test", "email", email, "password", password));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/signup")
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(signupBody))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
		String loginBody = jsonMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
		var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(loginBody))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andReturn().getResponse();
		var session = jsonMapper.readTree(response.getContentAsString());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
				.header("Authorization", "Bearer " + session.get("token").asText()))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id")
						.value(session.get("user").get("id").asText()));
	}

}
