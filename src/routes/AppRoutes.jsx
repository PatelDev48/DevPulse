import { Routes, Route } from "react-router-dom";
import DashboardLayout from "../layouts/DashboardLayout";
import Home from "../pages/Home/Home";
import Board from "../pages/Board/Board";
import Dashboard from "../pages/Dashboard/Dashboard";
import Team from "../pages/Team/Team";
import Login from "../pages/Login/Login";
import Signup from "../pages/Signup/Signup";

// Central place for all route + redirect definitions.
export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/signup" element={<Signup />} />

      <Route element={<DashboardLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/home" element={<Home />} />
        <Route path="/board" element={<Board />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/team" element={<Team />} />
      </Route>
    </Routes>
  );
}
