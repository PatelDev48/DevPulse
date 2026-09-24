import { Link } from "react-router-dom";

export default function Brand({ to = "/", compact = false }) {
  return <Link className={`brand${compact ? " brand--compact" : ""}`} to={to} aria-label="DevPulse home">
    <img src="/devpulse.svg" width="36" height="36" alt="" />
    <span>DevPulse<span className="brand-period">.</span></span>
  </Link>;
}