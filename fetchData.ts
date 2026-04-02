/**
 * Interview Problem #6 — TypeScript / API Utility
 * Difficulty: Easy | Issues to find: 1
 *
 * A shared fetch utility used across the entire frontend.
 * Every API call in the codebase goes through this function.
 * It has been in production for months with no visible errors.
 * Find 1 thing that is wrong.
 */
async function fetchData<T>(
  url: string,
  options?: RequestInit
): Promise<T> {
  const response = await fetch(url, options);

  return response.json() as Promise<T>;
}

export default fetchData;

// ─── Example usage across the app ────────────────────────────────────────────

interface User {
  id: number;
  name: string;
  email: string;
}

interface Order {
  id: number;
  total: number;
  status: string;
}

interface Report {
  quarter: string;
  revenue: number;
}

async function examples() {
  const user   = await fetchData<User>("/api/users/1");
  const orders = await fetchData<Order[]>("/api/orders");
  const report = await fetchData<Report>("/api/reports/q3");
}
