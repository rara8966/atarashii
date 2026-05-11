import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, ArrowRight, Loader2 } from 'lucide-react';
import { v2GetProject, v2ListProjects, v2ConfirmType } from '../api';
import type { ProjectRecord, ProjectTypeOption } from '../types';
import NavBar from './NavBar';

export default function TypeConfirm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [project, setProject] = useState<ProjectRecord | null>(null);
  const [projectTypes, setProjectTypes] = useState<ProjectTypeOption[]>([]);
  const [selectedType, setSelectedType] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!id) return;
    Promise.all([v2GetProject(id), v2ListProjects()])
      .then(([proj, dash]) => {
        setProject(proj);
        setProjectTypes(dash.projectTypes);
        setSelectedType(proj.projectType);
      })
      .catch(err => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
  }, [id]);

  async function handleConfirm() {
    if (!id || !selectedType) return;
    setSaving(true);
    setError('');
    try {
      await v2ConfirmType(id, selectedType);
      navigate(`/projects/${id}/info-confirm`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '确认失败');
      setSaving(false);
    }
  }

  return (
    <div className="app-shell">
      <NavBar project={project} crumb="确认项目类型" />
      {error && <div className="message-strip error-strip"><AlertTriangle size={16} />{error}</div>}

      <div className="v2-confirm-body">
        <section className="card" style={{ maxWidth: '600px', margin: '0 auto' }}>
          <div className="card-header"><CheckCircle2 size={17} />确认项目类型</div>
          <div className="card-body" style={{ padding: '24px' }}>
            {loading ? (
              <div style={{ textAlign: 'center', padding: '32px', color: '#888' }}>
                <Loader2 className="spin" size={22} />
                <div style={{ marginTop: '8px' }}>加载中…</div>
              </div>
            ) : (
              <>
                <p style={{ marginBottom: '20px', color: '#555', fontSize: '14px', lineHeight: '1.6' }}>
                  系统已完成文件分析。请根据实际项目情况选择或确认项目类型，
                  类型决定了后续标准库匹配和用地指标校验规则。
                </p>

                <div style={{ marginBottom: '24px' }}>
                  <label style={{ display: 'block', fontWeight: 600, marginBottom: '8px', fontSize: '14px' }}>
                    项目类型
                  </label>
                  <select
                    value={selectedType}
                    onChange={e => setSelectedType(e.target.value)}
                    style={{ width: '100%', padding: '10px 12px', border: '1.5px solid #d1d5db', borderRadius: '8px', fontSize: '14px' }}
                  >
                    {projectTypes.map(t => (
                      <option key={t.value} value={t.value}>{t.label}</option>
                    ))}
                  </select>
                </div>

                {project && selectedType !== project.projectType && (
                  <div className="message-strip notice-strip" style={{ marginBottom: '16px', fontSize: '13px' }}>
                    <CheckCircle2 size={15} />
                    已从"{project.projectTypeLabel}"修改为"{projectTypes.find(t => t.value === selectedType)?.label}"
                  </div>
                )}

                <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
                  <button className="btn btn-outline" type="button" onClick={() => navigate(`/projects/${id}/upload`)}>
                    ← 返回上传
                  </button>
                  <button className="btn btn-primary" type="button" onClick={handleConfirm} disabled={saving || !selectedType}>
                    {saving ? <Loader2 className="spin" size={15} /> : <ArrowRight size={15} />}
                    确认类型，下一步
                  </button>
                </div>
              </>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
