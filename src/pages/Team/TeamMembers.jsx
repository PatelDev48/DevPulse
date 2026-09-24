import { useEffect, useEffectEvent, useRef, useState } from "react";
import { ShieldCheck, UserRoundMinus, X } from "lucide-react";
import Button from "../../components/Button/Button";
import { useAuth } from "../../context/AuthContext";
import { listMembers, revokeMember } from "../../services/teamService";

export default function TeamMembers({ team, onClose }) {
  const { token, user, logout } = useAuth();
  const dialogRef = useRef(null);
  const closeRef = useRef(null);
  const revokeTrigger = useRef(null);
  const revokeButtons = useRef(new Map());
  const pendingRef = useRef(false);
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [selected, setSelected] = useState(null);
  const [pending, setPending] = useState(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  const expireSession = useEffectEvent(() => logout());
  const isOwner = members.some((member) => member.userId === user.id && member.role === "OWNER");

  useEffect(() => {
    const dialog = dialogRef.current;
    const trigger = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      dialog.close();
      document.body.style.overflow = previousOverflow;
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
    };
  }, []);

  useEffect(() => {
    let active = true;
    listMembers(team.id, token)
      .then((result) => { if (active) setMembers(result); })
      .catch((error) => {
        if (!active) return;
        if (error.status === 401) expireSession();
        else setLoadError(error.message || "Unable to load members.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [team.id, token, attempt]);

  useEffect(() => {
    if (!selected && !pending && revokeTrigger.current) {
      const trigger = revokeButtons.current.get(revokeTrigger.current);
      (trigger ?? closeRef.current)?.focus();
    }
  }, [selected, pending]);

  const cancelConfirmation = () => {
    if (pendingRef.current) return;
    setSelected(null);
    setActionError("");
  };

  const dismissDialog = (event) => {
    event.preventDefault();
    if (pendingRef.current) return;
    if (selected) cancelConfirmation();
    else onClose();
  };

  const confirmRevoke = async () => {
    if (pendingRef.current || !selected || !isOwner) return;
    pendingRef.current = true;
    setPending(true);
    setActionError("");
    const member = selected;
    try {
      await revokeMember(team.id, member.userId, token);
      setMembers((current) => current.filter((item) => item.userId !== member.userId));
      setSelected(null);
      setNotice(`Access revoked for ${member.name}.`);
    } catch (error) {
      if (error.status === 401) logout();
      else if (error.status === 404) {
        setMembers((current) => current.filter((item) => item.userId !== member.userId));
        setSelected(null);
        setNotice("This member no longer belongs to the team.");
      } else if (error.status === 403) {
        setSelected(null);
        setMembers([]);
        setLoadError("Your team permissions changed. Reload the member list.");
      } else setActionError(error.message || "Unable to revoke access.");
    } finally {
      pendingRef.current = false;
      setPending(false);
    }
  };

  return (
    <dialog ref={dialogRef} className="members-dialog" aria-labelledby="members-dialog-title"
      aria-describedby={selected ? "member-confirm-description" : undefined}
      onCancel={dismissDialog}
      onKeyDown={(event) => {
        if (event.key === "Escape") dismissDialog(event);
      }}>
      <header className="members-dialog-header">
        <div>
          <p className="section-kicker">{team.name}</p>
          <h2 id="members-dialog-title">{selected ? "Revoke member access?" : "Team members"}</h2>
        </div>
        <button ref={closeRef} type="button" className="members-close" aria-label="Close member dialog"
          title="Close member dialog" disabled={pending} onClick={onClose}><X size={20} aria-hidden="true" /></button>
      </header>

      {selected ? (
        <MemberConfirmation member={selected} team={team} pending={pending} error={actionError}
          onCancel={cancelConfirmation} onConfirm={confirmRevoke} />
      ) : (
        <div className="members-dialog-body" aria-busy={loading}>
          {loading ? <p role="status">Loading members...</p> : loadError ? (
            <div>
              <p className="team-error" role="alert">{loadError}</p>
              <Button onClick={() => { setLoading(true); setLoadError(""); setAttempt((value) => value + 1); }}>Try again</Button>
            </div>
          ) : (
            <>
              <p className="members-count">{members.length} {members.length === 1 ? "member" : "members"}</p>
              <ul className="members-list" aria-label="Team members">
                {members.map((member) => (
                  <li className="member-row" key={member.userId}>
                    <div className="member-identity">
                      <strong>{member.name}{member.userId === user.id ? " (you)" : ""}</strong>
                      <span>{member.email}</span>
                    </div>
                    <span className="member-role">{member.role === "OWNER" && <ShieldCheck size={16} aria-hidden="true" />}
                      {member.role === "OWNER" ? "Owner" : "Member"}</span>
                    {isOwner && member.role === "MEMBER" && (
                      <button type="button" className="member-revoke" aria-label={`Revoke access for ${member.name}`}
                        ref={(element) => {
                          if (element) revokeButtons.current.set(member.userId, element);
                          else revokeButtons.current.delete(member.userId);
                        }}
                        onClick={() => {
                          revokeTrigger.current = member.userId;
                          setActionError(""); setNotice(""); setSelected(member);
                        }}><UserRoundMinus size={16} aria-hidden="true" />Revoke access</button>
                    )}
                  </li>
                ))}
              </ul>
              {members.length === 0 && <p>No members available.</p>}
            </>
          )}
          <p className="team-notice" role="status">{notice}</p>
        </div>
      )}
    </dialog>
  );
}

function MemberConfirmation({ member, team, pending, error, onCancel, onConfirm }) {
  const cancelRef = useRef(null);
  useEffect(() => { cancelRef.current?.focus(); }, []);

  return (
    <div className="members-dialog-body" aria-busy={pending}>
      <p id="member-confirm-description">Remove <strong>{member.name}</strong> ({member.email}) from <strong>{team.name}</strong>?</p>
      <p>They will lose access to this team. Their account and other teams will not be affected.</p>
      {error && <p className="team-error" role="alert">{error}</p>}
      <div className="member-confirm-actions">
        <button ref={cancelRef} type="button" className="member-cancel" disabled={pending} onClick={onCancel}>Cancel</button>
        <button type="button" className="member-confirm" disabled={pending} onClick={onConfirm}>
          <UserRoundMinus size={18} aria-hidden="true" />{pending ? "Revoking..." : "Confirm revoke"}
        </button>
      </div>
    </div>
  );
}