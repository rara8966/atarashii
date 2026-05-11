import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, FolderOpen, RefreshCw, Loader2, AlertTriangle, CheckCircle2, Database, Trash2, Library } from 'lucide-react';
import { v2ListProjects, v2CreateProject, v2DeleteProject } from '../api';
import type { ProjectDashboard, ProjectRecord, CreateProjectPayload } from '../types';
import { canEdit, isAdmin } from '../auth';
import NavBar from './NavBar';

const STATUS_LABELS: Record<string, string> = {
  '草稿': '上传材料',
  'INFO_CONFIRM': '确认信息',
  'IN_PROGRESS': '工作台',
};

function projectRoute(p: ProjectRecord): string {
  if (p.status === 'IN_PROGRESS') return `/projects/${p.id}/workbench`;
  if (p.status === 'INFO_CONFIRM') return `/projects/${p.id}/info-confirm`;
  return `/projects/${p.id}/upload`;
}

function formatTime(v: string) {
  if (!v) return '—';
  try {
    const d = new Date(v);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  } catch {
    return v.slice(0, 16).replace('T', ' ');
  }
}

export default function ProjectList() {
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState<ProjectDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [creating, setCreating] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<CreateProjectPayload>({
    projectName: '', projectType: 'wind-power', owner: '', location: ''
  });

  async function load() {
    setLoading(true);
    setError('');
    try {
      setDashboard(await v2ListProjects());
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  async function handleDelete(p: ProjectRecord) {
    if (!window.confirm(`确认删除项目"${p.projectName}"（${p.projectCode}）？\n此操作不可恢复，将同时删除所有上传文件和分析结果。`)) return;
    setError('');
    try {
      await v2DeleteProject(p.id);
      setNotice(`已删除项目：${p.projectName}`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!form.projectName.trim()) { setError('请填写项目名称'); return; }
    setCreating(true);
    setError('');
    try {
      const created = await v2CreateProject(form);
      setNotice(`项目 ${created.projectCode} 已创建`);
      setShowForm(false);
      setForm({ projectName: '', projectType: 'wind-power', owner: '', location: '' });
      navigate(`/projects/${created.id}/upload`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建失败');
    } finally {
      setCreating(false);
    }
  }

  const projects = dashboard?.projects ?? [];
  const projectTypes = dashboard?.projectTypes ?? [];
  const standardCount = (dashboard?.standardSummaries ?? []).reduce((s, i) => s + i.count, 0);
  const editAllowed = canEdit();
  const adminOnly = isAdmin();

  return (
    <div className="app-shell">
      <NavBar />
      {(error || notice) && (
        <div className={error ? 'message-strip error-strip' : 'message-strip notice-strip'}>
          {error ? <AlertTriangle size={16} /> : <CheckCircle2 size={16} />}
          {error || notice}
        </div>
      )}

      <div className="home-shell" style={{ paddingTop: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
          <h2 style={{ margin: 0, fontSize: '17px' }}>项目列表</h2>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="btn btn-outline" type="button" onClick={() => navigate('/standards')}>
              <Library size={15} />用地标准管理
            </button>
            <button className="btn btn-outline" type="button" onClick={() => void load()} disabled={loading}>
              <RefreshCw size={15} />刷新
            </button>
            {editAllowed && (
              <button className="btn btn-primary" type="button" onClick={() => setShowForm(!showForm)}>
                <Plus size={15} />新建项目
              </button>
            )}
          </div>
        </div>

        {showForm && (
          <section className="home-panel create-project-panel" style={{ marginBottom: '20px' }}>
            <div className="panel-title"><Plus size={17} />新建项目草稿</div>
            <form onSubmit={handleCreate}>
              <div className="home-form-grid">
                <label>项目名称 <span style={{ color: '#e55' }}>*</span>
                  <input value={form.projectName} onChange={e => setForm({ ...form, projectName: e.target.value })} placeholder="例如：兴宁五塘风电场一期工程" required />
                </label>
                <label>项目类型
                  <select value={form.projectType} onChange={e => setForm({ ...form, projectType: e.target.value })}>
                    {projectTypes.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                  </select>
                </label>
                <label>建设单位
                  <input value={form.owner} onChange={e => setForm({ ...form, owner: e.target.value })} placeholder="建设单位/业主单位" />
                </label>
                <label>建设地点
                  <input value={form.location} onChange={e => setForm({ ...form, location: e.target.value })} placeholder="市、县、乡镇或具体位置" />
                </label>
              </div>
              <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
                <button className="btn btn-primary" type="submit" disabled={creating}>
                  {creating ? <Loader2 className="spin" size={15} /> : <Plus size={15} />}
                  创建并上传材料
                </button>
                <button className="btn btn-outline" type="button" onClick={() => setShowForm(false)}>取消</button>
              </div>
            </form>
          </section>
        )}

        <div className="home-grid" style={{ marginBottom: '20px' }}>
          <section className="home-panel stats-panel">
            <div className="panel-title"><Database size={17} />系统库状态</div>
            <div className="metric-grid">
              <div className="metric-cell"><strong>{projects.length}</strong><span>落库项目</span></div>
              <div className="metric-cell"><strong>{standardCount}</strong><span>用地标准</span></div>
              <div className="metric-cell"><strong>{dashboard?.standardSummaries.length ?? 0}</strong><span>项目类型</span></div>
            </div>
          </section>
        </div>

        <section className="home-panel project-list-panel">
          <div className="panel-title"><FolderOpen size={17} />草稿箱 / 历史项目</div>
          {loading ? (
            <div style={{ padding: '24px', textAlign: 'center', color: '#888' }}>
              <Loader2 className="spin" size={20} style={{ marginBottom: '8px' }} />
              <div>加载中…</div>
            </div>
          ) : projects.length === 0 ? (
            <p className="quiet">还没有项目，点击右上角"新建项目"开始。</p>
          ) : (
            <div className="project-table-wrap">
              <table className="project-table">
                <thead>
                  <tr>
                    <th>系统编号</th>
                    <th>项目名称</th>
                    <th>类型</th>
                    <th>建设单位</th>
                    <th>状态</th>
                    <th>更新时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {projects.map(p => (
                    <tr key={p.id}>
                      <td style={{ fontFamily: 'monospace', fontSize: '12px' }}>{p.projectCode}</td>
                      <td>
                        <strong>{p.projectName}</strong>
                        {p.location && <div className="subtle-text">{p.location}</div>}
                      </td>
                      <td>{p.projectTypeLabel}</td>
                      <td>{p.owner || '未填写'}</td>
                      <td>
                        <span className={`status-badge ${p.status === 'IN_PROGRESS' ? 'pass' : 'warn'}`}>
                          {p.status === '草稿' ? '草稿' : p.status === 'INFO_CONFIRM' ? '待确认' : p.status === 'IN_PROGRESS' ? '进行中' : p.status}
                        </span>
                      </td>
                      <td style={{ fontSize: '12px', color: '#888' }}>{formatTime(p.updatedAt)}</td>
                      <td className="project-actions-cell">
                        <button className="modify-btn open-btn" type="button" onClick={() => navigate(projectRoute(p))}>
                          <FolderOpen size={13} />{STATUS_LABELS[p.status] ?? '进入'}
                        </button>
                        {adminOnly && (
                          <button className="withdraw-btn" type="button" onClick={() => void handleDelete(p)} title="删除项目">
                            <Trash2 size={13} />删除
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
