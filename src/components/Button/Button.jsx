import { Link } from "react-router-dom";
import "./Button.css";

// Renders a styled <button>, or a react-router <Link> when `to` is provided.
export default function Button({
  variant = "primary",
  to,
  type = "button",
  block = false,
  className = "",
  children,
  ...props
}) {
  const classes = [
    "btn",
    `btn--${variant}`,
    block ? "btn--block" : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  if (to) {
    return (
      <Link to={to} className={classes} {...props}>
        {children}
      </Link>
    );
  }

  return (
    <button type={type} className={classes} {...props}>
      {children}
    </button>
  );
}
