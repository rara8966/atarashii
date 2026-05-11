import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AlertTriangle, CheckCircle2, Loader2, Download, RefreshCw, Eye } from 'lucide-react';
import { v2GetProject, v2GetPreview, v2ConfirmPreview, v2ExportReport } from '../api';
import type { ProjectRecord, PreviewStatus } from '../types';
import NavBar from './NavBar';

function downloadBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  URL.revokeObjectURL(url);
}

// 轻量 markdown 渲染：标题、加粗、列表、分隔线、段落（避免 <pre> 显示原始符号）
function renderInline(line: string): (string | JSX.Element)[] {
  const out: (string | JSX.Element)[] = [];
  const regex = /\*\*([^*]+)\*\*/g;
  let last = 0;
  let m: RegExpExecArray | null;
  let key = 0;
  while ((m = regex.exec(line)) !== null) {
    if (m.index > last) out.push(line.slice(last, m.index));
    out.push(<strong key={`b${key++}`}>{m[1]}</strong>);
    last = m.index + m[0].length;
  }
  if (last < line.length) out.push(line.slice(last));
  return out;
}

function MiniMarkdown({ text }: { text: string }) {
  const lines = text.split('\n');
  const elements: JSX.Element[] = [];
  let listBuf: string[] = [];
  function flushList() {
    if (listBuf.length === 0) return;
    elements.push(
      <ul key={`ul-${elements.length}`} style={{ paddingLeft: '20px', margin: '4px 0' }}>
        {listBuf.map((it, i) => <li key={i} style={{ marginBottom: '4px' }}>{renderInline(it)}</li>)}
      </ul>
    );
    listBuf = [];
  }
  for (let i = 0; i < lines.length; i++) {
    const ln = lines[i];
    if (ln.startsWith('### ')) {
      flushList();
      elements.push(<h3 key={i} style={{ marginTop: '20px', marginBottom: '8px', fontSize: '15px' }}>{renderInline(ln.slice(4))}</h3>);
    } else if (ln.startsWith('## ')) {
      flushList();
      elements.push(<h2 key={i} style={{ marginTop: '24px', marginBottom: '10px', fontSize: '17px', borderBottom: '1px solid #e5e7eb', paddingBottom: '4px' }}>{renderInline(ln.slice(3))}</h2>);
    } else if (ln.startsWith('# ')) {
      flushList();
      elements.push(<h1 key={i} style={{ marginTop: '8px', marginBottom: '14px', fontSize: '22px' }}>{renderInline(ln.slice(2))}</h1>);
    } else if (ln.startsWith('- ')) {
      listBuf.push(ln.slice(2));
    } else if (ln.trim() === '---') {
      flushList();
      elements.push(<hr key={i} style={{ border: 'none', borderTop: '1px solid #e5e7eb', margin: '14px 0' }} />);
    } else if (ln.trim() === '') {
      flushList();
      elements.push(<div key={i} style={{ height: '6px' }} />);
    } else {
      flushList();
      elements.push(<p key={i} style={{ margin: '4px 0', lineHeight: 1.7 }}>{renderInline(ln)}</p>);
    }
  }
  flushList();
  return <div className="markdown-rendered" style={{ color: '#142033' }}>{elements}</div>;
}

