import { apiRequest } from "./api";

export const taskStatuses = [["TODO", "To Do"], ["IN_PROGRESS", "In Progress"], ["IN_REVIEW", "In Review"], ["DONE", "Done"]];
export const taskPriorities = [["LOW", "Low"], ["MEDIUM", "Medium"], ["HIGH", "High"]];

function taskPath(teamId, projectId) {
  return `/teams/${encodeURIComponent(teamId)}/projects/${encodeURIComponent(projectId)}/tasks`;
}

export function listTasks(teamId, projectId, token) {
  return apiRequest(taskPath(teamId, projectId), { token });
}

export function createTask(teamId, projectId, task, token) {
  return apiRequest(taskPath(teamId, projectId), { method: "POST", body: task, token });
}

export function updateTask(teamId, projectId, taskId, task, token) {
  return apiRequest(`${taskPath(teamId, projectId)}/${encodeURIComponent(taskId)}`, { method: "PUT", body: task, token });
}

export function updateTaskStatus(teamId, projectId, taskId, status, token) {
  return apiRequest(`${taskPath(teamId, projectId)}/${encodeURIComponent(taskId)}/status`, {
    method: "PATCH", body: { status }, token,
  });
}