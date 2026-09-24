import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";

export default function PasswordField({ label = "Password", ...props }) {
  const [visible, setVisible] = useState(false);
  return <div className="auth-field"><label htmlFor={props.id}>{label}</label><div className="password-input">
    <input {...props} className="auth-input" type={visible ? "text" : "password"} />
    <button type="button" className="password-toggle" disabled={props.disabled} aria-label={`${visible ? "Hide" : "Show"} ${label.toLowerCase()}`} title={`${visible ? "Hide" : "Show"} ${label.toLowerCase()}`} aria-pressed={visible} onClick={() => setVisible((value) => !value)}>
      {visible ? <EyeOff size={18} /> : <Eye size={18} />}
    </button></div></div>;
}