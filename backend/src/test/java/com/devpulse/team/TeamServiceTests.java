package com.devpulse.team;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

class TeamServiceTests {

	private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

	private TeamRepository teamRepository;
	private TeamService teamService;

	@BeforeEach
	void setUp() {
		teamRepository = mock(TeamRepository.class);
		teamService = new TeamService(teamRepository, VALIDATOR_FACTORY.getValidator());
	}

	@AfterAll
	static void closeValidatorFactory() {
		VALIDATOR_FACTORY.close();
	}

	@Test
	void createsNormalizedTeamThenAddsOwner() {
		UUID ownerId = UUID.randomUUID();
		Team team = new Team(UUID.randomUUID(), "Platform", Instant.now());
		when(teamRepository.create("Platform")).thenReturn(team);

		assertSame(team, teamService.createTeam(" \tPlatform\n ", ownerId));

		var order = inOrder(teamRepository);
		order.verify(teamRepository).create("Platform");
		order.verify(teamRepository).addOwner(team.id(), ownerId);
		verifyNoMoreInteractions(teamRepository);
	}

	@ParameterizedTest
	@MethodSource("invalidInputs")
	void rejectsInvalidInputBeforeDatabaseAccess(String name, UUID ownerId) {
		assertThrows(IllegalArgumentException.class, () -> teamService.createTeam(name, ownerId));
		verifyNoInteractions(teamRepository);
	}

	static Stream<Arguments> invalidInputs() {
		UUID ownerId = UUID.randomUUID();
		return Stream.of(Arguments.of(null, ownerId), Arguments.of("", ownerId),
				Arguments.of(" \t\n ", ownerId), Arguments.of("a".repeat(101), ownerId),
				Arguments.of("Platform", null));
	}

	@ParameterizedTest
	@MethodSource("boundaryNames")
	void acceptsNameLengthBoundaries(String name) {
		UUID ownerId = UUID.randomUUID();
		Team team = new Team(UUID.randomUUID(), name, Instant.now());
		when(teamRepository.create(name)).thenReturn(team);
		assertSame(team, teamService.createTeam(" " + name + " ", ownerId));
	}

	static Stream<String> boundaryNames() {
		return Stream.of("A", "a".repeat(100));
	}

	@Test
	void teamInsertFailurePropagatesWithoutAddingOwner() {
		var failure = new DataAccessResourceFailureException("Database unavailable");
		when(teamRepository.create("Platform")).thenThrow(failure);
		assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
				() -> teamService.createTeam("Platform", UUID.randomUUID())));
		org.mockito.Mockito.verify(teamRepository).create("Platform");
		verifyNoMoreInteractions(teamRepository);
	}

	@Test
	void membershipFailurePropagatesToTransactionBoundary() {
		UUID ownerId = UUID.randomUUID();
		Team team = new Team(UUID.randomUUID(), "Platform", Instant.now());
		when(teamRepository.create("Platform")).thenReturn(team);
		var failure = new DataIntegrityViolationException("Owner does not exist");
		doThrow(failure).when(teamRepository).addOwner(team.id(), ownerId);
		assertSame(failure, assertThrows(DataIntegrityViolationException.class,
				() -> teamService.createTeam("Platform", ownerId)));
	}
}