import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Upload, FileText, RefreshCw, Loader2, AlertTriangle, CheckCircle2,
  ChevronDown, ChevronUp, Power, FileSearch, Trash2, X, Table as TableIcon,
  Sparkles, Tag, CheckCircle as CheckIcon
} from 'lucide-react';
import {
  listProjectTypes, toggleProjectType, listStandardItems,
  uploadStandardDocx, clearStandardItems,
  listStandardTables, uploadStandardTablesDocx, clearStandardTables,
  aiPrelabelAllTables,
} from '../api';
import type { ProjectTypeDto, StandardItemDto, StandardTableDto, TableAnnotation } from '../types';
import { aiConfigHasKey, readAiConfig } from '../aiConfig';
import NavBar from './NavBar';
import SettingsModal from './SettingsModal';
import AnnotationDrawer from './AnnotationDrawer';
import ConfirmModal from './ConfirmModal';

function parseAnnotation(json: string): TableAnnotation | null {
  if (!json) return null;
  try {
    const v = JSON.parse(json);
    return {
      queryKeys: Array.isArray(v.queryKeys) ? v.queryKeys : [],
      valueCols: Array.isArray(v.valueCols) ? v.valueCols : [],
      applicableSituations: Array.isArray(v.applicableSituations) ? v.applicableSituations : [],
      functionalZone: typeof v.functionalZone === 'string' ? v.functionalZone : '',
      notes: typeof v.notes === 'string' ? v.notes : '',
      source: v.source,
      model: v.model,
    };
  } catch { return null; }
}

/** 精简表格行：只显示表名 + 状态徽章 + 操作按钮；点「标注」打开全屏抽屉。 */
function StandardTableRow({ table, onOpen }: {
  table: StandardTableDto;
  onOpen: (t: StandardTableDto) => void;
}) {
  const zone = parseAnnotation(table.annotationJson)?.functionalZone ?? '';
  return (
    <div style={{
      marginBottom: '8px', padding: '10px 12px', background: '#fff',
      border: table.annotated ? '1px solid #0d8a72' : '1px solid #e5e7eb',
      borderRadius: '8px', display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap',
    }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: '13px', fontWeight: 600, color: '#142033' }}>
          {table.tableCode && <span style={{ color: '#0d8a72', marginRight: '6px' }}>{table.tableCode}</span>}
          {(table.tableTitle || '').replace(table.tableCode || '', '').trim()}
        </div>
        {(table.chapter || table.unit) && (
          <div style={{ fontSize: '11px', color: '#888', marginTop: '2px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {table.chapter}{table.chapter && table.unit ? ' · ' : ''}{table.unit ? `单位：${table.unit}` : ''}
          </div>
        )}
      </div>
      {table.annotated ? (
        <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '999px', background: '#0d8a721a', color: '#0d8a72', display: 'inline-flex', alignItems: 'center', gap: '3px', fontWeight: 600 }}>
          <CheckIcon size={11} />已标注
        </span>
      ) : table.aiPrelabeled ? (
        <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '999px', background: '#b676111a', color: '#b67611', display: 'inline-flex', alignItems: 'center', gap: '3px', fontWeight: 600 }}>
          <Sparkles size={11} />AI 待确认
        </span>
      ) : (
        <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '999px', background: '#5e6c801a', color: '#5e6c80', fontWeight: 600 }}>
          未标注
        </span>
      )}
      {zone && (
        <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '999px', background: '#3b82f61a', color: '#3b82f6', fontWeight: 600 }}>
          🏗️ {zone}
        </span>
      )}
      <button type="button" className="btn btn-primary" onClick={() => onOpen(table)} style={{ fontSize: '12px', padding: '4px 12px' }}>
        <Tag size={12} />标注
      </button>
    </div>
  );
}

