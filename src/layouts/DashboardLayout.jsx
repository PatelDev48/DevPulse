import { Outlet } from "react-router-dom";
import Navbar from "../components/Navbar/Navbar";
import "./DashboardLayout.css";

// Shared shell for authenticated/app pages: renders the Navbar once, then the
// active route's content via <Outlet />.
export default function DashboardLayout() {
  return (
    <main className="dashboard-shell">
      <Navbar />
      <Outlet />
    </main>
  );
}
