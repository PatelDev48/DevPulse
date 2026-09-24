import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import { LayoutDashboard, UsersRound, LogOut, Layers3 } from "lucide-react";
import Brand from "../Brand/Brand";
import { useAuth } from "../../context/AuthContext";
import "./Navbar.css";

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const teamsActive = pathname === "/team" || pathname.startsWith("/teams/");
  const initials = (user?.name || "User").split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toUpperCase();

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <aside className="workspace-sidebar">
      <Brand to="/dashboard" />
      <div className="workspace-label"><span className="workspace-label-icon"><Layers3 size={18} /></span><div><strong>My workspace</strong><span>Personal & team space</span></div></div>
      <nav className="dashboard-nav" aria-label="Primary navigation">
        <p className="nav-section-label">Workspace</p>
        <NavLink className="nav-link" to="/dashboard"><LayoutDashboard size={19} /><span>Dashboard</span></NavLink>
        <Link className={`nav-link${teamsActive ? " active" : ""}`} aria-current={teamsActive ? "page" : undefined} to="/team"><UsersRound size={19} /><span>Teams & projects</span></Link>
      </nav>
      <div className="sidebar-bottom">
        <div className="sidebar-note"><span className="sidebar-note-line" /><span>A little clarity.<br /><strong>A lot of progress.</strong></span></div>
        <div className="workspace-account"><span className="account-avatar" aria-hidden="true">{initials}</span><div><strong>{user?.name || "Your account"}</strong><span title={user?.email}>{user?.email}</span></div>
          <button className="logout-button" title="Log out" aria-label="Log out" onClick={handleLogout}><LogOut size={18} /></button>
        </div>
      </div>
    </aside>
  );
}
