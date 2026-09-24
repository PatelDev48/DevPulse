import { useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { UserPlus } from "lucide-react";
import Button from "../../components/Button/Button";
import Brand from "../../components/Brand/Brand";
import { useAuth } from "../../context/AuthContext";
import { acceptInvitation } from "../../services/invitationService";
import "./Team.css";

export default function JoinTeam() {
  const location = useLocation();
  return <InvitationAcceptance key={location.hash} invitationToken={location.hash.slice(1)} />;
}

function InvitationAcceptance({ invitationToken }) {
  const { token, user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [unavailable, setUnavailable] = useState(false);
  const [alreadyMember, setAlreadyMember] = useState(false);
  const pendingRef = useRef(false);
  const valid = /^[A-Za-z0-9_-]{43}$/.test(invitationToken);
  const from = `/invite#${invitationToken}`;

  const join = async () => {
    if (pendingRef.current || !valid || !isAuthenticated || unavailable) return;
    pendingRef.current = true;
    setPending(true);
    setError("");
    try {
      await acceptInvitation(invitationToken, token);
      navigate("/team", { replace: true, state: { joinedTeam: true } });
    } catch (failure) {
      if (failure.status === 401) {
        logout();
        setError("Your session expired. Sign in to continue.");
      } else {
        setError(failure.message || "Unable to join team.");
        setUnavailable(failure.status === 400 || failure.status === 409);
        setAlreadyMember(failure.status === 409);
      }
    } finally {
      pendingRef.current = false;
      setPending(false);
    }
  };

  return (
    <main className="join-page">
      <Brand to={isAuthenticated ? "/dashboard" : "/"} />
      <section className="join-content" aria-labelledby="join-heading">
        <p className="section-kicker">Team invitation</p>
        <h1 id="join-heading">Join team</h1>
        {!valid ? <p role="alert" className="team-error">This invitation link is incomplete or invalid.</p> : (
          <>
            <p>You have been invited to join a DevPulse team as a member.</p>
            {error && <p className="team-error" role="alert">{error}</p>}
            {isAuthenticated ? (
              <>
                <p className="join-account">Signed in as <strong>{user.email}</strong></p>
                {alreadyMember ? <Button to="/team">Open my teams</Button> : !unavailable && (
                  <Button onClick={join} disabled={pending} className="invitation-command">
                    <UserPlus size={18} aria-hidden="true" />{pending ? "Joining..." : "Join team"}
                  </Button>
                )}
                {!pending && <Button className="join-switch" onClick={() => {
                  logout(); setUnavailable(false); setAlreadyMember(false); setError("");
                }}>Use another account</Button>}
              </>
            ) : (
              <div className="join-actions">
                <Button to="/login" state={{ from }}>Sign in</Button>
                <Link to="/signup" state={{ from }}>Create account</Link>
              </div>
            )}
          </>
        )}
      </section>
    </main>
  );
}