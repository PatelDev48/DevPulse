import { apiRequest } from "./api";

export function listTeams(token) {
  return apiRequest("/teams", { token });
}

export function createTeam(name, token) {
  return apiRequest("/teams", { method: "POST", body: { name }, token });
}

export function listMembers(teamId, token) {
  return apiRequest(`/teams/${encodeURIComponent(teamId)}/members`, { token });
}

export function revokeMember(teamId, memberId, token) {
  return apiRequest(`/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(memberId)}`,
    { method: "DELETE", token });
}