import { useEffect, useRef, useState } from "react";
import { Copy, Link2Off, UserPlus } from "lucide-react";
import Button from "../../components/Button/Button";
import { useAuth } from "../../context/AuthContext";
import { createInvitation, revokeInvitation } from "../../services/invitationService";

export default function TeamInvitation({ team }) {
  const { token, logout } = useAuth();
  const [invitation, setInvitation] = useState(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const pendingRef = useRef(false);

  const perform = async (action) => {
    if (pendingRef.current) return;
    pendingRef.current = true;
    setPending(true);
    setError("");
    setNotice("");
    try {
      if (action === "create") {
        setInvitation(await createInvitation(team.id, token));
      } else {
        await revokeInvitation(team.id, invitation.id, token);
        setInvitation(null);
        setNotice("Invitation revoked.");
      }
    } catch (failure) {
      if (failure.status === 401) logout();
      else if (action === "revoke" && failure.status === 400) {
        setInvitation(null);
        setNotice("Invitation is no longer active.");
      } else setError(failure.message || "Unable to update invitation.");
    } finally {
      pendingRef.current = false;
      setPending(false);
    }
  };

  return (
    <div className="team-invitation" aria-label={`Invitations for ${team.name}`}>
      {invitation ? (
        <InvitationLink key={invitation.id} invitation={invitation} pending={pending}
          onRevoke={() => perform("revoke")} />
      ) : (
        <Button onClick={() => perform("create")} disabled={pending} className="invitation-command">
          <UserPlus size={18} aria-hidden="true" />{pending ? "Creating..." : "Invite member"}
        </Button>
      )}
      {error && <p className="team-error" role="alert">{error}</p>}
      {notice && <p className="team-notice" role="status">{notice}</p>}
    </div>
  );
}

function InvitationLink({ invitation, pending, onRevoke }) {
  const link = `${window.location.origin}/invite#${invitation.token}`;
  const [expired, setExpired] = useState(() => Date.parse(invitation.expiresAt) <= Date.now());
  const [copyStatus, setCopyStatus] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => setExpired(true), Math.max(0, Date.parse(invitation.expiresAt) - Date.now()));
    return () => clearTimeout(timer);
  }, [invitation.expiresAt]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopyStatus("Link copied.");
    } catch {
      setCopyStatus("Clipboard unavailable. Select and copy the invitation link.");
    }
  };

  return (
    <div className="invitation-link">
      <label htmlFor={`invite-${invitation.id}`}>Private invitation link</label>
      <div className="invitation-link-controls">
        <input id={`invite-${invitation.id}`} readOnly value={expired ? "Invitation expired" : link}
          onFocus={(event) => event.target.select()} />
        <Button onClick={copy} disabled={pending || expired} className="invitation-icon"
          aria-label="Copy invitation link" title="Copy invitation link"><Copy size={18} aria-hidden="true" /></Button>
        <Button onClick={onRevoke} disabled={pending} className="invitation-command">
          <Link2Off size={18} aria-hidden="true" />{pending ? "Revoking..." : expired ? "Dismiss" : "Revoke"}
        </Button>
      </div>
      <p className="invitation-meta">{expired ? "Expired" : `Expires ${new Date(invitation.expiresAt).toLocaleString()}`} · Single use</p>
      <p className="invitation-meta">Anyone with this link can join as a member.</p>
      <p className="team-notice" role="status">{copyStatus}</p>
    </div>
  );
}