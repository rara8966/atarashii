import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  FileUp, FileSearch, Loader2, AlertTriangle, Trash2, ArrowRight, RefreshCw, CheckCircle2
} from 'lucide-react';
import {
  v2GetProject, v2UploadFiles, v2AnalysisStatus, v2ListFiles, v2DeleteFile
} from '../api';
import type { ProjectRecord, ProjectFile, AnalysisProgress, AiConfig } from '../types';
import NavBar from './NavBar';

const AI_CONFIG_KEY = 'policy-report-demo-ai-config';
const UPLOAD_CONCURRENCY = 10;

function readAiConfig(): AiConfig {
  const fallback: AiConfig = { provider: 'deepseek', deepseekApiKey: '', deepseekModel: 'deepseek-chat', doubaoApiKey: '', doubaoEndpoint: '' };
  try { return { ...fallback, ...JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || '{}') }; } catch { return fallback; }
}

const STATUS_MAP: Record<string, { cls: string; text: string }> = {
  PENDING:   { cls: 'badge-optional', text: '等待分析' },
  RUNNING:   { cls: 'badge-uploaded', text: '分析中…' },
  ANALYZING: { cls: 'badge-uploaded', text: '分析中…' },
  DONE:      { cls: 'badge-uploaded', text: '✓ 已分析' },
  FAILED:    { cls: 'badge-required', text: '✕ 失败' },
};

async function collectDropFiles(event: React.DragEvent): Promise<File[]> {
  const entries = Array.from(event.dataTransfer.items)
    .map((item) => item.webkitGetAsEntry?.())
    .filter((e): e is FileSystemEntry => e != null);
  if (entries.length === 0) return Array.from(event.dataTransfer.files);
  async function readEntry(entry: FileSystemEntry): Promise<File[]> {
    if (entry.isFile) return new Promise((r) => (entry as FileSystemFileEntry).file((f) => r([f]), () => r([])));
    if (entry.isDirectory) {
      const reader = (entry as FileSystemDirectoryEntry).createReader();
      const all: FileSystemEntry[] = [];
      await new Promise<void>((r) => {
        const next = () => reader.readEntries((b) => { if (b.length === 0) r(); else { all.push(...b); next(); } }, () => r());
        next();
      });
      return (await Promise.all(all.map(readEntry))).flat();
    }
    return [];
  }
  return (await Promise.all(entries.map(readEntry))).flat();
}

