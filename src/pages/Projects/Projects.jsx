import { useEffect, useEffectEvent, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, ArrowUpRight, FolderKanban, Plus, Settings2, Search } from "lucide-react";
import Button from "../../components/Button/Button";
import { useAuth } from "../../context/AuthContext";
import { listTeams } from "../../services/teamService";
import { createProject, listProjects, updateProject, setProjectArchived } from "../../services/projectService";
import ProjectSettings from "./ProjectSettings";
import "./Projects.css";

export default function Projects() {
  const { teamId } = useParams();
  return <TeamProjects key={teamId} teamId={teamId} />;
}

function TeamProjects({ teamId }) {
  const { token, logout } = useAuth();
  const [team, setTeam] = useState(null);
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [name, setName] = useState("");
  const [search, setSearch] = useState("");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [createError, setCreateError] = useState("");
  const [notice, setNotice] = useState("");
  const [view, setView] = useState("active");
  const [editing, setEditing] = useState(null);
  const [editError, setEditError] = useState("");
  const filterRef = useRef(null);
  const pending = useRef(false);
  const mounted = useRef(false);
  const expireSession = useEffectEvent(() => logout());

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([listTeams(token), listProjects(teamId, token)])
      .then(([teams, result]) => {
        if (!active) return;
        const currentTeam = teams.find((item) => item.id.toLowerCase() === teamId.toLowerCase());
        if (!currentTeam) {
          setLoadError("This team is unavailable or you no longer have access.");
          return;
        }
        setTeam(currentTeam);
        setProjects(result);
      })
      .catch((error) => {
        if (!active) return;
        if (error.status === 401) expireSession();
        else setLoadError(error.status === 403 ? "This team is unavailable or you no longer have access."
          : error.message || "Unable to load projects.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [teamId, token, attempt]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (pending.current || loading || loadError || team?.role !== "OWNER") return;
    const normalizedName = name.trim();
    const normalizedDescription = description.trim();
    setNotice("");
    if (!normalizedName || normalizedName.length > 100 || normalizedDescription.length > 2000) {
      setCreateError("Enter a name of 1 to 100 characters and a description of at most 2000 characters.");
      return;
    }
    pending.current = true;
    setSubmitting(true);
    setCreateError("");
    try {
      const project = await createProject(teamId, normalizedName, normalizedDescription, token);
      if (!mounted.current) return;
      setProjects((current) => [project, ...current.filter((item) => item.id !== project.id)]);
      setName("");
      setDescription("");
      setView("active");
      setSearch("");
      setNotice(`Project "${project.name}" created.`);
    } catch (error) {
      if (!mounted.current) return;
      if (error.status === 401) logout();
      else if (error.status === 403) {
        setTeam(null);
        setProjects([]);
        setLoadError("Your team permissions changed. Reload the project list.");
      } else setCreateError(error.message || "Unable to create project.");
    } finally {
      pending.current = false;
      if (mounted.current) setSubmitting(false);
    }
  };

  const manageProject = async (operation, message) => {
    if (pending.current) return;
    pending.current = true; setSubmitting(true); setEditError("");
    try {
      const result = await operation();
      if (!mounted.current) return;
      setProjects((current) => current.map((project) => project.id === result.id ? result : project));
      setEditing(null); setNotice(message);
    } catch (error) {
      if (!mounted.current) return;
      if (error.status === 401) logout();
      else if ([403, 404, 409].includes(error.status)) {
        setEditing(null); setTeam(null); setProjects([]);
        setLoadError(error.message || "Project access changed. Reload the list.");
      } else setEditError(error.message || "Unable to update project.");
    } finally { pending.current = false; if (mounted.current) setSubmitting(false); }
  };
  const visibleProjects = projects.filter((project) => Boolean(project.archivedAt) === (view === "archived") &&
    `${project.name} ${project.description || ""}`.toLowerCase().includes(search.trim().toLowerCase()));

  return (
    <section className="projects-page">
      <Link className="projects-back" to="/team"><ArrowLeft size={18} aria-hidden="true" />My Teams</Link>
      <header className="projects-heading">
        {team && !loadError && <p className="section-kicker">{team.name}</p>}
        <h1>Projects</h1>
        <p className="page-description">A shared direction. One task at a time.</p>
      </header>
      <div aria-busy={loading}>
        {loading ? <p role="status">Loading projects...</p> : loadError ? (
          <div className="projects-feedback">
            <p className="projects-error" role="alert">{loadError}</p>
            <Button onClick={() => { setLoadError(""); setLoading(true); setAttempt((value) => value + 1); }}>Try again</Button>
          </div>
        ) : (
          <>
            {team?.role === "OWNER" && (
              <details className="workspace-create"><summary><Plus size={17} />Create a project</summary>
              <form className="project-create" aria-label="Create project" aria-busy={submitting} onSubmit={handleSubmit}>
                <div className="project-field">
                  <label htmlFor="project-name">Project name</label>
                  <input id="project-name" value={name} required maxLength={100} autoComplete="off"
                    disabled={submitting} aria-describedby={createError ? "project-create-error" : undefined}
                    onChange={(event) => { setName(event.target.value); setCreateError(""); setNotice(""); }} />
                </div>
                <div className="project-field">
                  <label htmlFor="project-description">Description <span>(optional)</span></label>
                  <textarea id="project-description" value={description} maxLength={2000} rows={3}
                    disabled={submitting} aria-describedby={createError ? "project-create-error" : undefined}
                    onChange={(event) => { setDescription(event.target.value); setCreateError(""); setNotice(""); }} />
                </div>
                <Button type="submit" className="project-submit" disabled={submitting}>
                  <Plus size={18} aria-hidden="true" />{submitting ? "Creating..." : "Create project"}
                </Button>
                {createError && <p id="project-create-error" className="projects-error" role="alert">{createError}</p>}
              </form>
              </details>
            )}
            <p className="projects-notice" role="status">{notice}</p>
            <div className="project-results">
              <div className="project-results-heading"><h2>{visibleProjects.length} {view} {visibleProjects.length === 1 ? "project" : "projects"}</h2>
                <div className="workspace-search"><Search size={17} aria-hidden="true" /><input type="search" aria-label="Search projects" placeholder="Search projects..." value={search} onChange={(event) => setSearch(event.target.value)} /></div>
                <label>View <select ref={filterRef} aria-label="Project state" value={view} disabled={submitting} onChange={(event) => setView(event.target.value)}>
                  <option value="active">Active</option><option value="archived">Archived</option></select></label></div>
              {visibleProjects.length === 0 ? <p className="projects-empty">{search.trim() ? "No projects match your search." : `No ${view} projects.`}</p> : (
                <ul className="project-list" aria-label="Team projects">
                  {visibleProjects.map((project) => (
                    <li className="project-row" key={project.id}>
                      <FolderKanban size={22} aria-hidden="true" />
                      <div className="project-details">
                        <h3><Link to={`/teams/${teamId}/projects/${project.id}/board`}>{project.name}<ArrowUpRight size={15} aria-hidden="true" /></Link></h3>
                        {project.description && <p>{project.description}</p>}
                        <span>Created <time dateTime={project.createdAt}>{new Date(project.createdAt).toLocaleDateString(undefined,
                          { year: "numeric", month: "short", day: "numeric" })}</time></span>
                      </div>
                      {team?.role === "OWNER" && <button className="project-icon project-settings-button" title={`Settings for ${project.name}`} aria-label={`Settings for ${project.name}`} disabled={submitting}
                        onClick={() => { setEditError(""); setEditing(project); }}><Settings2 size={20} /></button>}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </>
        )}
      </div>
      {editing && <ProjectSettings project={editing} busy={submitting} error={editError} fallbackFocus={filterRef}
        onClose={() => { if (!pending.current) setEditing(null); }}
        onSave={(fields) => manageProject(() => updateProject(teamId, editing.id, fields, token), "Project updated.")}
        onArchive={(archived) => manageProject(() => setProjectArchived(teamId, editing.id, archived, token), archived ? "Project archived." : "Project restored.")} />}
    </section>
  );
}