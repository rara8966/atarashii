const TOKEN_KEY = 'policy-report-token';
const USERNAME_KEY = 'policy-report-username';
const ROLE_KEY = 'policy-report-role';

export type Role = 'ADMIN' | 'REPORTER' | 'VIEWER';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string, username: string, role?: string) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USERNAME_KEY, username);
  if (role) {
    localStorage.setItem(ROLE_KEY, role);
  } else {
    // role 缺省时尝试从 token 自身的 payload 解析
    const parsed = parseTokenRole(token);
    if (parsed) localStorage.setItem(ROLE_KEY, parsed);
  }
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
  localStorage.removeItem(ROLE_KEY);
}

export function getUsername(): string {
  return localStorage.getItem(USERNAME_KEY) ?? '';
}

export function getRole(): Role | '' {
  const r = localStorage.getItem(ROLE_KEY) ?? parseTokenRole(getToken() ?? '');
  if (r === 'ADMIN' || r === 'REPORTER' || r === 'VIEWER') return r;
  return '';
}

export function isLoggedIn(): boolean {
  const token = getToken();
  if (!token) return false;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

export function canEdit(): boolean {
  const r = getRole();
  return r === 'ADMIN' || r === 'REPORTER';
}

export function isAdmin(): boolean {
  return getRole() === 'ADMIN';
}

function parseTokenRole(token: string): string | null {
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.role === 'string' ? payload.role : null;
  } catch {
    return null;
  }
}
