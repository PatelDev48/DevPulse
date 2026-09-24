import { Navigate, Link } from "react-router-dom";
import { ArrowUpRight, ArrowRight, Layers3, ChartNoAxesCombined, UsersRound } from "lucide-react";
import Button from "../../components/Button/Button";
import Brand from "../../components/Brand/Brand";
import { useAuth } from "../../context/AuthContext";
import "../../styles/public.css";

export default function Home() {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;
  return <div className="public-page">
    <header className="public-header"><Brand /><nav aria-label="Public navigation"><Link to="/login">Sign in</Link><Button to="/signup">Get started<ArrowUpRight size={16} /></Button></nav></header>
    <main>
      <section className="landing-hero">
        <img className="landing-product-background" src="/workspace-preview.png" alt="DevPulse dashboard with example project and task data" />
        <div className="landing-hero-copy"><p className="section-kicker">A shared space for moving work forward</p><h1>DevPulse<span>.</span></h1>
          <p className="landing-statement">Less chasing updates.<br />More meaningful progress.</p>
          <div className="landing-actions"><Button to="/signup">Create your account<ArrowRight size={17} /></Button><Link to="/login">Already a member? Sign in</Link></div>
        </div>
        <span className="landing-caption">Example workspace</span>
      </section>
      <section className="landing-principles" aria-label="Your workspace"><div><span>01 / People</span><UsersRound size={24} /><h2>Bring your team together.</h2><p>Shared projects, clear ownership, and a place for everyone's work.</p></div>
        <div><span>02 / Projects</span><Layers3 size={24} /><h2>Give work a little structure.</h2><p>From the first task to the final review, keep the whole project in view.</p></div>
        <div><span>03 / Perspective</span><ChartNoAxesCombined size={24} /><h2>See what needs your attention.</h2><p>Your priorities and your team's progress, in one considered workspace.</p></div>
      </section>
      <section className="landing-close"><div><p className="section-kicker">Your next chapter of work</p><h2>Make room for focus.</h2></div><Button to="/signup">Start your workspace<ArrowUpRight size={17} /></Button></section>
    </main>
    <footer className="public-footer"><Brand compact /><span>Thoughtfully built for small engineering teams.</span><Link to="/login">Sign in<ArrowUpRight size={14} /></Link></footer>
  </div>;
}
