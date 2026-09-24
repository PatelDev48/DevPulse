package com.devpulse.team;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

	private final TeamRepository teamRepository;
	private final Validator validator;

	public TeamService(TeamRepository teamRepository, Validator validator) {
		this.teamRepository = teamRepository;
		this.validator = validator;
	}

	@Transactional
	public Team createTeam(String name, UUID ownerId) {
		String normalizedName = name == null ? null : name.strip();
		if (!validator.validate(new CreateTeamInput(normalizedName, ownerId)).isEmpty()) {
			throw new IllegalArgumentException("A team name of 1 to 100 characters and an owner ID are required.");
		}

		Team team = teamRepository.create(normalizedName);
		teamRepository.addOwner(team.id(), ownerId);
		return team;
	}

	public List<TeamMembership> listTeams(UUID userId) {
		if (userId == null) {
			throw new IllegalArgumentException("A user ID is required.");
		}
		return teamRepository.findByMemberId(userId);
	}

	public List<TeamMember> listMembers(UUID teamId, UUID userId) {
		if (teamRepository.findRole(teamId, userId).isEmpty()) {
			throw new AccessDeniedException("Team membership is required.");
		}
		return teamRepository.findMembers(teamId, userId);
	}

	@Transactional
	public void revokeMember(UUID teamId, UUID memberId, UUID ownerId) {
		teamRepository.lockTaskMembership(teamId);
		if (!teamRepository.findRole(teamId, ownerId).filter("OWNER"::equals).isPresent()) {
			throw new AccessDeniedException("Only the team owner can revoke access.");
		}
		if (ownerId.equals(memberId)) {
			throw new OwnerRemovalException();
		}
		if (!teamRepository.removeMember(teamId, memberId, ownerId)) {
			throw new MemberNotFoundException();
		}
		teamRepository.clearTaskAssignments(teamId, memberId);
	}

	public static class OwnerRemovalException extends RuntimeException {
		public OwnerRemovalException() {
			super("The team owner cannot be removed.");
		}
	}

	public static class MemberNotFoundException extends RuntimeException {
		public MemberNotFoundException() {
			super("Team member not found.");
		}
	}

	private record CreateTeamInput(@NotBlank @Size(max = 100) String name, @NotNull UUID ownerId) {
	}
}