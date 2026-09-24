import { apiRequest } from "./api";

export function getDashboard(teamId, projectId, token) {
  const query = projectId ? `?${new URLSearchParams({ projectId })}` : "";
  return apiRequest(`/teams/${encodeURIComponent(teamId)}/dashboard${query}`, { token });
}