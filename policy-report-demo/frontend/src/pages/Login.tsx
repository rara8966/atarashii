import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { apiLogin, apiRegister, apiBootstrap } from '../api';
import { setToken, isLoggedIn, isAdmin } from '../auth';

export default function Login() {
  const navigate = useNavigate();

  if (isLoggedIn()) {
    navigate('/', { replace: true });
    return null;
  }

  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'ADMIN' | 'REPORTER' | 'VIEWER'>('REPORTER');
  const [hasAdmin, setHasAdmin] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    void apiBootstrap().then(b => setHasAdmin(b.hasAdmin)).catch(() => setHasAdmin(true));
  }, []);

  // bootstrap 阶段（无 ADMIN）：注册必为 ADMIN，前端隐藏角色选择并展示提示
  const bootstrapMode = hasAdmin === false;
  // 已有 ADMIN 后：必须是 ADMIN 登录后才能注册新账号；否则注册 tab 不允许直接打开
  const canRegisterHere = bootstrapMode || (isLoggedIn() && isAdmin());

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const data = tab === 'login'
        ? await apiLogin(username, password)
        : await apiRegister(username, password, bootstrapMode ? 'ADMIN' : role);
      setToken(data.token, data.username, data.role);
      navigate('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="brand-block" style={{ marginBottom: '28px', justifyContent: 'center' }}>
          <div className="brand-icon"><ShieldCheck size={24} /></div>
          <div>
            <h1 style={{ fontSize: '17px', margin: 0 }}>建设用地报批审查报告</h1>
            <p style={{ fontSize: '13px', color: '#888', margin: '2px 0 0' }}>智能生成系统</p>
          </div>
        </div>

        <div className="segmented" style={{ marginBottom: '20px' }}>
          <button type="button" className={tab === 'login' ? 'active' : ''} onClick={() => setTab('login')}>登录</button>
          <button type="button" className={tab === 'register' ? 'active' : ''} onClick={() => setTab('register')}>注册</button>
        </div>

        {tab === 'register' && bootstrapMode && (
          <div className="message-strip notice-strip" style={{ marginBottom: '14px', fontSize: '12px' }}>
            系统首次注册：自动创建为管理员（ADMIN）。
          </div>
        )}
        {tab === 'register' && !bootstrapMode && !canRegisterHere && (
          <div className="message-strip error-strip" style={{ marginBottom: '14px', fontSize: '12px' }}>
            创建新账号需要先以管理员身份登录。
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="login-field">
            <label>用户名</label>
            <input
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="请输入用户名"
              autoComplete="username"
              required
            />
          </div>
          <div className="login-field">
            <label>密码</label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="请输入密码"
              autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
              required
            />
          </div>
          {tab === 'register' && !bootstrapMode && (
            <div className="login-field">
              <label>角色</label>
              <select value={role} onChange={e => setRole(e.target.value as 'ADMIN' | 'REPORTER' | 'VIEWER')}>
                <option value="REPORTER">报告员（REPORTER）— 可上传/改挂/导出</option>
                <option value="VIEWER">查看者（VIEWER）— 只读</option>
                <option value="ADMIN">管理员（ADMIN）— 全部权限</option>
              </select>
            </div>
          )}
          {error && (
            <div className="message-strip error-strip" style={{ marginBottom: '14px', fontSize: '13px' }}>
              {error}
            </div>
          )}
          <button
            className="btn btn-primary full"
            type="submit"
            disabled={loading || (tab === 'register' && !canRegisterHere)}
            style={{ marginTop: '4px' }}
          >
            {loading ? '处理中…' : tab === 'login' ? '登录' : '注册账号'}
          </button>
        </form>
      </div>
    </div>
  );
}
