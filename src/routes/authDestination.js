export function authDestination(from) {
  if (typeof from !== "string") return "/dashboard";
  if (["/dashboard", "/team"].includes(from)) return from;
  if (/^\/teams\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/projects$/i.test(from)) return from;
  if (/^\/teams\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/projects\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/board$/i.test(from)) return from;
  return /^\/invite#[A-Za-z0-9_-]{43}$/.test(from) ? from : "/dashboard";
}