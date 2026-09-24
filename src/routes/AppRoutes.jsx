import { Routes, Route, Navigate } from "react-router-dom";
import DashboardLayout from "../layouts/DashboardLayout";
import Home from "../pages/Home/Home";
import Board from "../pages/Board/Board";
import Dashboard from "../pages/Dashboard/Dashboard";
import Team from "../pages/Team/Team";
import Projects from "../pages/Projects/Projects";
import JoinTeam from "../pages/Team/JoinTeam";
import Login from "../pages/Login/Login";
import Signup from "../pages/Signup/Signup";
import ProtectedRoute from "./ProtectedRoute";

// Central place for all route + redirect definitions.
export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/home" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/signup" element={<Signup />} />
      <Route path="/invite" element={<JoinTeam />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<DashboardLayout />}>
        <Route path="/board" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/team" element={<Team />} />
        <Route path="/teams/:teamId/projects" element={<Projects />} />
        <Route path="/teams/:teamId/projects/:projectId/board" element={<Board />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
