import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import AuthLayout from "../../layouts/AuthLayout";
import PasswordField from "../../components/PasswordField/PasswordField";
import Button from "../../components/Button/Button";
import { useAuth } from "../../context/AuthContext";
import { login as loginRequest } from "../../services/authService";
import { authDestination } from "../../routes/authDestination";

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isAuthenticated } = useAuth();
  const [email, setEmail] = useState(location.state?.signupComplete ? location.state.email : "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (submitting) return;

    if (!email.trim() || !password.trim()) {
      setError("Please enter your email and password.");
      return;
    }

    setError("");
    setSubmitting(true);
    try {
      const session = await loginRequest({ email: email.trim(), password });
      login(session);
      const destination = authDestination(location.state?.from);
      navigate(destination, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  if (isAuthenticated) return <Navigate to={authDestination(location.state?.from)} replace />;
  return (
    <AuthLayout>
        <div className="auth-card__header">
          <p className="auth-tag">Welcome back</p>
          <h1 className="auth-title">Sign in to DevPulse</h1>
          <p className="auth-subtitle">
            Use your work email and password to continue.
          </p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          {location.state?.signupComplete && (
            <p className="auth-subtitle" role="status">Account created successfully.</p>
          )}
          <label className="auth-field">
            <span>Email address</span>
            <input
              type="email"
              className="auth-input"
              placeholder="name@company.com"
              autoComplete="email"
              required
              maxLength={254}
              disabled={submitting}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>

            <PasswordField
              id="login-password"
              placeholder="Enter your password"
              autoComplete="current-password"
              required
              maxLength={72}
              disabled={submitting}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />

          {error && <p className="auth-error" role="alert">{error}</p>}

          <Button type="submit" variant="primary" block disabled={submitting}>
            {submitting ? "Signing in\u2026" : "Sign in"}<ArrowRight size={17} aria-hidden="true" />
          </Button>

          <p className="auth-footer">
            Don&apos;t have an account? <Link to="/signup" state={{ from: authDestination(location.state?.from) }}>Create one</Link>
          </p>
        </form>
    </AuthLayout>
  );
}
