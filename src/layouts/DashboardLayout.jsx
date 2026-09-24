import { Outlet, Link, useLocation } from "react-router-dom";
import { ChevronRight, CalendarDays } from "lucide-react";
import Navbar from "../components/Navbar/Navbar";
import "./DashboardLayout.css";

// Shared shell for authenticated/app pages: renders the Navbar once, then the
// active route's content via <Outlet />.
export default function DashboardLayout() {
  const { pathname } = useLocation();
  const current = pathname === "/dashboard" ? "Overview" : pathname.endsWith("/board") ? "Project board" : pathname.endsWith("/projects") ? "Projects" : "Teams";
  return (
    <div className="dashboard-shell">
      <a className="skip-link" href="#workspace-content">Skip to content</a>
      <Navbar />
      <div className="workspace-main">
        <header className="workspace-topbar"><div><Link to="/dashboard">Workspace</Link><ChevronRight size={14} /><span>{current}</span></div>
          <time dateTime={new Date().toISOString().slice(0, 10)}><CalendarDays size={15} />{new Date().toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })}</time>
        </header>
        <main id="workspace-content" className="workspace-content" tabIndex={-1}><Outlet /></main>
        <footer className="workspace-footer"><span>DevPulse</span><span>Built around your team.</span></footer>
      </div>
    </div>
  );
}
