import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ClipboardList, AlertTriangle, ArrowRight, Loader2 } from 'lucide-react';
import { v2GetProject, v2ConfirmInfo } from '../api';
import type { ProjectRecord } from '../types';
import NavBar from './NavBar';

export default function InfoConfirm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [project, setProject] = useState<ProjectRecord | null>(null);
  const [name, setName] = useState('');
  const [owner, setOwner] = useState('');
  const [location, setLocation] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!id) return;
    v2GetProject(id)
      .then(proj => {
        setProject(proj);
        setName(proj.projectName);
        setOwner(proj.owner);
        setLocation(proj.location);
      })
      .catch(err => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
  }, [id]);

  async function handleConfirm(e: React.FormEvent) {
    e.preventDefault();
    if (!id || !name.trim()) { setError('项目名称不能为空'); return; }
    setSaving(true);
    setError('');
    try {
      await v2ConfirmInfo(id, name.trim(), owner.trim(), location.trim());
      navigate(`/projects/${id}/workbench`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
      setSaving(false);
    }
  }

  return (
    <div className="app-shell">
      <NavBar project={project} crumb="确认项目信息" />
      {error && <div className="message-strip error-strip"><AlertTriangle size={16} />{error}</div>}

      <div className="v2-confirm-body">
        <section className="card" style={{ maxWidth: '600px', margin: '0 auto' }}>
          <div className="card-header"><ClipboardList size={17} />确认项目基本信息</div>
          <div className="card-body" style={{ padding: '24px' }}>
            {loading ? (
              <div style={{ textAlign: 'center', padding: '32px', color: '#888' }}>
                <Loader2 className="spin" size={22} />
                <div style={{ marginTop: '8px' }}>加载中…</div>
              </div>
            ) : (
              <form onSubmit={handleConfirm}>
                <p style={{ marginBottom: '20px', color: '#555', fontSize: '14px', lineHeight: '1.6' }}>
                  请核对或补充以下项目基本信息，这些信息将写入正式审查报告。
                  类型已确认为：<strong>{project?.projectTypeLabel}</strong>
                </p>

                <div className="home-form-grid">
                  <label style={{ gridColumn: '1 / -1' }}>
                    项目名称 <span style={{ color: '#e55' }}>*</span>
                    <input value={name} onChange={e => setName(e.target.value)} placeholder="项目全称" required />
                  </label>
                  <label>
                    建设单位
                    <input value={owner} onChange={e => setOwner(e.target.value)} placeholder="建设单位/业主单位" />
                  </label>
                  <label>
                    建设地点
                    <input value={location} onChange={e => setLocation(e.target.value)} placeholder="市、县、乡镇或具体位置" />
                  </label>
                </div>

                <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', marginTop: '24px' }}>
                  <button className="btn btn-outline" type="button" onClick={() => navigate(`/projects/${id}/type-confirm`)}>
                    ← 返回类型确认
                  </button>
                  <button className="btn btn-primary" type="submit" disabled={saving}>
                    {saving ? <Loader2 className="spin" size={15} /> : <ArrowRight size={15} />}
                    确认信息，进入工作台
                  </button>
                </div>
              </form>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
