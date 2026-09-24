import { useEffect, useEffectEvent, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { FolderKanban, Users, Search, Plus } from "lucide-react";
import Button from "../../components/Button/Button";
import { useAuth } from "../../context/AuthContext";
import { createTeam, listTeams } from "../../services/teamService";
import TeamInvitation from "./TeamInvitation";
import TeamMembers from "./TeamMembers";
import "./Team.css";

export default function Team() {
  const location = useLocation();
  const { token, logout } = useAuth();
  const [teams, setTeams] = useState([]);
  const [selectedTeam, setSelectedTeam] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [name, setName] = useState("");
  const [search, setSearch] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [createError, setCreateError] = useState("");
  const [notice, setNotice] = useState(location.state?.joinedTeam ? "You joined the team." : "");
  const submissionPending = useRef(false);
  const expireSession = useEffectEvent(() => logout());

  useEffect(() => {
    let active = true;
    listTeams(token)
      .then((result) => {
        if (active) setTeams(result);
      })
      .catch((error) => {
        if (!active) return;
        if (error.status === 401) expireSession();
        else setLoadError(error.message || "Unable to load teams.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [token, attempt]);

  const retry = () => {
    setLoadError("");
    setLoading(true);
    setAttempt((value) => value + 1);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submissionPending.current || loading || loadError) return;
    const normalizedName = name.trim();
    setNotice("");
    if (!normalizedName || normalizedName.length > 100) {
      setCreateError("Enter a team name of 1 to 100 characters.");
      return;
    }
    submissionPending.current = true;
    setSubmitting(true);
    setCreateError("");
    try {
      const team = await createTeam(normalizedName, token);
      setTeams((current) => [{ ...team, role: "OWNER" }, ...current.filter((item) => item.id !== team.id)]);
      setName("");
      setSearch("");
      setNotice("Team created.");
    } catch (error) {
      if (error.status === 401) logout();
      else setCreateError(error.message || "Unable to create team.");
    } finally {
      submissionPending.current = false;
      setSubmitting(false);
    }
  };

  const visibleTeams = teams.filter((team) => team.name.toLowerCase().includes(search.trim().toLowerCase()));

  return (
    <section className="team-page">
      <div className="section-heading">
        <div>
          <p className="section-kicker">Workspace / People</p>
          <h1>My Teams</h1>
          <p className="page-description">Good work starts with the right people.</p>
        </div>
      </div>

      <details className="workspace-create"><summary><Plus size={17} />Create a team</summary>
      <form className="team-create" onSubmit={handleSubmit} aria-label="Create team" aria-busy={submitting}>
        <label htmlFor="team-name">Team name</label>
        <div className="team-create-controls">
          <input id="team-name" name="teamName" value={name} required maxLength={100}
            autoComplete="off" placeholder="Platform engineering"
            disabled={submitting || loading || Boolean(loadError)}
            aria-invalid={Boolean(createError)} aria-describedby={createError ? "team-create-error" : undefined}
            onChange={(event) => { setName(event.target.value); setCreateError(""); setNotice(""); }} />
          <Button type="submit" disabled={submitting || loading || Boolean(loadError)}>
            <Plus size={16} />{submitting ? "Creating..." : "Create team"}
          </Button>
        </div>
        {createError && <p id="team-create-error" className="team-error" role="alert">{createError}</p>}
      </form>
      </details>
      <p className="team-notice" role="status">{notice}</p>
      <div className="team-list-toolbar"><h2>Your teams <span>{teams.length}</span></h2><div className="workspace-search"><Search size={17} aria-hidden="true" /><input type="search" aria-label="Search teams" placeholder="Search teams..." value={search} onChange={(event) => setSearch(event.target.value)} /></div></div>

      <div className="team-results" aria-busy={loading}>
        {loading ? <p role="status">Loading teams...</p> : loadError ? (
          <div className="team-load-error">
            <p className="team-error" role="alert">{loadError}</p>
            <Button onClick={retry}>Try again</Button>
          </div>
        ) : visibleTeams.length === 0 ? <p className="team-empty">{teams.length ? "No teams match your search." : "No teams yet."}</p> : (
          <ul className="team-list" aria-label="My teams">
            {visibleTeams.map((team) => (
              <li className="team-row" key={team.id}>
                <div className="team-row-heading">
                  <button type="button" className="team-members-trigger" aria-label={`View members of ${team.name}`}
                    aria-haspopup="dialog" onClick={() => setSelectedTeam(team)}>
                    <Users size={18} aria-hidden="true" /><strong>{team.name}</strong>
                  </button>
                  <span>{team.role === "OWNER" ? "Owner" : "Member"} · Created <time dateTime={team.createdAt}>{new Date(team.createdAt).toLocaleDateString(undefined,
                    { year: "numeric", month: "short", day: "numeric" })}</time></span>
                </div>
                <Button to={`/teams/${team.id}/projects`} variant="secondary" className="team-projects-link"
                  aria-label={`View projects for ${team.name}`}>
                  <FolderKanban size={18} aria-hidden="true" />Projects
                </Button>
                {team.role === "OWNER" && <TeamInvitation team={team} />}
              </li>
            ))}
          </ul>
        )}
      </div>
      {selectedTeam && <TeamMembers key={selectedTeam.id} team={selectedTeam} onClose={() => setSelectedTeam(null)} />}
    </section>
  );
}
