import { Link } from "react-router-dom";
import Button from "../Button/Button";
import "./Navbar.css";

export default function Navbar() {
  return (
    <nav className="dashboard-nav" aria-label="Primary navigation">
      <Link className="brand" to="/home">
        <span className="brand-mark">DP</span>
        <span>
          <strong>DevPulse</strong>
          <small>Developer productivity tracker</small>
        </span>
      </Link>

      <div className="nav-actions">
        <Link className="nav-link" to="/board">
          Board
        </Link>
        <Link className="nav-link" to="/dashboard">
          Dashboard
        </Link>
        <Link className="nav-link" to="/team">
          Team
        </Link>
        <Link className="nav-link" to="/login">
          Log in
        </Link>
        <Button to="/signup" variant="nav">
          Create account
        </Button>
      </div>
    </nav>
  );
}
