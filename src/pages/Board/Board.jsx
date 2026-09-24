import { useEffect, useEffectEvent, useRef, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { Plus, RefreshCw, UserRound, Search, X, CircleCheck } from "lucide-react";
import Button from "../../components/Button/Button";
import { useAuth } from "../../context/AuthContext";
import { listTeams, listMembers } from "../../services/teamService";
import { listProjects } from "../../services/projectService";
import { createTask, listTasks, updateTask, updateTaskStatus, taskStatuses, taskPriorities } from "../../services/taskService";
import TaskEditor from "./TaskEditor";
import "./Board.css";

export default function Board() {
  const { teamId, projectId } = useParams();
  if (!teamId || !projectId) return <Navigate to="/team" replace />;
  return <ProjectBoard key={`${teamId}/${projectId}`} teamId={teamId} projectId={projectId} />;
}

function ProjectBoard({ teamId, projectId }) {
  const { token, logout, user } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [editor, setEditor] = useState(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  const [search, setSearch] = useState("");
  const [assignee, setAssignee] = useState("");
  const [priority, setPriority] = useState("");
  const pending = useRef(false);
  const mounted = useRef(false);
  const newTaskRef = useRef(null);
  const statusControls = useRef(new Map());
  const statusFocus = useRef(null);
  const expireSession = useEffectEvent(() => logout());

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([listTeams(token), listProjects(teamId, token), listTasks(teamId, projectId, token), listMembers(teamId, token)])
      .then(([teams, projects, tasks, members]) => {
        if (!active) return;
        const team = teams.find((item) => item.id.toLowerCase() === teamId.toLowerCase());
        const project = projects.find((item) => item.id.toLowerCase() === projectId.toLowerCase());
        if (!team || !project) {
          setLoadError("This project is unavailable or you no longer have access.");
          return;
        }
        setData({ team, project, projects, tasks, members });
      })
      .catch((error) => {
        if (!active) return;
        if (error.status === 401) expireSession();
        else setLoadError([403, 404].includes(error.status)
          ? "This project is unavailable or you no longer have access." : error.message || "Unable to load board.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [teamId, projectId, token, attempt]);

  useEffect(() => {
    if (!busy && statusFocus.current) {
      statusControls.current.get(statusFocus.current)?.focus();
      statusFocus.current = null;
    }
  }, [busy]);

  const reload = () => {
    if (pending.current) return;
    setData(null); setLoadError(""); setActionError(""); setNotice(""); setLoading(true);
    setAttempt((value) => value + 1);
  };

  const accessFailure = (error) => {
    if (error.status === 401) { logout(); return true; }
    if ([403, 404, 409].includes(error.status)) {
      setEditor(null); setData(null);
      setLoadError(error.status === 409 ? "This project was archived. Reload the read-only board." : error.status === 403 ? "Your access changed. Reload the board."
        : "The task or project is no longer available. Reload the board.");
      return true;
    }
    return false;
  };

  const mutate = async (operation, success, closeEditor) => {
    if (pending.current) return;
    pending.current = true; setBusy(true); setActionError(""); setNotice("");
    try {
      const task = await operation();
      if (!mounted.current) return;
      setData((current) => ({ ...current, tasks: current.tasks.some((item) => item.id === task.id)
        ? current.tasks.map((item) => item.id === task.id ? task : item) : [task, ...current.tasks] }));
      if (closeEditor) setEditor(null);
      setNotice(success);
    } catch (error) {
      if (mounted.current && !accessFailure(error)) setActionError(error.message || "Unable to save task.");
    } finally {
      pending.current = false;
      if (mounted.current) setBusy(false);
    }
  };

  const openEditor = (task = null) => {
    if (pending.current) return;
    setActionError(""); setNotice(""); setEditor({ task });
  };

  const closeEditor = () => {
    if (pending.current) return;
    setEditor(null); setActionError("");
  };

  const query = search.trim().toLowerCase();
  const filteredTasks = data?.tasks.filter((task) =>
    (!query || `${task.title} ${task.description || ""}`.toLowerCase().includes(query)) &&
    (!priority || task.priority === priority) &&
    (!assignee || (assignee === "unassigned" ? !task.assigneeId : task.assigneeId === (assignee === "me" ? user.id : assignee)))) || [];
  const hasFilters = Boolean(query || assignee || priority);
  const completed = data?.tasks.filter((task) => task.status === "DONE").length || 0;

  return (
    <section className="board-page">
      <nav className="board-breadcrumbs" aria-label="Breadcrumb">
        <Link to="/team">My Teams</Link><span aria-hidden="true">/</span>
        <Link to={`/teams/${teamId}/projects`}>{data?.team.name || "Projects"}</Link>
        {data && <><span aria-hidden="true">/</span><span aria-current="page">{data.project.name}</span></>}
      </nav>
      {loading ? <p role="status">Loading board...</p> : loadError ? (
        <div><h1>Task board</h1><p className="board-error" role="alert">{loadError}</p><Button onClick={reload}>Try again</Button></div>
      ) : data && (
        <>
          <header className="board-heading">
            <div><p className="section-kicker">Project / Task board</p><h1>{data.project.name}</h1>{data.project.description && <p className="page-description">{data.project.description}</p>}</div>
            <div className="board-actions">
              <button className="board-icon" title="Refresh board" aria-label="Refresh board" disabled={busy} onClick={reload}>
                <RefreshCw size={18} aria-hidden="true" />
              </button>
              <Button ref={newTaskRef} className="board-command" disabled={busy || Boolean(data.project.archivedAt)} onClick={() => openEditor()}>
                <Plus size={18} aria-hidden="true" />New task
              </Button>
            </div>
          </header>
          <div className="board-toolbar">
            <label htmlFor="board-project">Project</label>
            <select id="board-project" value={data.project.id} disabled={busy}
              onChange={(event) => navigate(`/teams/${teamId}/projects/${event.target.value}/board`)}>
              {data.projects.filter((project) => !project.archivedAt || project.id === data.project.id).map((project) => <option key={project.id} value={project.id}>{project.name}{project.archivedAt ? " (archived)" : ""}</option>)}
            </select>
            <span>{data.tasks.length} {data.tasks.length === 1 ? "task" : "tasks"}</span>
            <span className="board-completion"><CircleCheck size={15} aria-hidden="true" />{completed} completed</span>
          </div>
          <div className="board-filters" role="search" aria-label="Filter tasks">
            <div className="workspace-search"><Search size={17} aria-hidden="true" /><input type="search" aria-label="Search tasks" placeholder="Search tasks..." value={search} onChange={(event) => setSearch(event.target.value)} /></div>
            <select aria-label="Filter by assignee" value={assignee} onChange={(event) => setAssignee(event.target.value)}>
              <option value="">All assignees</option><option value="me">Assigned to me</option><option value="unassigned">Unassigned</option>
              {data.members.filter((member) => member.userId !== user.id).map((member) => <option key={member.userId} value={member.userId}>{member.name}</option>)}
            </select>
            <select aria-label="Filter by priority" value={priority} onChange={(event) => setPriority(event.target.value)}><option value="">All priorities</option>{taskPriorities.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
            {hasFilters && <button className="board-icon" aria-label="Clear task filters" title="Clear task filters" onClick={() => { setSearch(""); setAssignee(""); setPriority(""); }}><X size={17} /></button>}
          </div>
          {hasFilters && <p className="board-filter-count" role="status">{filteredTasks.length} of {data.tasks.length} tasks</p>}
          {data.project.archivedAt && <p className="board-notice" role="status">Archived project. Tasks are read-only. {data.team.role === "OWNER" && <Link to={`/teams/${teamId}/projects`}>Manage projects</Link>}</p>}
          {!editor && actionError && <p className="board-error" role="alert">{actionError}</p>}
          <p className="board-notice" role="status">{busy ? "Saving task..." : notice}</p>
          <div className="board-columns" aria-busy={busy}>
            {taskStatuses.map(([value, label]) => {
              const tasks = filteredTasks.filter((task) => task.status === value);
              return (
                <section className={`board-column board-column--${value.toLowerCase()}`} key={value} aria-label={label}>
                  <header><h2>{label}</h2><span>{tasks.length}</span></header>
                  {tasks.length === 0 ? <p className="board-empty">{hasFilters ? "No matching tasks" : "No tasks yet"}</p> : (
                    <ul className="board-task-list">
                      {tasks.map((task) => (
                        <li className="task-card" key={task.id}>
                          {data.project.archivedAt ? <><h3 className="task-title">{task.title}</h3>{task.description && <p style={{ whiteSpace: "pre-wrap" }}>{task.description}</p>}</>
                            : <button className="task-title" disabled={busy} onClick={() => openEditor(task)} aria-label={`Edit task: ${task.title}`}>{task.title}</button>}
                          <span className={`task-priority task-priority--${task.priority.toLowerCase()}`}>
                            {taskPriorities.find(([priority]) => priority === task.priority)?.[1]}
                          </span>
                          <p className="task-assignee"><UserRound size={15} aria-hidden="true" />
                            {task.assigneeId ? data.members.find((member) => member.userId === task.assigneeId)?.name || "Former member" : "Unassigned"}
                          </p>
                          <label className="task-status-label" htmlFor={`status-${task.id}`}>Status</label>
                          <select id={`status-${task.id}`} aria-label={`Status for ${task.title}`} value={task.status} disabled={busy || Boolean(data.project.archivedAt)}
                            ref={(element) => { if (element) statusControls.current.set(task.id, element); else statusControls.current.delete(task.id); }}
                            onChange={(event) => {
                              statusFocus.current = task.id;
                              const nextStatus = event.target.value;
                              void mutate(() => updateTaskStatus(teamId, projectId, task.id, nextStatus, token), "Task status updated.", false);
                            }}>
                            {taskStatuses.map(([status, text]) => <option key={status} value={status}>{text}</option>)}
                          </select>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              );
            })}
          </div>
          {editor && <TaskEditor task={editor.task} teamId={teamId} busy={busy} error={actionError}
            fallbackFocus={newTaskRef} onClose={closeEditor} onAccessFailure={accessFailure}
            onMembers={(members) => setData((current) => current ? { ...current, members } : current)}
            onSave={(fields) => mutate(() => editor.task
              ? updateTask(teamId, projectId, editor.task.id, fields, token)
              : createTask(teamId, projectId, fields, token), editor.task ? "Task updated." : "Task created.", true)} />}
        </>
      )}
    </section>
  );
}
