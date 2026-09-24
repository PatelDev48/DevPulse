import { Link } from "react-router-dom";
import { ArrowLeft, ArrowUpRight } from "lucide-react";
import Brand from "../components/Brand/Brand";
import "../styles/public.css";

export default function AuthLayout({ children, signup = false }) {
  return <main className="auth-page">
    <header className="public-header"><Brand /><Link className="public-back" to="/"><ArrowLeft size={15} />Back to home</Link></header>
    <div className="auth-body"><section className="auth-card" aria-label={signup ? "Signup form" : "Login form"}>{children}</section>
      <aside className="auth-editorial" aria-label="DevPulse workspace preview"><p className="section-kicker">A little clarity. A lot of progress.</p>
        <h2>Your team.<br />In a good place<span>.</span></h2>
        <figure><img src="/workspace-preview.png" width="1280" height="900" alt="DevPulse dashboard showing an example workspace" /><figcaption>DevPulse / Example workspace</figcaption></figure>
        <Link to="/">Meet your next workspace<ArrowUpRight size={17} /></Link>
      </aside>
    </div>
    <footer className="auth-page-footer">DevPulse / Built around your team.</footer>
  </main>;
}