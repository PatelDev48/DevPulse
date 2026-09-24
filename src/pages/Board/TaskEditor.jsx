import { useEffect, useEffectEvent, useRef, useState } from "react";
import { RefreshCw, Save, X } from "lucide-react";
import Button from "../../components/Button/Button";
import { useAuth } from "../../context/AuthContext";
import { listMembers } from "../../services/teamService";
import { taskStatuses, taskPriorities } from "../../services/taskService";

export default function TaskEditor({ task, teamId, busy, error, onSave, onClose, fallbackFocus, onAccessFailure, onMembers }) {
  const { token } = useAuth();
  const dialogRef = useRef(null);
  const titleRef = useRef(null);
  const [title, setTitle] = useState(task?.title ?? "");
  const [description, setDescription] = useState(task?.description ?? "");
  const [status, setStatus] = useState(task?.status ?? "TODO");
  const [priority, setPriority] = useState(task?.priority ?? "MEDIUM");
  const [assigneeId, setAssigneeId] = useState(task?.assigneeId ?? "");
  const [validationError, setValidationError] = useState("");
  const [members, setMembers] = useState([]);
  const [membersLoading, setMembersLoading] = useState(true);
  const [membersError, setMembersError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const reportMembers = useEffectEvent((result) => onMembers(result));
  const reportAccess = useEffectEvent((failure) => onAccessFailure(failure));

  useEffect(() => {
    const dialog = dialogRef.current;
    const trigger = document.activeElement;
    const fallback = fallbackFocus.current;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal(); titleRef.current?.focus();
    document.body.style.overflow = "hidden";
    return () => {
      dialog.close(); document.body.style.overflow = previousOverflow;
      if (trigger instanceof HTMLElement && trigger.isConnected && !trigger.disabled) trigger.focus();
      else fallback?.focus();
    };
  }, [fallbackFocus]);

  useEffect(() => {
    let active = true;
    listMembers(teamId, token).then((result) => {
      if (active) { setMembers(result); reportMembers(result); }
    }).catch((failure) => {
      if (active && !reportAccess(failure)) setMembersError(failure.message || "Unable to load team members.");
    }).finally(() => { if (active) setMembersLoading(false); });
    return () => { active = false; };
  }, [teamId, token, attempt]);

  const dismiss = (event) => {
    event.preventDefault();
    if (!busy) onClose();
  };
  const unavailableAssignee = assigneeId && !members.some((member) => member.userId === assigneeId);

  const submit = (event) => {
    event.preventDefault();
    if (busy || membersLoading || membersError) return;
    const normalizedTitle = title.trim();
    const normalizedDescription = description.trim();
    if (!normalizedTitle || normalizedTitle.length > 200 || normalizedDescription.length > 5000) {
      setValidationError("Enter a title of 1 to 200 characters and a description of at most 5000 characters.");
      return;
    }
    if (unavailableAssignee) { setValidationError("Choose a current team member or Unassigned."); return; }
    setValidationError("");
    const fields = { title: normalizedTitle, description: normalizedDescription || null, priority, assigneeId: assigneeId || null };
    if (task) fields.status = status;
    void onSave(fields);
  };

  return (
    <dialog className="task-dialog" ref={dialogRef} aria-labelledby="task-dialog-title" onCancel={dismiss}
      onKeyDown={(event) => { if (event.key === "Escape") dismiss(event); }}>
      <header className="task-dialog-header">
        <h2 id="task-dialog-title">{task ? "Edit task" : "New task"}</h2>
        <button type="button" className="board-icon" title="Close task dialog" aria-label="Close task dialog" disabled={busy} onClick={onClose}>
          <X size={20} aria-hidden="true" />
        </button>
      </header>
      <form onSubmit={submit} className="task-form" aria-busy={busy}>
        <label htmlFor="task-title">Title</label>
        <input ref={titleRef} id="task-title" value={title} maxLength={200} required disabled={busy}
          onChange={(event) => { setTitle(event.target.value); setValidationError(""); }} />
        <label htmlFor="task-description">Description (optional)</label>
        <textarea id="task-description" value={description} maxLength={5000} rows={5} disabled={busy}
          onChange={(event) => { setDescription(event.target.value); setValidationError(""); }} />
        <div className="task-form-grid">
          <div><label htmlFor="task-priority">Priority</label>
            <select id="task-priority" value={priority} disabled={busy} onChange={(event) => setPriority(event.target.value)}>
              {taskPriorities.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </div>
          {task && <div><label htmlFor="task-status">Status</label>
            <select id="task-status" value={status} disabled={busy} onChange={(event) => setStatus(event.target.value)}>
              {taskStatuses.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </div>}
        </div>
        <label htmlFor="task-assignee">Assignee</label>
        <div className="task-assignee-controls">
          <select id="task-assignee" value={assigneeId} disabled={busy || membersLoading || Boolean(membersError)}
            onChange={(event) => { setAssigneeId(event.target.value); setValidationError(""); }}>
            <option value="">Unassigned</option>
            {unavailableAssignee && <option value={assigneeId} disabled>Unavailable member</option>}
            {members.map((member) => <option key={member.userId} value={member.userId}>{member.name} ({member.email})</option>)}
          </select>
          <button type="button" className="board-icon" title="Refresh members" aria-label="Refresh members" disabled={busy || membersLoading}
            onClick={() => { setMembersLoading(true); setMembersError(""); setAttempt((value) => value + 1); }}>
            <RefreshCw size={18} aria-hidden="true" />
          </button>
        </div>
        {membersLoading && <p role="status">Loading members...</p>}
        {(validationError || error || membersError) && <p className="board-error" role="alert">{validationError || error || membersError}</p>}
        <div className="task-dialog-actions">
          <button type="button" className="task-cancel" disabled={busy} onClick={onClose}>Cancel</button>
          <Button type="submit" className="board-command task-save" disabled={busy || membersLoading || Boolean(membersError)}>
            <Save size={18} aria-hidden="true" />{busy ? "Saving..." : task ? "Save changes" : "Create task"}
          </Button>
        </div>
      </form>
    </dialog>
  );
}