export default function Preview() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [project, setProject] = useState<ProjectRecord | null>(null);
  const [preview, setPreview] = useState<PreviewStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [exporting, setExporting] = useState<'docx' | 'md' | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  async function loadPreview() {
    if (!id) return;
    const [proj, pv] = await Promise.all([v2GetProject(id), v2GetPreview(id)]);
    setProject(proj);
    setPreview(pv);
    return pv;
  }

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    loadPreview().catch(err => setError(err instanceof Error ? err.message : '加载失败')).finally(() => setLoading(false));
  }, [id]);

  // 仅在 GENERATING 状态轮询；NONE/READY/CONFIRMED 不再轮询
  useEffect(() => {
    if (!id) return;
    if (preview && preview.status !== 'GENERATING') {
      if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
      return;
    }
    pollRef.current = setInterval(async () => {
      try {
        const pv = await v2GetPreview(id);
        setPreview(pv);
        if (pv.status !== 'GENERATING') {
          if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; }
        }
      } catch { /* ignore */ }
    }, 3000);
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [id, preview?.status]);

  async function handleConfirm() {
    if (!id) return;
    setConfirming(true);
    setError('');
    try {
      await v2ConfirmPreview(id);
      const pv = await v2GetPreview(id);
      setPreview(pv);
      setNotice('预览已确认，可以导出正式报告');
    } catch (err) {
      setError(err instanceof Error ? err.message : '确认失败');
    } finally {
      setConfirming(false);
    }
  }

  async function handleExport(format: 'docx' | 'md') {
    if (!id) return;
    setExporting(format);
    setError('');
    try {
      const blob = await v2ExportReport(id, format);
      const name = `${project?.projectName || '审查报告'}.${format}`.replace(/[\\/:*?"<>|]/g, '_');
      downloadBlob(blob, name);
      setNotice(`已导出 ${format.toUpperCase()} 文件`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '导出失败');
    } finally {
      setExporting(null);
    }
  }

  const isGenerating = preview?.status === 'GENERATING';
  const isReady = preview?.status === 'READY';
  const isConfirmed = preview?.status === 'CONFIRMED';
  const isNone = preview?.status === 'NONE';

  return (
    <div className="app-shell">
      <NavBar project={project} crumb="预览报告" />
      {(error || notice) && (
        <div className={error ? 'message-strip error-strip' : 'message-strip notice-strip'}>
          {error ? <AlertTriangle size={16} /> : <CheckCircle2 size={16} />}
          {error || notice}
        </div>
      )}

      <div className="preview-page-body">
        <div className="preview-page-actions">
          <button className="btn btn-outline" type="button" onClick={() => navigate(`/projects/${id}/workbench`)}>
            ← 返回工作台
          </button>
          <button className="btn btn-outline" type="button" onClick={() => void loadPreview()} disabled={isGenerating}>
            <RefreshCw size={15} />刷新
          </button>

          {(isReady || isConfirmed) && !isConfirmed && (
            <button className="btn btn-teal" type="button" onClick={handleConfirm} disabled={confirming}>
              {confirming ? <Loader2 className="spin" size={15} /> : <Eye size={15} />}
              确认报告
            </button>
          )}

          {isConfirmed && (
            <>
              <button className="btn btn-outline" type="button" onClick={() => void handleExport('md')} disabled={!!exporting}>
                {exporting === 'md' ? <Loader2 className="spin" size={15} /> : <Download size={15} />}导出 .md
              </button>
              <button className="btn btn-primary" type="button" onClick={() => void handleExport('docx')} disabled={!!exporting}>
                {exporting === 'docx' ? <Loader2 className="spin" size={15} /> : <Download size={15} />}导出 .docx
              </button>
            </>
          )}
        </div>

        <div className="preview-status-bar">
          {isGenerating && <><Loader2 className="spin" size={16} /><span>报告生成中，每隔 3 秒自动刷新…</span></>}
          {isReady && <><Eye size={16} /><span>报告已就绪，请确认后导出</span></>}
          {isConfirmed && <><CheckCircle2 size={16} style={{ color: '#16a34a' }} /><span style={{ color: '#16a34a' }}>已确认，可以导出正式报告</span></>}
          {isNone && <span style={{ color: '#888' }}>尚未生成预览，请返回工作台点击"生成预览报告"</span>}
          {!preview && !loading && <span style={{ color: '#888' }}>暂无预览，请返回工作台点击"生成预览报告"</span>}
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px', color: '#888' }}>
            <Loader2 className="spin" size={28} />
            <div style={{ marginTop: '12px' }}>加载预览…</div>
          </div>
        ) : preview?.markdown ? (
          <div className="markdown-preview-shell">
            <MiniMarkdown text={preview.markdown} />
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: '60px', color: '#aaa' }}>
            {isGenerating ? '报告生成中…' : '暂无预览内容'}
          </div>
        )}
      </div>
    </div>
  );
}
