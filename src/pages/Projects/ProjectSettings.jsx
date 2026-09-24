import { useEffect, useRef, useState } from "react";
import { Archive, ArchiveRestore, Save, X } from "lucide-react";
import Button from "../../components/Button/Button";

export default function ProjectSettings({ project, busy, error, onClose, onSave, onArchive, fallbackFocus }) {
  const dialogRef = useRef(null);
  const closeRef = useRef(null);
  const cancelRef = useRef(null);
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description || "");
  const [confirming, setConfirming] = useState(false);
  const [validation, setValidation] = useState("");
  useEffect(() => {
    const dialog = dialogRef.current;
    const trigger = document.activeElement;
    const fallback = fallbackFocus.current;
    const overflow = document.body.style.overflow;
    dialog.showModal(); closeRef.current.focus(); document.body.style.overflow = "hidden";
    return () => {
      dialog.close(); document.body.style.overflow = overflow;
      if (trigger instanceof HTMLElement && trigger.isConnected && !trigger.disabled) trigger.focus();
      else fallback?.focus();
    };
  }, [fallbackFocus]);
  useEffect(() => {
    if (busy) return;
    if (confirming) cancelRef.current?.focus();
    else closeRef.current?.focus();
  }, [confirming, busy]);
  const dismiss = (event) => { event.preventDefault(); if (!busy) onClose(); };
  const submit = (event) => {
    event.preventDefault();
    if (busy || project.archivedAt) return;
    if (!name.trim() || name.trim().length > 100 || description.trim().length > 2000) {
      setValidation("Enter a name of 1 to 100 characters and a description of at most 2000 characters."); return;
    }
    setValidation(""); onSave({ name: name.trim(), description: description.trim() || null });
  };
  return <dialog ref={dialogRef} className="project-dialog" aria-labelledby="project-settings-title" onCancel={dismiss}
    onKeyDown={(event) => { if (event.key === "Escape") dismiss(event); }}>
    <header><h2 id="project-settings-title">{confirming ? "Archive project?" : "Project settings"}</h2>
      <button ref={closeRef} className="project-icon" title="Close project settings" aria-label="Close project settings" disabled={busy} onClick={onClose}><X size={20} /></button>
    </header>
    {confirming ? <div className="project-confirm">
      <p><strong>{project.name}</strong> and its tasks will become read-only. Existing work is preserved. You can restore this project later.</p>
      <div className="project-dialog-actions"><button ref={cancelRef} disabled={busy} onClick={() => setConfirming(false)}>Cancel</button>
        <Button disabled={busy} onClick={() => onArchive(true)}><Archive size={18} />{busy ? "Archiving..." : "Confirm archive"}</Button></div>
    </div> : <>
      <form onSubmit={submit} aria-busy={busy}>
        <div className="project-field"><label htmlFor="edit-project-name">Project name</label>
          <input id="edit-project-name" value={name} maxLength={100} required disabled={busy || Boolean(project.archivedAt)} onChange={(event) => setName(event.target.value)} /></div>
        <div className="project-field"><label htmlFor="edit-project-description">Description (optional)</label>
          <textarea id="edit-project-description" value={description} rows={4} maxLength={2000} disabled={busy || Boolean(project.archivedAt)} onChange={(event) => setDescription(event.target.value)} /></div>
        {!project.archivedAt && <Button type="submit" disabled={busy}><Save size={18} />{busy ? "Saving..." : "Save project"}</Button>}
      </form>
      <div className="project-lifecycle">
        {project.archivedAt ? <><p>Archived {new Date(project.archivedAt).toLocaleDateString()}</p><Button disabled={busy} onClick={() => onArchive(false)}><ArchiveRestore size={18} />{busy ? "Restoring..." : "Restore project"}</Button></>
          : <button className="project-archive" disabled={busy} onClick={() => setConfirming(true)}><Archive size={18} />Archive project</button>}
      </div>
    </>}
    {(validation || error) && <p role="alert" className="projects-error">{validation || error}</p>}
  </dialog>;
}