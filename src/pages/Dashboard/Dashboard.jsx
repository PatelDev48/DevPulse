import { useEffect, useEffectEvent, useState } from "react";
import { Link } from "react-router-dom";
import { Activity, ArrowUpRight, CheckCheck, CircleAlert, Clock3, FolderKanban, ListTodo, RefreshCw, ScanEye, Users } from "lucide-react";
import { useAuth } from "../../context/AuthContext";
import { listTeams } from "../../services/teamService";
import { getDashboard } from "../../services/dashboardService";
import { taskStatuses } from "../../services/taskService";
import "./Dashboard.css";

const percentage = (part, total) => total ? Math.round(part / total * 100) : 0;
const boardPath = (teamId, projectId) => `/teams/${teamId}/projects/${projectId}/board`;
const number = (value) => value.toLocaleString();

export default function Dashboard() {
  const { token, logout, user } = useAuth();
  const [teams, setTeams] = useState([]);
  const [selected, setSelected] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const expireSession = useEffectEvent(() => logout());
  useEffect(() => {
    let active = true;
    listTeams(token).then((result) => {
      if (active) { setTeams(result); setSelected(result[0]?.id || ""); }
    }).catch((failure) => {
      if (!active) return;
      if (failure.status === 401) expireSession();
      else setError(failure.message || "Unable to load teams.");
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [token, attempt]);

  return <section className="dashboard-page">
    <header className="dash-heading"><div><p className="section-kicker">Your workspace, in focus</p><h1>Dashboard</h1><p className="page-description">Welcome back{user?.name ? `, ${user.name.split(" ")[0]}` : ""}.</p></div>
      <Link className="dash-link" to="/team"><Users size={16} />My teams<ArrowUpRight size={16} /></Link></header>
    {loading ? <p className="dash-loading" role="status">Loading your workspace...</p> : error ? <div className="dash-feedback">
      <p role="alert">{error}</p><button onClick={() => { setError(""); setLoading(true); setAttempt((value) => value + 1); }}>Try again</button>
    </div> : teams.length === 0 ? <div className="dash-empty-workspace"><FolderKanban size={36} /><h2>No teams yet</h2><Link className="dash-link" to="/team">Create a team<ArrowUpRight size={16} /></Link></div> : <>
      <div className="dash-team-filter"><label htmlFor="dash-team">Team</label><select id="dash-team" value={selected} onChange={(event) => setSelected(event.target.value)}>
        {teams.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}
      </select><span>{teams.length} {teams.length === 1 ? "workspace" : "workspaces"}</span></div>
      <TeamDashboard key={selected} team={teams.find((team) => team.id === selected)} />
    </>}
  </section>;
}

function TeamDashboard({ team }) {
  const { token, logout } = useAuth();
  const [projectId, setProjectId] = useState("");
  const [projects, setProjects] = useState([]);
  const [scope, setScope] = useState("personal");
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [detailView, setDetailView] = useState("projects");
  const expireSession = useEffectEvent(() => logout());
  useEffect(() => {
    let active = true;
    getDashboard(team.id, projectId, token).then((result) => {
      if (active) { setData(result); setProjects(result.projects); }
    }).catch((failure) => {
      if (!active) return;
      if (failure.status === 401) expireSession();
      else {
        if (failure.status === 403) setProjects([]);
        setError([403, 404, 409].includes(failure.status)
          ? "This team or project is no longer available. Select another team or reload all active projects."
          : failure.message || "Unable to load dashboard.");
      }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [team.id, projectId, token, attempt]);

  const refresh = (resetProject = false) => {
    setData(null); setError(""); setLoading(true);
    if (resetProject) setProjectId("");
    setAttempt((value) => value + 1);
  };
  const overview = data?.[scope];
  const completion = overview ? percentage(overview.completed, overview.total) : 0;
  const selectedProjects = data?.projects.filter((project) => !projectId || project.id === projectId) || [];

  return <>
    <div className="dash-toolbar">
      <div className="dash-segment" role="group" aria-label="Metric scope">
        <button aria-pressed={scope === "personal"} onClick={() => setScope("personal")}><ListTodo size={16} />My work</button>
        <button aria-pressed={scope === "team"} onClick={() => setScope("team")}><Users size={16} />Team overview</button>
      </div>
      <div className="dash-project-filter"><label htmlFor="dash-project">Project</label>
        <select id="dash-project" value={projectId} onChange={(event) => { setProjectId(event.target.value); setData(null); setError(""); setLoading(true); }}>
          <option value="">All active projects</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
        </select>
        <button className="dash-icon" title="Refresh dashboard" aria-label="Refresh dashboard" disabled={loading} onClick={() => refresh()}><RefreshCw size={18} /></button>
      </div>
    </div>
    {loading ? <div className="dash-loading" role="status"><Activity size={22} />Loading dashboard...</div> : error ? <div className="dash-feedback">
      <p role="alert">{error}</p><button onClick={() => refresh(true)}>Reload dashboard</button>
    </div> : data && <div className="dash-content">
      <div className="dash-scope-line"><span>{scope === "personal" ? "Assigned to you" : "Everyone in " + team.name} / {projectId ? projects.find((project) => project.id === projectId)?.name : "All active projects"}</span>
        <span>Updated <time dateTime={data.generatedAt}>{new Date(data.generatedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</time></span></div>
      <div className="dash-metrics">
        <Metric icon={<ListTodo size={18} />} label={scope === "personal" ? "My open tasks" : "Team open tasks"} value={overview.open} note={`${number(overview.highPriorityOpen)} high priority`} tone="teal" />
        <Metric icon={<Activity size={18} />} label="In progress" value={overview.byStatus.IN_PROGRESS} note={`${number(overview.byStatus.TODO)} still to do`} tone="blue" />
        <Metric icon={<ScanEye size={18} />} label="In review" value={overview.byStatus.IN_REVIEW} note="Awaiting review" tone="amber" />
        <Metric icon={<CheckCheck size={18} />} label="Completed" value={overview.completed} note={`${number(overview.total)} total tasks`} tone="green" />
      </div>
      <div className="dash-chart-band">
        <section className="dash-status-section" aria-labelledby="dash-status-heading">
          <div className="dash-section-heading"><h2 id="dash-status-heading">Work by phase</h2><span>{number(overview.total)} tasks</span></div>
          <div className="dash-phase-chart" role="img" aria-label={taskStatuses.map(([status, label]) => `${label}: ${overview.byStatus[status]}`).join(", ")}>
            {taskStatuses.map(([status, label]) => <div className="dash-phase" key={status}>
              <span className="dash-phase-label">{label}</span><div className="dash-bar-track"><span className={`dash-bar dash-color-${status}`} style={{ width: `${percentage(overview.byStatus[status], overview.total)}%` }} /></div>
              <strong>{number(overview.byStatus[status])}</strong><span className="dash-phase-percent">{percentage(overview.byStatus[status], overview.total)}%</span>
            </div>)}
          </div>
          {overview.total === 0 && <p className="dash-muted">No tasks in this scope.</p>}
        </section>
        <section className="dash-completion-section" aria-labelledby="dash-completion-heading">
          <div className="dash-section-heading"><h2 id="dash-completion-heading">Completion</h2><span>Current snapshot</span></div>
          <div className="dash-completion-body"><div className="dash-donut" style={{ "--completion": `${completion}%` }} role="img" aria-label={overview.total ? `${completion}% complete, ${overview.completed} of ${overview.total} tasks` : "No tasks to measure"}>
            <div><strong>{overview.total ? `${completion}%` : "--"}</strong><span>complete</span></div>
          </div><div className="dash-completion-legend"><p><span className="dash-dot dash-color-DONE" />{number(overview.completed)} completed</p><p><span className="dash-dot dash-color-TODO" />{number(overview.open)} remaining</p></div></div>
        </section>
      </div>
      <section className="dash-signals" aria-labelledby="dash-signals-heading">
        <div className="dash-section-heading"><h2 id="dash-signals-heading"><Activity size={18} />Work signals</h2><span>Rule-based insights</span></div>
        <div className="dash-signals-grid">
          <Signal icon={<CircleAlert size={19} />} value={overview.highPriorityOpen} title="High-priority open tasks" detail={overview.highPriorityOpen ? "Prioritize these in your next planning check." : "No high-priority tasks are open."} tone="red" />
          <Signal icon={<Clock3 size={19} />} value={overview.staleOpen} title="No updates in 7+ days" detail={overview.staleOpen ? "Check whether these tasks need attention." : "No open tasks have gone unchanged for 7 days."} tone="amber" />
          {scope === "team" ? <Signal icon={<Users size={19} />} value={overview.unassignedOpen} title="Unassigned open tasks" detail={overview.unassignedOpen ? "Choose an owner to make responsibility clear." : "Every open task has an assignee."} tone="blue" />
            : <Signal icon={<ScanEye size={19} />} value={overview.byStatus.IN_REVIEW} title="Your tasks in review" detail={overview.byStatus.IN_REVIEW ? "Follow up with your team on pending reviews." : "You have no tasks waiting in review."} tone="blue" />}
        </div>
      </section>
      <div className="dash-detail-band">
        <section className="dash-queue" aria-labelledby="dash-queue-heading">
          <div className="dash-section-heading"><h2 id="dash-queue-heading">My open task queue</h2><span>{data.myOpenTasks.length} of {number(data.personal.open)}</span></div>
          <p className="dash-subtitle">Assigned to you / Highest priority first</p>
          {data.myOpenTasks.length === 0 ? <div className="dash-empty"><CheckCheck size={28} /><p>No open tasks assigned to you.</p></div>
            : <ul className="dash-task-queue">{data.myOpenTasks.map((task) => <li key={task.id}>
              <span className={`dash-priority-mark dash-priority-${task.priority}`} title={`${task.priority.toLowerCase()} priority`} />
              <div><Link to={boardPath(team.id, task.projectId)}>{task.title}</Link><span>{task.projectName} / {task.priority.charAt(0) + task.priority.slice(1).toLowerCase()} priority</span></div>
              <span className={`dash-status-tag dash-tag-${task.status}`}>{taskStatuses.find(([status]) => status === task.status)?.[1]}</span>
            </li>)}</ul>}
        </section>
        <section className="dash-team-detail" aria-label="Team details">
          <div className="dash-detail-tabs" role="group" aria-label="Team detail view">
            <button aria-pressed={detailView === "projects"} onClick={() => setDetailView("projects")}>Project progress</button>
            <button aria-pressed={detailView === "workload"} onClick={() => setDetailView("workload")}>Team workload</button>
          </div>
          <p className="dash-subtitle">Team totals / {projectId ? "Selected project" : "All active projects"}</p>
          {detailView === "projects" ? <>
            {selectedProjects.length === 0 ? <div className="dash-empty"><FolderKanban size={28} /><p>No active projects.</p><Link className="dash-link" to={`/teams/${team.id}/projects`}>View projects<ArrowUpRight size={16} /></Link></div>
              : <ul className="dash-project-list">{selectedProjects.map((project) => <li key={project.id}><div><Link to={boardPath(team.id, project.id)}>{project.name}</Link><span>{project.completed}/{project.total} done</span></div>
                <progress max={project.total || 1} value={project.completed} aria-label={`${project.name}: ${project.completed} of ${project.total} completed`} /></li>)}</ul>}
          </> : <div className="dash-table-wrap"><table className="dash-workload"><caption className="dash-sr-only">Task workload for current team members</caption>
            <thead><tr><th scope="col">Member</th><th scope="col">Open</th><th scope="col">Active</th><th scope="col">Review</th></tr></thead>
            <tbody>{data.workload.map((member) => <tr key={member.userId}><th scope="row">{member.name}</th><td>{number(member.open)}</td><td>{number(member.inProgress)}</td><td>{number(member.inReview)}</td></tr>)}</tbody>
          </table></div>}
        </section>
      </div>
      <footer className="dash-footer"><span>Active projects only / {data.archivedProjects} archived</span><Link className="dash-link" to={`/teams/${team.id}/projects`}>Manage projects<ArrowUpRight size={16} /></Link></footer>
    </div>}
  </>;
}

function Metric({ icon, label, value, note, tone }) {
  return <article className={`dash-metric dash-tone-${tone}`} aria-label={label}><div><span>{label}</span>{icon}</div><strong>{number(value)}</strong><p>{note}</p></article>;
}

function Signal({ icon, value, title, detail, tone }) {
  return <article className={`dash-signal dash-tone-${tone}`}><div>{icon}<strong>{number(value)}</strong></div><h3>{title}</h3><p>{detail}</p></article>;
}
