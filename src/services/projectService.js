import { apiRequest } from "./api";

export function listProjects(teamId, token) {
  return apiRequest(`/teams/${encodeURIComponent(teamId)}/projects`, { token });
}

export function createProject(teamId, name, description, token) {
  return apiRequest(`/teams/${encodeURIComponent(teamId)}/projects`, {
    method: "POST", body: { name, description: description || null }, token,
  });
}

export function updateProject(teamId, projectId, fields, token) {
  return apiRequest(`/teams/${encodeURIComponent(teamId)}/projects/${encodeURIComponent(projectId)}`, {
    method: "PUT", body: fields, token,
  });
}

export function setProjectArchived(teamId, projectId, archived, token) {
  return apiRequest(`/teams/${encodeURIComponent(teamId)}/projects/${encodeURIComponent(projectId)}/archive`, {
    method: "PATCH", body: { archived }, token,
  });
}