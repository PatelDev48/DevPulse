import "./Team.css";

const members = [
  { name: "Dev Patel", role: "Admin", load: 7 },
  { name: "Priya Shah", role: "Member", load: 5 },
  { name: "Arjun Mehta", role: "Member", load: 4 },
  { name: "Meera Nair", role: "Member", load: 3 },
];

export default function Team() {
  return (
    <section className="team-page">
      <div className="section-heading">
        <div>
          <p className="section-kicker">People</p>
          <h1>Team Alpha</h1>
        </div>
        <span>Placeholder — member management coming soon</span>
      </div>

      <div className="team-list">
        {members.map((member) => (
          <div className="team-row" key={member.name}>
            <span>
              <strong>{member.name}</strong>
              <small>{member.role}</small>
            </span>
            <b>{member.load}</b>
          </div>
        ))}
      </div>
    </section>
  );
}
