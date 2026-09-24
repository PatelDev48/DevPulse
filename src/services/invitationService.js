import { apiRequest } from "./api";

export function createInvitation(teamId, token) {
  return apiRequest(`/teams/${encodeURIComponent(teamId)}/invitations`, { method: "POST", token });
}

export function revokeInvitation(teamId, invitationId, token) {
  return apiRequest(`/teams/${encodeURIComponent(teamId)}/invitations/${encodeURIComponent(invitationId)}`,
    { method: "DELETE", token });
}

export function acceptInvitation(invitationToken, token) {
  return apiRequest("/invitations/accept", { method: "POST", body: { token: invitationToken }, token });
}