import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Button from "../../components/Button/Button";
import "../../styles/auth.css";

const highlights = [
  "Create a team-ready account in under a minute",
  "Prepare for tasks, roles, analytics, and live updates",
  "Keep early MVP onboarding simple and professional",
];

export default function Signup() {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();

    if (!name.trim() || !email.trim() || !password.trim() || !confirmPassword.trim()) {
      setError("Please complete all fields.");
      return;
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setError("");
    console.log("Signing up:", { name, email, password });
    navigate("/home");
  };

  return (
    <main className="auth-shell">
      <section className="auth-panel auth-panel--intro" aria-label="Product overview">
        <p className="auth-eyebrow">Start DevPulse</p>
        <h1 className="auth-headline">Create your workspace for real-time team visibility</h1>
        <p className="auth-copy">
          Set up access for a lightweight productivity tracker built around
          Kanban flow, live activity, and clear delivery metrics.
        </p>

        <ul className="auth-points" aria-label="Benefits">
          {highlights.map((point) => (
            <li key={point}>{point}</li>
          ))}
        </ul>
      </section>

      <section className="auth-card" aria-label="Signup form">
        <div className="auth-card__header">
          <p className="auth-tag">Get started</p>
          <h2 className="auth-title">Create your account</h2>
          <p className="auth-subtitle">
            Enter your details below to set up your workspace.
          </p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-field">
            <span>Full name</span>
            <input
              type="text"
              className="auth-input"
              placeholder="John Smith"
              autoComplete="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>

          <label className="auth-field">
            <span>Email address</span>
            <input
              type="email"
              className="auth-input"
              placeholder="name@company.com"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>

          <label className="auth-field">
            <span>Password</span>
            <input
              type="password"
              className="auth-input"
              placeholder="Create a password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>

          <label className="auth-field">
            <span>Confirm password</span>
            <input
              type="password"
              className="auth-input"
              placeholder="Repeat your password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </label>

          {error && <p className="auth-error">{error}</p>}

          <Button type="submit" variant="primary" block>
            Create account
          </Button>

          <p className="auth-footer">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </form>
      </section>
    </main>
  );
}