export default function ProjectUpload() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [project, setProject] = useState<ProjectRecord | null>(null);
  const [files, setFiles] = useState<ProjectFile[]>([]);
  const [progress, setProgress] = useState<AnalysisProgress | null>(null);
  const [aiConfig, setAiConfig] = useState<AiConfig>(readAiConfig);
  const [uploadProgress, setUploadProgress] = useState<{ current: number; total: number; fileName: string } | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [dragging, setDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refreshData = useCallback(async () => {
    if (!id) return;
    const [proj, fileList, prog] = await Promise.all([v2GetProject(id), v2ListFiles(id), v2AnalysisStatus(id)]);
    setProject(proj);
    setFiles(fileList);
    setProgress(prog);
    return prog;
  }, [id]);

  useEffect(() => { void refreshData(); }, [refreshData]);

  useEffect(() => {
    if (!id) return;
    const poll = async () => {
      try {
        const [fileList, prog] = await Promise.all([v2ListFiles(id), v2AnalysisStatus(id)]);
        setFiles(fileList);
        setProgress(prog);
      } catch { /* swallow */ }
    };
    pollRef.current = setInterval(poll, 2000);
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [id]);

  async function handleFiles(newFiles: File[]) {
    if (!id || newFiles.length === 0) return;
    setError('');
    setNotice('');
    localStorage.setItem(AI_CONFIG_KEY, JSON.stringify(aiConfig));
    setUploadProgress({ current: 0, total: newFiles.length, fileName: '' });

    const failed: string[] = [];
    let completed = 0;
    const queue = [...newFiles];

    const worker = async () => {
      while (queue.length > 0) {
        const file = queue.shift();
        if (!file) break;
        try {
          await v2UploadFiles(id, [file], aiConfig);
        } catch {
          failed.push(file.name);
        } finally {
          completed++;
          setUploadProgress({ current: completed, total: newFiles.length, fileName: file.name });
        }
      }
    };

    try {
      await Promise.all(Array.from({ length: Math.min(UPLOAD_CONCURRENCY, queue.length) }, worker));
      const okCount = newFiles.length - failed.length;
      if (failed.length === 0) setNotice(`已上传 ${okCount} 份材料，后台正在分析…`);
      else if (okCount === 0) setError(`全部 ${failed.length} 份上传失败：${failed.slice(0, 3).join('、')}${failed.length > 3 ? ' 等' : ''}`);
      else setNotice(`成功 ${okCount} 份，失败 ${failed.length} 份：${failed.slice(0, 3).join('、')}${failed.length > 3 ? ' 等' : ''}`);
      await refreshData();
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setTimeout(() => setUploadProgress(null), 1500);
    }
  }

  async function deleteFile(fileId: string, name: string) {
    if (!id) return;
    if (!window.confirm(`确认删除 "${name}"？此操作会同时删除文件和分析结果。`)) return;
    try {
      await v2DeleteFile(id, fileId);
      setNotice(`已删除 ${name}`);
      await refreshData();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    }
  }

  const allDone = !!(progress && progress.total > 0 && progress.done + progress.failed >= progress.total);
  const analysisPct = progress && progress.total > 0 ? Math.round(((progress.done + progress.failed) / progress.total) * 100) : 0;
  const uploading = !!uploadProgress;
  const uploadPct = uploadProgress ? Math.round((uploadProgress.current / uploadProgress.total) * 100) : 0;

  return (
    <div className="app-shell">
      <NavBar project={project} crumb="上传材料" />
      {(error || notice) && (
        <div className={error ? 'message-strip error-strip' : 'message-strip notice-strip'}>
          {error ? <AlertTriangle size={16} /> : <CheckCircle2 size={16} />}
          {error || notice}
        </div>
      )}

      <div className="upload-page-body">
        <section className="card ai-card">
          <div className="card-header">AI 配置</div>
          <div className="card-body compact-body">
            <div className="ai-config-label">文字分析模型</div>
            <div className="ai-config-row">
              <div className="segmented" style={{ marginBottom: 0, flex: '0 0 auto' }}>
                <button type="button" className={aiConfig.provider === 'deepseek' ? 'active' : ''} onClick={() => setAiConfig({ ...aiConfig, provider: 'deepseek' })}>DeepSeek</button>
                <button type="button" className={aiConfig.provider === 'rules' ? 'active' : ''} onClick={() => setAiConfig({ ...aiConfig, provider: 'rules' })}>规则引擎</button>
              </div>
              {aiConfig.provider === 'deepseek' && (
                <>
                  <input className="config-input" type="password" placeholder="DeepSeek API Key"
                         value={aiConfig.deepseekApiKey}
                         onChange={e => setAiConfig({ ...aiConfig, deepseekApiKey: e.target.value })}
                         style={{ flex: '1 1 220px', minWidth: '160px' }} />
                  <input className="config-input" type="text" placeholder="模型 (deepseek-chat)"
                         value={aiConfig.deepseekModel}
                         onChange={e => setAiConfig({ ...aiConfig, deepseekModel: e.target.value })}
                         style={{ flex: '0 1 200px' }} />
                </>
              )}
            </div>
            <div className="ai-config-divider" />
            <div className="ai-config-label">视觉分析（图片/扫描件）— 豆包</div>
            <div className="ai-config-row">
              <input className="config-input" type="password" placeholder="豆包 API Key"
                     value={aiConfig.doubaoApiKey}
                     onChange={e => setAiConfig({ ...aiConfig, doubaoApiKey: e.target.value })}
                     style={{ flex: '1 1 220px', minWidth: '160px' }} />
              <input className="config-input" type="text" placeholder="豆包 Endpoint ID（ep-xxx）"
                     value={aiConfig.doubaoEndpoint}
                     onChange={e => setAiConfig({ ...aiConfig, doubaoEndpoint: e.target.value })}
                     style={{ flex: '1 1 220px', minWidth: '180px' }} />
            </div>
            <div className="ai-config-hint">
              留空则跳过豆包视觉分析，扫描件 / 图片仅走本地 OCR；填好可显著提升表格、签章、手写件识别准确率。
            </div>
          </div>
        </section>

        <section
          className={`upload-dropzone ${dragging ? 'dragging' : ''}`}
          onDragOver={e => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={e => { e.preventDefault(); setDragging(false); void collectDropFiles(e).then(handleFiles); }}
        >
          <FileUp size={28} />
          <div>
            <strong>批量上传材料</strong>
            <span>把 PDF、Word、图片、txt 全部拖进来，系统按文件名和内容自动归档到八步</span>
          </div>
          <button className="btn btn-outline full" type="button" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
            {uploading ? <><Loader2 className="spin" size={16} />上传中…</> : <><FileSearch size={16} />选择/拖入</>}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept=".pdf,.doc,.docx,.txt,.md,.png,.jpg,.jpeg"
            className="hidden-input"
            onChange={e => { void handleFiles(Array.from(e.target.files ?? [])); e.currentTarget.value = ''; }}
          />
          {uploadProgress && (
            <div className="upload-progress">
              <div className="upload-progress-bar" style={{ width: `${uploadPct}%` }} />
              <span className="upload-progress-label">上传 {uploadProgress.current} / {uploadProgress.total} — {uploadProgress.fileName}</span>
            </div>
          )}
        </section>

        {progress && progress.total > 0 && (
          <section className="card analysis-progress-card">
            <div className="analysis-progress-row">
              <span className="analysis-progress-text">
                后台分析进度：{progress.done + progress.failed} / {progress.total}
                {progress.failed > 0 && <span style={{ color: '#dc2626' }}>（失败 {progress.failed}）</span>}
                {progress.running > 0 && <span style={{ color: '#2563eb' }}>（分析中 {progress.running}）</span>}
              </span>
              <button className="btn btn-outline" type="button" onClick={() => void refreshData()}>
                <RefreshCw size={14} />刷新
              </button>
            </div>
            <div className="upload-progress" style={{ marginTop: '8px' }}>
              <div className="upload-progress-bar" style={{ width: `${analysisPct}%`, background: allDone ? '#16a34a' : '#2563eb' }} />
              <span className="upload-progress-label">{analysisPct}%</span>
            </div>
          </section>
        )}

        <section className="upload-file-list-section">
          <div className="v2-section-title">
            <span>已上传文件（{files.length} 份）</span>
            {allDone && (
              <button className="btn btn-primary" type="button" onClick={() => navigate(`/projects/${id}/type-confirm`)}>
                下一步：确认类型 <ArrowRight size={15} />
              </button>
            )}
            {!allDone && progress && progress.total > 0 && (
              <span style={{ fontSize: '13px', color: '#888' }}>
                <Loader2 className="spin" size={14} style={{ marginRight: '4px' }} />分析中，请稍候…
              </span>
            )}
          </div>

          {files.length === 0 ? (
            <p className="quiet" style={{ marginTop: '16px' }}>尚未上传任何材料。</p>
          ) : (
            <div className="file-card-list">
              {files.map(f => {
                const s = STATUS_MAP[f.analysisStatus] ?? { cls: 'badge-optional', text: f.analysisStatus };
                return (
                  <div key={f.id} className="file-card">
                    <div className="file-card-name" title={f.originalName}>{f.originalName}</div>
                    <div className="file-card-meta">
                      <span className={s.cls}>{s.text}</span>
                      {f.aiSuggestedStep && <span className="file-step-tag">AI建议→第{f.aiSuggestedStep}步</span>}
                      {f.currentStep && <span className="file-step-tag active">已归档→第{f.currentStep}步</span>}
                    </div>
                    <button className="withdraw-btn" type="button" onClick={() => void deleteFile(f.id, f.originalName)}>
                      <Trash2 size={13} />删除
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
