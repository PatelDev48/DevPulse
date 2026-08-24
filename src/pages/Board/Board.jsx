import "./Board.css";

const columns = [
  { title: "To Do", tasks: ["Add invite link flow", "Create team settings view"] },
  { title: "In Progress", tasks: ["JWT refresh token handling", "Task status API validation"] },
  { title: "In Review", tasks: ["Dashboard summary query", "Activity feed UI states"] },
  { title: "Done", tasks: ["Signup form polish", "Professional auth layout"] },
];

export default function Board() {
  return (
    <section className="board-page">
      <div className="section-heading">
        <div>
          <p className="section-kicker">Task board</p>
          <h1>Team Alpha board</h1>
        </div>
        <span>Placeholder — live board coming soon</span>
      </div>

      <div className="board-columns">
        {columns.map((column) => (
          <article className="board-column" key={column.title}>
            <header>
              <strong>{column.title}</strong>
              <span>{column.tasks.length}</span>
            </header>
            {column.tasks.map((task) => (
              <p key={task}>{task}</p>
            ))}
          </article>
        ))}
      </div>
    </section>
  );
}
