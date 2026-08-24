import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Button from "../../components/Button/Button";
import "../../styles/auth.css";

const highlights = [
  "Jump back into your team board and live feed",
  "Review task movement, blockers, and delivery signals",
  "Designed for small engineering teams moving fast",
];

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [error, setError] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();

    if (!email.trim() || !password.trim()) {
      setError("Please enter your email and password.");
      return;
    }

    setError("");
    console.log("Logging in with:", { email, password, remember });
    navigate("/home");
  };

  return (
    <main className="auth-shell">
      <section className="auth-panel auth-panel--intro" aria-label="Product overview">
        <p className="auth-eyebrow">DevPulse access</p>
        <h1 className="auth-headline">Return to your engineering command center</h1>
        <p className="auth-copy">
          Continue tracking team activity, task progress, and productivity
          signals from one focused workspace.
        </p>

        <ul className="auth-points" aria-label="Benefits">
          {highlights.map((point) => (
            <li key={point}>{point}</li>
          ))}
        </ul>
      </section>

      <section className="auth-card" aria-label="Login form">
        <div className="auth-card__header">
          <p className="auth-tag">Welcome back</p>
          <h2 className="auth-title">Sign in to DevPulse</h2>
          <p className="auth-subtitle">
            Use your work email and password to continue.
          </p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
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
              placeholder="Enter your password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>

          <div className="auth-row">
            <label className="auth-check">
              <input
                type="checkbox"
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
              />
              <span>Remember me</span>
            </label>

            <button type="button" className="auth-link">
              Forgot password?
            </button>
          </div>

          {error && <p className="auth-error">{error}</p>}

          <Button type="submit" variant="primary" block>
            Sign in
          </Button>

          <p className="auth-footer">
            Don&apos;t have an account? <Link to="/signup">Create one</Link>
          </p>
        </form>
      </section>
    </main>
  );
}
