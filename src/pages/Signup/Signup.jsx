import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import AuthLayout from "../../layouts/AuthLayout";
import PasswordField from "../../components/PasswordField/PasswordField";
import { useAuth } from "../../context/AuthContext";
import Button from "../../components/Button/Button";
import { signup as signupRequest } from "../../services/authService";
import { authDestination } from "../../routes/authDestination";

export default function Signup() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated } = useAuth();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (submitting) return;

    if (!name.trim() || !email.trim() || !password.trim() || !confirmPassword.trim()) {
      setError("Please complete all fields.");
      return;
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    if (password.length < 15 || password.length > 72 || new TextEncoder().encode(password).length > 72) {
      setError("Use at least 15 characters and no more than 72 UTF-8 bytes for your password.");
      return;
    }

    setError("");
    setSubmitting(true);
    try {
      const user = await signupRequest({ name: name.trim(), email: email.trim(), password });
      navigate("/login", { replace: true, state: {
        signupComplete: true, email: user.email, from: authDestination(location.state?.from),
      } });
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  if (isAuthenticated) return <Navigate to={authDestination(location.state?.from)} replace />;
  return (
    <AuthLayout signup>
        <div className="auth-card__header">
          <p className="auth-tag">Get started</p>
          <h1 className="auth-title">Create your account</h1>
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
              required
              maxLength={100}
              disabled={submitting}
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
              required
              maxLength={254}
              disabled={submitting}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>

            <PasswordField
              id="signup-password"
              aria-describedby="signup-password-hint"
              placeholder="Create a password"
              autoComplete="new-password"
              required
              minLength={15}
              maxLength={72}
              disabled={submitting}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          <p className="auth-password-hint" id="signup-password-hint">15 or more characters. Up to 72 UTF-8 bytes.</p>
            <PasswordField
              id="signup-confirm-password"
              label="Confirm password"
              placeholder="Repeat your password"
              autoComplete="new-password"
              required
              disabled={submitting}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />

          {error && <p className="auth-error" role="alert">{error}</p>}

          <Button type="submit" variant="primary" block disabled={submitting}>
            {submitting ? "Creating account\u2026" : "Create account"}<ArrowRight size={17} aria-hidden="true" />
          </Button>

          <p className="auth-footer">
            Already have an account? <Link to="/login" state={{ from: authDestination(location.state?.from) }}>Sign in</Link>
          </p>
        </form>
    </AuthLayout>
  );
}
