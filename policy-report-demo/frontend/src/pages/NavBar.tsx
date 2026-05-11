import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, LogOut, Home, ChevronRight, Shield, UserCog, Eye, Settings, LogIn, User } from 'lucide-react';
import { clearToken, getUsername, getRole, isLoggedIn } from '../auth';
import type { ProjectRecord } from '../types';
import SettingsModal from './SettingsModal';

const ROLE_META: Record<string, { label: string; color: string; icon: JSX.Element }> = {
  ADMIN: { label: '管理员', color: '#bb4b5b', icon: <Shield size={12} /> },
  REPORTER: { label: '报告员', color: '#0d8a72', icon: <UserCog size={12} /> },
  VIEWER: { label: '查看者', color: '#5e6c80', icon: <Eye size={12} /> },
};

export default function NavBar({ project, crumb }: { project?: ProjectRecord | null; crumb?: string }) {
  const navigate = useNavigate();
  const role = getRole();
  const meta = role ? ROLE_META[role] : null;
  const loggedIn = isLoggedIn();
  const [settingsOpen, setSettingsOpen] = useState(false);

  function logout() {
    clearToken();
    navigate('/');
  }

  return (
    <header className="top-bar">
      <div className="brand-block" style={{ cursor: 'pointer' }} onClick={() => navigate('/')}>
        <div className="brand-icon"><ShieldCheck size={20} /></div>
        <div>
          <h1 style={{ fontSize: '15px' }}>建设用地报批审查报告智能生成系统</h1>
          {project && (
            <p style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '12px', color: '#888' }}>
              <span>{project.projectCode}</span>
              <ChevronRight size={12} />
              <span>{project.projectName}</span>
              {crumb && <><ChevronRight size={12} /><span>{crumb}</span></>}
            </p>
          )}
        </div>
      </div>
      <div className="top-meta">
        <button className="btn btn-outline" type="button" onClick={() => navigate('/')}>
          <Home size={15} />首页
        </button>
        <button className="btn btn-outline" type="button" onClick={() => setSettingsOpen(true)} title="AI 配置（DeepSeek / 豆包 Key）">
          <Settings size={15} />AI 配置
        </button>
        {loggedIn ? (
          <>
            <span style={{ fontSize: '13px', color: '#666', display: 'flex', alignItems: 'center', gap: '4px' }}>
              {getUsername()}
              {meta && (
                <span style={{
                  display: 'inline-flex', alignItems: 'center', gap: '3px',
                  padding: '2px 8px', borderRadius: '999px',
                  background: `${meta.color}1a`, color: meta.color,
                  fontSize: '11px', fontWeight: 600,
                }} title={`角色：${meta.label}`}>
                  {meta.icon}{meta.label}
                </span>
              )}
            </span>
            <button className="btn btn-outline" type="button" onClick={logout}>
              <LogOut size={15} />退出
            </button>
          </>
        ) : (
          <>
            <span style={{ fontSize: '13px', color: '#888', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <User size={13} />游客
            </span>
            <button className="btn btn-outline" type="button" onClick={() => navigate('/login')}>
              <LogIn size={15} />登录
            </button>
          </>
        )}
      </div>
      <SettingsModal open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </header>
  );
}