export default function StandardLibrary() {
  const navigate = useNavigate();
  const [types, setTypes] = useState<ProjectTypeDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [expanded, setExpanded] = useState<{ key: string; mode: 'items' | 'tables' } | null>(null);
  const [items, setItems] = useState<Map<string, StandardItemDto[]>>(new Map());
  const [itemsLoading, setItemsLoading] = useState<string | null>(null);
  const [tables, setTables] = useState<Map<string, StandardTableDto[]>>(new Map());
  const [tablesLoading, setTablesLoading] = useState<string | null>(null);
  const [prelabelingAll, setPrelabelingAll] = useState<string | null>(null);
  const [drawerTable, setDrawerTable] = useState<StandardTableDto | null>(null);
  const [keyModalOpen, setKeyModalOpen] = useState(false);
  const [confirmKeyOpen, setConfirmKeyOpen] = useState(false);

  async function prelabelAll(typeKey: string) {
    // AI 预标注：非强依赖场景，未配 key 弹居中确认弹窗引导去配置、不拦截访问
    if (!aiConfigHasKey()) { setConfirmKeyOpen(true); return; }
    const cfg = readAiConfig();
    if (!window.confirm('AI 一键预标注会对该类型下所有未标注的表逐个调用 DeepSeek，可能耗时较长。继续？')) return;
    setPrelabelingAll(typeKey);
    setError('');
    setNotice('');
    try {
      const result = await aiPrelabelAllTables(typeKey, cfg.deepseekApiKey, cfg.deepseekModel || 'deepseek-chat', true);
      setNotice(result.message);
      // 重新拉表格列表，刷新标注徽章
      const list = await listStandardTables(typeKey);
      setTables(prev => new Map(prev).set(typeKey, list));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 预标注失败');
    } finally {
      setPrelabelingAll(null);
    }
  }
  const [uploadModal, setUploadModal] = useState<{ file: File } | null>(null);
  const [uploadingKey, setUploadingKey] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const reload = useCallback(async () => {
    setError('');
    setLoading(true);
    try {
      setTypes(await listProjectTypes());
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void reload(); }, [reload]);

  function pickFile(file: File) {
    if (!file.name.toLowerCase().endsWith('.docx')) {
      setError('仅支持 .docx 格式');
      return;
    }
    setError('');
    setUploadModal({ file });
  }

  async function toggle(typeKey: string) {
    setError('');
    try {
      const updated = await toggleProjectType(typeKey);
      setTypes(prev => prev.map(t => t.typeKey === typeKey ? updated : t));
      setNotice(`已${updated.enabled ? '启用' : '停用'}：${updated.label}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    }
  }

  async function expand(typeKey: string, mode: 'items' | 'tables') {
    if (expanded && expanded.key === typeKey && expanded.mode === mode) {
      setExpanded(null);
      return;
    }
    setExpanded({ key: typeKey, mode });
    if (mode === 'items' && !items.has(typeKey)) {
      setItemsLoading(typeKey);
      try {
        const list = await listStandardItems(typeKey);
        setItems(prev => new Map(prev).set(typeKey, list));
      } catch (err) {
        setError(err instanceof Error ? err.message : '加载条目失败');
      } finally {
        setItemsLoading(null);
      }
    }
    if (mode === 'tables' && !tables.has(typeKey)) {
      setTablesLoading(typeKey);
      try {
        const list = await listStandardTables(typeKey);
        setTables(prev => new Map(prev).set(typeKey, list));
      } catch (err) {
        setError(err instanceof Error ? err.message : '加载表格失败');
      } finally {
        setTablesLoading(null);
      }
    }
  }

  async function clearItems(typeKey: string, label: string) {
    if (!window.confirm(`确认清空「${label}」下的所有文字标准条目？`)) return;
    try {
      const result = await clearStandardItems(typeKey);
      setNotice(`已清空 ${result.removed} 条标准条目`);
      setItems(prev => { const m = new Map(prev); m.delete(typeKey); return m; });
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : '清空失败');
    }
  }

  async function clearTables(typeKey: string, label: string) {
    if (!window.confirm(`确认清空「${label}」下的所有结构化表格？`)) return;
    try {
      const result = await clearStandardTables(typeKey);
      setNotice(`已清空 ${result.removed} 张表格`);
      setTables(prev => { const m = new Map(prev); m.delete(typeKey); return m; });
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : '清空失败');
    }
  }

  async function submitUpload(typeKey: string, typeLabel: string, replace: boolean) {
    if (!uploadModal) return;
    setUploadingKey(typeKey);
    setError('');
    try {
      // 同时入两张表：文字切片 land_use_standard + 结构化二维表 standard_tables
      const [items, tablesResp] = await Promise.all([
        uploadStandardDocx(typeKey, typeLabel, uploadModal.file, replace),
        uploadStandardTablesDocx(typeKey, uploadModal.file, replace, typeLabel).catch(err => ({
          imported: 0, total: 0, typeKey, message: 'POI 解析未能写入表格：' + (err instanceof Error ? err.message : '未知错误'),
        }))
      ]);
      const t = tablesResp.imported > 0 ? `，并解析出 ${tablesResp.imported} 张结构化表格` : '';
      setNotice(`${items.message}${t}`);
      setUploadModal(null);
      setItems(prev => { const m = new Map(prev); m.delete(typeKey); return m; });
      setTables(prev => { const m = new Map(prev); m.delete(typeKey); return m; });
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploadingKey(null);
    }
  }

  return (
    <div className="app-shell">
      <NavBar crumb="用地标准管理" />
      {(error || notice) && (
        <div className={error ? 'message-strip error-strip' : 'message-strip notice-strip'}>
          {error ? <AlertTriangle size={16} /> : <CheckCircle2 size={16} />}
          {error || notice}
          <button type="button" onClick={() => { setError(''); setNotice(''); }} style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer' }}>
            <X size={14} />
          </button>
        </div>
      )}

      <div className="home-shell" style={{ paddingTop: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
          <div>
            <h2 style={{ margin: 0, fontSize: '17px' }}>用地标准管理</h2>
            <p className="quiet" style={{ marginTop: '4px', marginBottom: 0, fontSize: '13px' }}>
              拖入 .docx 自动解析并入库；已有项目类型可启用/停用，停用后新建项目时不再出现在下拉中
            </p>
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="btn btn-outline" type="button" onClick={() => navigate('/')}>← 返回首页</button>
            <button className="btn btn-outline" type="button" onClick={() => void reload()} disabled={loading}>
              <RefreshCw size={15} />刷新
            </button>
          </div>
        </div>

        {/* 拖拽上传区 */}
        <section
          className={`upload-dropzone ${dragging ? 'dragging' : ''}`}
          style={{ marginBottom: '20px' }}
          onDragOver={e => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={e => {
            e.preventDefault();
            setDragging(false);
            const f = Array.from(e.dataTransfer.files).find(f => f.name.toLowerCase().endsWith('.docx'));
            if (f) pickFile(f);
            else setError('请拖入 .docx 文件');
          }}
        >
          <Upload size={28} />
          <div>
            <strong>上传新的用地标准 (.docx)</strong>
            <span>系统自动按章节切片入库；上传后会要求填写项目类型 key 和显示名称</span>
          </div>
          <button className="btn btn-outline full" type="button" onClick={() => fileInputRef.current?.click()}>
            <FileSearch size={16} />选择 .docx
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".docx"
            style={{ display: 'none' }}
            onChange={e => {
              const f = e.target.files?.[0];
              if (f) pickFile(f);
              e.currentTarget.value = '';
            }}
          />
        </section>

        {/* 项目类型卡片 */}
        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px', color: '#888' }}>
            <Loader2 className="spin" size={24} />
            <div style={{ marginTop: '8px' }}>加载中…</div>
          </div>
        ) : types.length === 0 ? (
          <p className="quiet" style={{ textAlign: 'center', padding: '40px' }}>暂无项目类型，请上传 .docx 创建</p>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(380px, 1fr))', gap: '12px' }}>
            {types.map(t => (
              <div
                key={t.typeKey}
                style={{
                  background: '#fff',
                  border: '1px solid #e5e7eb',
                  borderRadius: '10px',
                  padding: '14px 16px',
                  opacity: t.enabled ? 1 : 0.6,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', marginBottom: '8px' }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: '14px', fontWeight: 600, color: '#142033', marginBottom: '2px' }}>{t.label}</div>
                    <div style={{ fontSize: '12px', color: '#888', fontFamily: 'monospace' }}>{t.typeKey}</div>
                  </div>
                  <span style={{
                    fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                    background: t.enabled ? '#0d8a721a' : '#5e6c801a',
                    color: t.enabled ? '#0d8a72' : '#5e6c80',
                    fontWeight: 600, whiteSpace: 'nowrap',
                  }}>{t.enabled ? '已启用' : '已停用'}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '12px', color: '#666', marginBottom: '8px', flexWrap: 'wrap' }}>
                  <span>📚 {t.standardCount} 条文字</span>
                  <span style={{ color: '#0d8a72' }}>📊 {t.tableCount} 张表格</span>
                  {t.sourceFile && <span title={t.sourceFile} style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '200px' }}>📄 {t.sourceFile}</span>}
                </div>
                <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                  <button className="btn btn-outline" type="button" onClick={() => void toggle(t.typeKey)} style={{ fontSize: '12px', padding: '4px 10px' }}>
                    <Power size={12} />{t.enabled ? '停用' : '启用'}
                  </button>
                  <button
                    className="btn btn-outline" type="button"
                    onClick={() => void expand(t.typeKey, 'items')}
                    style={{ fontSize: '12px', padding: '4px 10px' }}
                  >
                    {expanded?.key === t.typeKey && expanded.mode === 'items' ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                    <FileText size={12} />
                    {expanded?.key === t.typeKey && expanded.mode === 'items' ? '收起文字' : '查看文字'}
                  </button>
                  <button
                    className="btn btn-outline" type="button"
                    onClick={() => void expand(t.typeKey, 'tables')}
                    style={{ fontSize: '12px', padding: '4px 10px', borderColor: '#0d8a72', color: '#0d8a72' }}
                  >
                    {expanded?.key === t.typeKey && expanded.mode === 'tables' ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                    <TableIcon size={12} />
                    {expanded?.key === t.typeKey && expanded.mode === 'tables' ? '收起表格' : '查看表格'}
                  </button>
                  {t.standardCount > 0 && (
                    <button className="btn btn-outline" type="button" onClick={() => void clearItems(t.typeKey, t.label)} style={{ fontSize: '12px', padding: '4px 10px', color: '#bb4b5b' }}>
                      <Trash2 size={12} />清空文字
                    </button>
                  )}
                  {t.tableCount > 0 && (
                    <button className="btn btn-outline" type="button" onClick={() => void clearTables(t.typeKey, t.label)} style={{ fontSize: '12px', padding: '4px 10px', color: '#bb4b5b' }}>
                      <Trash2 size={12} />清空表格
                    </button>
                  )}
                </div>

                {expanded?.key === t.typeKey && expanded.mode === 'items' && (
                  <div style={{ marginTop: '10px', padding: '10px', background: '#fafbfc', border: '1px solid #eef0f2', borderRadius: '8px', maxHeight: '360px', overflowY: 'auto' }}>
                    {itemsLoading === t.typeKey ? (
                      <div style={{ textAlign: 'center', color: '#888' }}><Loader2 className="spin" size={14} /> 加载条目…</div>
                    ) : (items.get(t.typeKey) ?? []).length === 0 ? (
                      <p className="quiet" style={{ margin: 0, fontSize: '12px' }}>暂无文字条目，上传 .docx 即可导入</p>
                    ) : (
                      (items.get(t.typeKey) ?? []).map(it => (
                        <div key={it.id} style={{ marginBottom: '8px', paddingBottom: '8px', borderBottom: '1px dashed #e5e7eb' }}>
                          <div style={{ fontSize: '12px', fontWeight: 600, marginBottom: '2px' }}>
                            <FileText size={11} style={{ marginRight: '4px', display: 'inline' }} />
                            {it.chapterTitle}
                          </div>
                          <div style={{ fontSize: '11px', color: '#666', lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>
                            {it.content.length > 400 ? it.content.slice(0, 400) + '…' : it.content}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                )}

                {expanded?.key === t.typeKey && expanded.mode === 'tables' && (
                  <div style={{ marginTop: '10px', padding: '10px', background: '#fafbfc', border: '1px solid #eef0f2', borderRadius: '8px', maxHeight: '640px', overflowY: 'auto' }}>
                    {(tables.get(t.typeKey) ?? []).length > 0 && (
                      <div style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        marginBottom: '10px', paddingBottom: '8px', borderBottom: '1px dashed #d1d5db',
                      }}>
                        <span style={{ fontSize: '12px', color: '#555' }}>
                          已标注 {(tables.get(t.typeKey) ?? []).filter(x => x.annotated).length}
                          {' / '}
                          {(tables.get(t.typeKey) ?? []).length}
                          {(tables.get(t.typeKey) ?? []).filter(x => x.aiPrelabeled && !x.annotated).length > 0 && (
                            <span style={{ color: '#b67611', marginLeft: '6px' }}>
                              · AI 待确认 {(tables.get(t.typeKey) ?? []).filter(x => x.aiPrelabeled && !x.annotated).length}
                            </span>
                          )}
                        </span>
                        <button
                          type="button"
                          className="btn btn-outline"
                          onClick={() => void prelabelAll(t.typeKey)}
                          disabled={prelabelingAll === t.typeKey}
                          style={{ fontSize: '11px', padding: '3px 10px' }}
                        >
                          {prelabelingAll === t.typeKey ? <Loader2 className="spin" size={12} /> : <Sparkles size={12} />}
                          AI 一键预标注（跳过已标注）
                        </button>
                      </div>
                    )}
                    {tablesLoading === t.typeKey ? (
                      <div style={{ textAlign: 'center', color: '#888' }}><Loader2 className="spin" size={14} /> 加载表格…</div>
                    ) : (tables.get(t.typeKey) ?? []).length === 0 ? (
                      <p className="quiet" style={{ margin: 0, fontSize: '12px' }}>
                        暂无结构化表格。POI 在解析 docx 时只识别真正的 &lt;table&gt; 元素；如果 docx 里是用文字排版的"伪表格"则不会被收录。
                      </p>
                    ) : (
                      (tables.get(t.typeKey) ?? []).map(tbl => (
                        <StandardTableRow
                          key={tbl.id}
                          table={tbl}
                          onOpen={setDrawerTable}
                        />
                      ))
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {uploadModal && (
        <UploadModal
          file={uploadModal.file}
          existingKeys={types.map(t => t.typeKey)}
          uploading={uploadingKey !== null}
          onCancel={() => setUploadModal(null)}
          onSubmit={submitUpload}
        />
      )}

      {drawerTable && (
        <AnnotationDrawer
          table={drawerTable}
          onUpdate={fresh => {
            setDrawerTable(fresh);
            setTables(prev => {
              const m = new Map(prev);
              const list = (m.get(fresh.projectType) ?? []).map(x => x.id === fresh.id ? fresh : x);
              m.set(fresh.projectType, list);
              return m;
            });
          }}
          onError={setError}
          onNotice={setNotice}
          onClose={() => setDrawerTable(null)}
          onNeedKey={() => setConfirmKeyOpen(true)}
        />
      )}

      <ConfirmModal
        open={confirmKeyOpen}
        title="需要配置 AI Key"
        message="AI 预标注需要先配置 DeepSeek API Key。是否现在去配置？"
        confirmText="去配置"
        onConfirm={() => { setConfirmKeyOpen(false); setKeyModalOpen(true); }}
        onCancel={() => setConfirmKeyOpen(false)}
      />

      <SettingsModal open={keyModalOpen} onClose={() => setKeyModalOpen(false)} />
    </div>
  );
}

function UploadModal({ file, existingKeys, uploading, onCancel, onSubmit }: {
  file: File;
  existingKeys: string[];
  uploading: boolean;
  onCancel: () => void;
  onSubmit: (typeKey: string, typeLabel: string, replace: boolean) => Promise<void>;
}) {
  // 从文件名猜默认值：去掉序号前缀和扩展名
  const defaultLabel = file.name.replace(/\.docx$/i, '').replace(/^[0-9]+[\s.、]+/, '').trim();
  const defaultKey = pinyinKey(defaultLabel) || `type-${Date.now()}`;
  const [typeKey, setTypeKey] = useState(defaultKey);
  const [typeLabel, setTypeLabel] = useState(defaultLabel);
  const [replace, setReplace] = useState(true);

  const exists = existingKeys.includes(typeKey);

  return (
    <div
      style={{ position: 'fixed', inset: 0, background: 'rgba(20,32,51,0.45)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      onClick={() => !uploading && onCancel()}
    >
      <div
        style={{ background: '#fff', borderRadius: '12px', padding: '20px 24px', width: 'min(520px, calc(100vw - 32px))', boxShadow: '0 18px 48px rgba(20, 32, 51, 0.2)' }}
        onClick={e => e.stopPropagation()}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
          <strong style={{ fontSize: '15px' }}>导入用地标准</strong>
          <button type="button" onClick={onCancel} disabled={uploading} style={{ background: 'none', border: 'none', cursor: 'pointer' }}><X size={18} /></button>
        </div>

        <div style={{ fontSize: '12px', color: '#666', marginBottom: '12px', padding: '8px 10px', background: '#f5f8fc', borderRadius: '6px' }}>
          📄 {file.name} <span style={{ color: '#888' }}>· {(file.size / 1024).toFixed(1)} KB</span>
        </div>

        <label style={{ display: 'block', marginBottom: '10px' }}>
          <span style={{ fontSize: '12px', color: '#666', display: 'block', marginBottom: '4px' }}>项目类型 key（英文小写、连字符，例如 <code>highway</code>）</span>
          <input
            value={typeKey}
            onChange={e => setTypeKey(e.target.value.trim().toLowerCase().replace(/[^a-z0-9-]/g, ''))}
            className="config-input"
            style={{ width: '100%' }}
            disabled={uploading}
          />
        </label>

        <label style={{ display: 'block', marginBottom: '10px' }}>
          <span style={{ fontSize: '12px', color: '#666', display: 'block', marginBottom: '4px' }}>显示名称（中文）</span>
          <input
            value={typeLabel}
            onChange={e => setTypeLabel(e.target.value)}
            className="config-input"
            style={{ width: '100%' }}
            disabled={uploading}
          />
        </label>

        {exists && (
          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: '#b67611', marginBottom: '10px' }}>
            <input type="checkbox" checked={replace} onChange={e => setReplace(e.target.checked)} disabled={uploading} />
            <span>该 key 已存在 — 勾选则清空旧条目后重新导入；不勾选则追加</span>
          </label>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
          <button className="btn btn-outline" type="button" onClick={onCancel} disabled={uploading}>取消</button>
          <button
            className="btn btn-primary"
            type="button"
            disabled={uploading || !typeKey || !typeLabel}
            onClick={() => void onSubmit(typeKey, typeLabel, replace)}
          >
            {uploading ? <Loader2 className="spin" size={14} /> : <Upload size={14} />}
            {uploading ? '导入中…' : '开始导入'}
          </button>
        </div>
      </div>
    </div>
  );
}

// 极简的"中文标签 → 拼音 key"映射，仅覆盖几类常见名称；其他场景让用户手填
function pinyinKey(label: string): string {
  const map: Array<[RegExp, string]> = [
    [/风电/, 'wind-power'],
    [/火电|核电|变电|换流/, 'power-station'],
    [/石油|天然气|油气/, 'oil-gas'],
    [/煤炭/, 'coal'],
    [/铁路/, 'railway'],
    [/公路/, 'highway'],
    [/机场|航空/, 'airport'],
    [/图书馆|文化馆|体育/, 'public-facility'],
  ];
  for (const [re, key] of map) {
    if (re.test(label)) return key;
  }
  return '';
}
