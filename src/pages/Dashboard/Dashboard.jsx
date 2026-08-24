import "./Dashboard.css";

const metrics = [
  { label: "Tasks completed (7d)", value: "42", change: "+12% vs last week" },
  { label: "Avg. time to close", value: "2.4d", change: "-18% vs last week" },
  { label: "Open tasks", value: "24", change: "6 added this week" },
  { label: "Active members", value: "8", change: "Across Team Alpha" },
];

export default function Dashboard() {
  return (
    <section className="dashboard-page">
      <div className="section-heading">
        <div>
          <p className="section-kicker">Analytics</p>
          <h1>Productivity dashboard</h1>
        </div>
        <span>Placeholder — live metrics coming soon</span>
      </div>

      <div className="metrics-grid">
        {metrics.map((item) => (
          <article className="metric-card" key={item.label}>
            <span>{item.label}</span>
            <strong>{item.value}</strong>
            <p>{item.change}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
