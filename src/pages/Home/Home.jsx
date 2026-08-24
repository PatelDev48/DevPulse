import Button from "../../components/Button/Button";
import "./Home.css";

const stats = [
  { label: "Open tasks", value: "24", change: "+6 this week" },
  { label: "Done today", value: "8", change: "32% faster close rate" },
  { label: "Avg. time to close", value: "2.4d", change: "-18% vs last week" },
  { label: "Live updates", value: "14", change: "Last 60 minutes" },
];

const board = [
  {
    title: "To Do",
    count: 6,
    tasks: ["Add invite link flow", "Create team settings view"],
  },
  {
    title: "In Progress",
    count: 9,
    tasks: ["JWT refresh token handling", "Task status API validation"],
  },
  {
    title: "In Review",
    count: 4,
    tasks: ["Dashboard summary query", "Activity feed UI states"],
  },
  {
    title: "Done",
    count: 5,
    tasks: ["Signup form polish", "Professional auth layout"],
  },
];

const activity = [
  "Priya moved Dashboard summary query to In Review",
  "Arjun created Task status API validation",
  "Dev completed Professional auth layout",
  "Meera assigned Invite link flow to herself",
];

const members = [
  { name: "Dev Patel", load: 7, role: "Admin" },
  { name: "Priya Shah", load: 5, role: "Member" },
  { name: "Arjun Mehta", load: 4, role: "Member" },
];

export default function Home() {
  return (
    <>
      <section className="dashboard-hero">
        <div className="hero-content">
          <p className="section-kicker">Real-time engineering visibility</p>
          <h1>Track work, team activity, and delivery health in one place.</h1>
          <p>
            DevPulse combines a Kanban task board, live team activity, and
            productivity analytics for small engineering teams.
          </p>

          <div className="hero-actions">
            <Button to="/signup" variant="primary">
              Start tracking
            </Button>
            <Button to="/login" variant="secondary">
              View demo access
            </Button>
          </div>
        </div>

        <aside className="hero-summary" aria-label="Current team summary">
          <div>
            <span className="summary-label">Sprint health</span>
            <strong>On track</strong>
          </div>
          <div className="summary-meter" aria-hidden="true">
            <span></span>
          </div>
          <p>76% of active work is moving through review or completion.</p>
        </aside>
      </section>

      <section className="stats-grid" aria-label="Productivity metrics">
        {stats.map((item) => (
          <article className="metric-card" key={item.label}>
            <span>{item.label}</span>
            <strong>{item.value}</strong>
            <p>{item.change}</p>
          </article>
        ))}
      </section>

      <section className="dashboard-grid">
        <div className="work-card board-card">
          <div className="section-heading">
            <div>
              <p className="section-kicker">Task board</p>
              <h2>Current workflow</h2>
            </div>
            <span>Team Alpha</span>
          </div>

          <div className="kanban-preview">
            {board.map((column) => (
              <article className="kanban-column" key={column.title}>
                <header>
                  <strong>{column.title}</strong>
                  <span>{column.count}</span>
                </header>
                {column.tasks.map((task) => (
                  <p key={task}>{task}</p>
                ))}
              </article>
            ))}
          </div>
        </div>

        <aside className="work-card activity-card">
          <div className="section-heading">
            <div>
              <p className="section-kicker">Live feed</p>
              <h2>Team updates</h2>
            </div>
          </div>

          <ul className="activity-list">
            {activity.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </aside>

        <aside className="work-card workload-card">
          <div className="section-heading">
            <div>
              <p className="section-kicker">Workload</p>
              <h2>Member load</h2>
            </div>
          </div>

          <div className="member-list">
            {members.map((member) => (
              <div className="member-row" key={member.name}>
                <span>
                  <strong>{member.name}</strong>
                  <small>{member.role}</small>
                </span>
                <b>{member.load}</b>
              </div>
            ))}
          </div>
        </aside>
      </section>
    </>
  );
}
