import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useRef } from 'react';
import {
  AlertTriangle, CheckCircle2, Loader2, RefreshCw, Eye,
  Undo2, ArrowRightLeft, FileText, Inbox, ChevronDown, ChevronUp, Bot, Paperclip, Upload, Sparkles,
  Layers, ShieldCheck, XCircle
} from 'lucide-react';
import {
  v2GetProject, v2GetWorkspace, v2RefreshVerdicts,
  v2AssignStep, v2RetractFile, v2InitiatePreview, v2GetFileAnalysis,
  v2GetStepFields, v2UpdateStepField,
  v2ListSituations, v2UpsertSituation, v2AutoDetectSituations,
  v2UploadFiles, v2AnalysisStatus,
  v2GetFunctionalZoneVerdict,
  sourceFileUrl
} from '../api';
import type { ProjectRecord, ProjectFile, VerdictResult, WorkspaceV2, FileAnalysisDetail, StepFieldDto, AiConfig, ZoneVerdictDto } from '../types';
import NavBar from './NavBar';
import { canEdit } from '../auth';
import {
  MATERIAL_SPECS, matchesMaterialV2, parseExtractedFields,
  CASE_GROUPS, flattenAllGroups, isMaterialSkipped, isMaterialRequired,
  type MaterialSpec, type CaseGroup, type SituationMap
} from '../materialSpecs';

const STEP_NAMES = ['', '项目基本情况', '申请用地现状', '农用地转用', '补充耕地', '土地征收', '土地利用', '地灾压矿', '信访违法'];

const AI_CONFIG_KEY = 'policy-report-demo-ai-config';

function readAiConfig(): AiConfig {
  const fallback: AiConfig = { provider: 'deepseek', deepseekApiKey: '', deepseekModel: 'deepseek-chat', doubaoApiKey: '', doubaoEndpoint: '' };
  try { return { ...fallback, ...JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || '{}') }; } catch { return fallback; }
}

type VerdictItem = {
  materialName: string;
  matchedFile: string | null;
  matchedFileId?: string | null;
  detail: string;
};

function parseItems(json: string | null): VerdictItem[] {
  try { return JSON.parse(json ?? '[]') || []; } catch { return []; }
}

function VerdictItemPill({ item, kind }: { item: VerdictItem; kind: 'pass' | 'warn' | 'fail' }) {
  const sym = kind === 'pass' ? '✓' : kind === 'warn' ? '⚠' : '✕';
  const cls = `verdict-item verdict-item-${kind}`;
  const fileId = item.matchedFileId;
  const baseLabel = (
    <>
      {sym} {item.materialName}
      {item.matchedFile && <span style={{ marginLeft: '4px', opacity: 0.7, fontSize: '11px' }}>· {item.matchedFile}</span>}
    </>
  );
  if (fileId) {
    return (
      <button
        type="button"
        className={cls}
        onClick={() => window.open(sourceFileUrl(fileId), '_blank')}
        title={`点击打开证据文件：${item.matchedFile ?? ''}`}
        style={{ cursor: 'pointer', border: 'none', background: 'inherit', font: 'inherit' }}
      >
        {baseLabel}
      </button>
    );
  }
  return <span className={cls} title={item.detail}>{baseLabel}</span>;
}

function VerdictCard({ verdict }: { verdict: VerdictResult | undefined }) {
  if (!verdict) return <div className="verdict-card verdict-none">暂无审查结论，点击"刷新审查"生成</div>;
  const passItems = parseItems(verdict.passItemsJson);
  const warnItems = parseItems(verdict.warnItemsJson);
  const failItems = parseItems(verdict.failItemsJson);
  const cls = verdict.verdict === 'PASS' ? 'verdict-pass' : verdict.verdict === 'FAIL' ? 'verdict-fail' : 'verdict-warn';
  const label = verdict.verdict === 'PASS' ? '✅ 通过' : verdict.verdict === 'FAIL' ? '❌ 不通过' : '⚠️ 需关注';
  return (
    <div className={`verdict-card ${cls}`}>
      <div className="verdict-label">{label}</div>
      {passItems.length > 0 && (
        <div className="verdict-items">
          {passItems.map((item, i) => <VerdictItemPill key={`p${i}`} item={item} kind="pass" />)}
        </div>
      )}
      {warnItems.length > 0 && (
        <div className="verdict-items">
          {warnItems.map((item, i) => <VerdictItemPill key={`w${i}`} item={item} kind="warn" />)}
        </div>
      )}
      {failItems.length > 0 && (
        <div className="verdict-items">
          {failItems.map((item, i) => <VerdictItemPill key={`f${i}`} item={item} kind="fail" />)}
        </div>
      )}
      {verdict.generatedAt && (
        <div className="verdict-time">生成于 {verdict.generatedAt.replace('T', ' ').slice(0, 16)}</div>
      )}
    </div>
  );
}

function MaterialChecklist({ stepNo, stepFiles, fileDetails, situations, allGroups, onQuickUpload, uploadingSpec, readOnly }: {
  stepNo: number;
  stepFiles: ProjectFile[];
  fileDetails: Map<string, FileAnalysisDetail>;
  situations: SituationMap;
  allGroups: CaseGroup[];
  onQuickUpload: (specId: string, files: File[]) => void;
  uploadingSpec: string | null;
  readOnly: boolean;
}) {
  const specs = MATERIAL_SPECS[stepNo] ?? [];
  if (specs.length === 0) return null;

  const matchedMap = new Map<string, ProjectFile[]>();
  for (const spec of specs) {
    const matched = stepFiles.filter((f) => matchesMaterialV2(f, fileDetails.get(f.id) ?? null, spec));
    matchedMap.set(spec.id, matched);
  }

  // 三态：required / skipped / optional（通过 spec.required 与 condition+situations 共同决定）
  const required = specs.filter(s => isMaterialRequired(s, situations, allGroups));
  const skipped = specs.filter(s => isMaterialSkipped(s, situations, allGroups));
  const optional = specs.filter(s => !required.includes(s) && !skipped.includes(s));
  const requiredUploaded = required.filter((s) => (matchedMap.get(s.id)?.length ?? 0) > 0);
  const requiredMissing = required.filter((s) => (matchedMap.get(s.id)?.length ?? 0) === 0);

  return (
    <div className="material-group" style={{ marginBottom: '14px' }}>
      <div className="material-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        材料清单
        <span style={{ fontSize: '12px', color: '#666', fontWeight: 'normal' }}>
          必传 {requiredUploaded.length}/{required.length}
          {requiredMissing.length > 0 && <span style={{ color: '#bb4b5b', marginLeft: '8px' }}>· 缺 {requiredMissing.length}</span>}
          {skipped.length > 0 && <span style={{ color: '#888', marginLeft: '8px' }}>· 跳过 {skipped.length}</span>}
        </span>
      </div>
      {required.map((s) => (
        <SpecRow
          key={`req-${s.id}`}
          spec={s}
          matched={matchedMap.get(s.id) ?? []}
          status="required"
          onQuickUpload={onQuickUpload}
          uploading={uploadingSpec === s.id}
          readOnly={readOnly}
        />
      ))}
      {optional.length > 0 && (
        <>
          <div className="material-title" style={{ marginTop: '10px', fontSize: '12px', color: '#888' }}>选传材料</div>
          {optional.map((s) => (
            <SpecRow
              key={`opt-${s.id}`}
              spec={s}
              matched={matchedMap.get(s.id) ?? []}
              status="optional"
              onQuickUpload={onQuickUpload}
              uploading={uploadingSpec === s.id}
              readOnly={readOnly}
            />
          ))}
        </>
      )}
      {skipped.length > 0 && (
        <>
          <div className="material-title" style={{ marginTop: '10px', fontSize: '12px', color: '#888' }}>当前情形无需上传</div>
          {skipped.map((s) => (
            <SpecRow
              key={`skip-${s.id}`}
              spec={s}
              matched={[]}
              status="skipped"
              onQuickUpload={onQuickUpload}
              uploading={false}
              readOnly={readOnly}
            />
          ))}
        </>
      )}
    </div>
  );
}

function SpecRow({ spec, matched, status, onQuickUpload, uploading, readOnly }: {
  spec: MaterialSpec;
  matched: ProjectFile[];
  status: 'required' | 'optional' | 'skipped';
  onQuickUpload: (specId: string, files: File[]) => void;
  uploading: boolean;
  readOnly: boolean;
}) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const uploaded = matched.length > 0;
  const statusClass = uploaded ? 'uploaded' : status === 'skipped' ? 'skipped' : status === 'required' ? 'missing' : 'optional';
  const badgeClass = uploaded ? 'badge-uploaded' : status === 'skipped' ? 'badge-skipped' : status === 'required' ? 'badge-required' : 'badge-optional';
  const badgeText = uploaded ? '已传' : status === 'skipped' ? '无需上传' : status === 'required' ? '必传' : '选传';
  const showUpload = !readOnly && !uploaded && status !== 'skipped';

  return (
    <div className={`upload-item ${statusClass}`}>
      <div className="item-header">
        <div className="item-title-area">
          <span className="item-icon">📄</span>
          <div>
            <span className="item-label">{spec.label}</span>
            <div className="item-sub">{spec.sub}</div>
          </div>
        </div>
        <span className={badgeClass}>{badgeText}</span>
      </div>
      <div className="file-info">
        {uploaded ? (
          matched.map((f) => (
            <span className="source-file-actions" key={f.id}>
              <button className="source-link" type="button" onClick={() => window.open(sourceFileUrl(f.id), '_blank')}>
                {f.originalName}
              </button>
            </span>
          ))
        ) : (
          <span className="upload-hint">
            {status === 'skipped' ? '当前情形无需上传' : status === 'required' ? '尚未识别到该必传材料' : '项目涉及时上传'}
          </span>
        )}
        {showUpload && (
          <>
            <button
              className="modify-btn"
              type="button"
              onClick={() => inputRef.current?.click()}
              disabled={uploading}
              style={{ marginLeft: 'auto' }}
              title="选择文件上传到本步骤"
            >
              {uploading ? <Loader2 className="spin" size={12} /> : <Upload size={12} />}
              {uploading ? '上传中…' : status === 'required' ? '补传' : '上传'}
            </button>
            <input
              ref={inputRef}
              type="file"
              multiple
              accept=".pdf,.doc,.docx,.txt,.md,.png,.jpg,.jpeg"
              style={{ display: 'none' }}
              onChange={e => {
                const files = Array.from(e.target.files ?? []);
                if (files.length) onQuickUpload(spec.id, files);
                e.currentTarget.value = '';
              }}
            />
          </>
        )}
      </div>
    </div>
  );
}

function ZoneVerdictBadge({ verdict, deltaPct }: { verdict: string; deltaPct: number | null }) {
  const styleMap: Record<string, { bg: string; color: string; icon: JSX.Element; label: string }> = {
    PASS:    { bg: '#dcfce7', color: '#16a34a', icon: <ShieldCheck size={11} />, label: '通过' },
    FAIL:    { bg: '#fee2e2', color: '#dc2626', icon: <XCircle size={11} />,     label: '不通过' },
    WARN:    { bg: '#fef3c7', color: '#b67611', icon: <AlertTriangle size={11}/>, label: '注意' },
    UNKNOWN: { bg: '#f3f4f6', color: '#5e6c80', icon: <Eye size={11} />,         label: '未核对' },
  };
  const s = styleMap[verdict] ?? styleMap.UNKNOWN;
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: '3px',
      padding: '1px 7px', borderRadius: '999px',
      background: s.bg, color: s.color, fontSize: '10px', fontWeight: 600,
    }}>
      {s.icon}{s.label}
      {deltaPct != null && verdict === 'FAIL' && (
        <span style={{ marginLeft: '3px' }}>{deltaPct > 0 ? '+' : ''}{deltaPct}%</span>
      )}
    </span>
  );
}

function ZoneVerdictPanel({ data, loading, onRefresh }: {
  data: ZoneVerdictDto[] | null;
  loading: boolean;
  onRefresh: () => void;
}) {
  if (loading && !data) {
    return <div style={{ padding: '20px', textAlign: 'center', color: '#888', fontSize: '13px' }}>
      <Loader2 className="spin" size={16} /> 正在按功能区查表比对…
    </div>;
  }
  if (!data || data.length === 0) {
    return <div style={{ padding: '16px', fontSize: '12px', color: '#888' }}>
      暂无可分析数据。需要：1）项目类型对应的标准表已标注功能区；2）项目已上传材料且 AI 抽出带功能区标签的字段。
    </div>;
  }
  let totalPass = 0, totalFail = 0, totalWarn = 0, totalUnknown = 0;
  for (const z of data) for (const t of z.tables) for (const i of t.indicators) {
    if (i.verdict === 'PASS') totalPass++;
    else if (i.verdict === 'FAIL') totalFail++;
    else if (i.verdict === 'WARN') totalWarn++;
    else totalUnknown++;
  }
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px', flexWrap: 'wrap', gap: '6px' }}>
        <div style={{ fontSize: '13px', color: '#444' }}>
          <strong>{data.length}</strong> 个功能区，
          ✅ <strong style={{ color: '#16a34a' }}>{totalPass}</strong> 通过 ·
          ❌ <strong style={{ color: '#dc2626' }}>{totalFail}</strong> 不通过 ·
          ⚠️ <strong style={{ color: '#b67611' }}>{totalWarn}</strong> 注意 ·
          <span style={{ color: '#5e6c80' }}> {totalUnknown}</span> 未核对
        </div>
        <button type="button" className="btn btn-outline" onClick={onRefresh} disabled={loading} style={{ fontSize: '12px', padding: '3px 10px' }}>
          {loading ? <Loader2 className="spin" size={12} /> : <RefreshCw size={12} />} 重新分析
        </button>
      </div>

      {data.map(zone => (
        <div key={zone.functionalZone} style={{
          marginBottom: '12px', padding: '10px 12px',
          background: '#fff', border: '1px solid #e5e7eb', borderRadius: '8px',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
            <Layers size={14} style={{ color: '#3b82f6' }} />
            <strong style={{ fontSize: '13px' }}>{zone.functionalZone}</strong>
            <span style={{ fontSize: '11px', color: '#888' }}>
              · 命中 {zone.matchedTables}/{zone.totalAnnotatedTables} 张标准表
              · 项目相关字段 {zone.projectFieldCount} 个
            </span>
          </div>

          {zone.tables.length === 0 && (
            <p className="quiet" style={{ margin: 0, fontSize: '12px', paddingLeft: '22px' }}>
              {zone.totalAnnotatedTables === 0
                ? '该功能区暂无已标注的标准表；请在「用地标准管理」标注或调用 AI 预标注。'
                : '项目字段未提供查询键，未能命中表行（如缺少机组容量/地形类型）。'}
            </p>
          )}

          {zone.tables.map(tbl => (
            <div key={tbl.tableId} style={{
              marginTop: '8px', padding: '8px 10px',
              background: '#fafbfc', border: '1px solid #eef0f2', borderRadius: '6px',
            }}>
              <div style={{ fontSize: '12px', marginBottom: '6px' }}>
                <span style={{ color: '#0d8a72', fontWeight: 600 }}>{tbl.tableCode}</span>
                <span style={{ color: '#444', marginLeft: '6px' }}>{(tbl.tableTitle || '').replace(tbl.tableCode || '', '').trim()}</span>
              </div>
              {Object.keys(tbl.matchedQueryKeys).length > 0 && (
                <div style={{ fontSize: '11px', color: '#888', marginBottom: '6px' }}>
                  查询键：{Object.entries(tbl.matchedQueryKeys).map(([k, v]) => `${k}=${v}`).join('，')}
                  {tbl.matchedRowIndex < 0 && <span style={{ color: '#bb4b5b', marginLeft: '4px' }}>（未命中行）</span>}
                </div>
              )}
              {tbl.indicators.length === 0 ? (
                <p className="quiet" style={{ margin: 0, fontSize: '11px' }}>该表无标注的标准值列</p>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ fontSize: '11px', borderCollapse: 'collapse', width: '100%' }}>
                    <thead style={{ background: '#f5f8fc' }}>
                      <tr>
                        <th style={{ border: '1px solid #e5e7eb', padding: '4px 8px', textAlign: 'left' }}>指标</th>
                        <th style={{ border: '1px solid #e5e7eb', padding: '4px 8px', textAlign: 'left' }}>标准值</th>
                        <th style={{ border: '1px solid #e5e7eb', padding: '4px 8px', textAlign: 'left' }}>项目实际</th>
                        <th style={{ border: '1px solid #e5e7eb', padding: '4px 8px', textAlign: 'left' }}>判定</th>
                        <th style={{ border: '1px solid #e5e7eb', padding: '4px 8px', textAlign: 'left' }}>备注</th>
                      </tr>
                    </thead>
                    <tbody>
                      {tbl.indicators.map((ind, i) => (
                        <tr key={i}>
                          <td style={{ border: '1px solid #e5e7eb', padding: '3px 8px' }}>{ind.indicatorName}</td>
                          <td style={{ border: '1px solid #e5e7eb', padding: '3px 8px' }}>
                            {ind.standardValue || <span className="quiet">—</span>}
                            <span style={{ color: '#aaa', marginLeft: '4px', fontSize: '10px' }}>
                              ({ind.semantic === 'upper_bound' ? '上限' : ind.semantic === 'lower_bound' ? '下限' : '精确'})
                            </span>
                          </td>
                          <td style={{ border: '1px solid #e5e7eb', padding: '3px 8px' }}>
                            {ind.actualValue ? (
                              ind.sourceFileId ? (
                                <button type="button" onClick={() => window.open(sourceFileUrl(ind.sourceFileId!), '_blank')} style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', color: '#3b82f6', font: 'inherit', textDecoration: 'underline' }}>
                                  {ind.actualValue}
                                </button>
                              ) : ind.actualValue
                            ) : <span className="quiet">未申报</span>}
                          </td>
                          <td style={{ border: '1px solid #e5e7eb', padding: '3px 8px' }}>
                            <ZoneVerdictBadge verdict={ind.verdict} deltaPct={ind.deltaPct} />
                          </td>
                          <td style={{ border: '1px solid #e5e7eb', padding: '3px 8px', color: '#666' }}>{ind.note}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

function CaseSelectors({ stepNo, situations, readOnly, onSelect, onAutoDetect, autoDetecting }: {
  stepNo: number;
  situations: SituationMap;
  readOnly: boolean;
  onSelect: (groupId: string, value: string) => void;
  onAutoDetect: () => void;
  autoDetecting: boolean;
}) {
  const groups = CASE_GROUPS[stepNo] ?? [];
  if (groups.length === 0) return null;
  // 是否本步至少有一项已选过（区分用户选过的 vs 默认 fallback）
  const userSelectedCount = groups.filter(g => situations[g.id] !== undefined).length;
  return (
    <div style={{ marginBottom: '14px', padding: '12px', background: '#fafbfc', border: '1px solid #e5e7eb', borderRadius: '8px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
        <div style={{ fontSize: '13px', color: '#666', fontWeight: 600 }}>
          项目情形选择
          <span style={{ fontSize: '11px', color: '#888', fontWeight: 'normal', marginLeft: '8px' }}>
            已识别 {userSelectedCount}/{groups.length}
          </span>
        </div>
        {!readOnly && (
          <button
            type="button"
            className="btn btn-outline"
            style={{ fontSize: '12px', padding: '4px 10px' }}
            onClick={onAutoDetect}
            disabled={autoDetecting}
            title="基于已上传材料让 DeepSeek 推断每组应选哪个选项"
          >
            {autoDetecting ? <Loader2 className="spin" size={12} /> : <Sparkles size={12} />}
            {autoDetecting ? 'AI 识别中…' : 'AI 自动识别'}
          </button>
        )}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '10px' }}>
        {groups.map(g => {
          const userSelected = situations[g.id];
          const selected = userSelected ?? g.options[0]?.value ?? '';
          return (
            <div key={g.id}>
              <div style={{ fontSize: '12px', color: '#444', marginBottom: '4px', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '4px' }}>
                {g.title}
                {!userSelected && <span style={{ fontSize: '10px', color: '#bbb', fontWeight: 'normal' }}>（默认）</span>}
              </div>
              <select
                value={selected}
                onChange={e => onSelect(g.id, e.target.value)}
                disabled={readOnly}
                style={{
                  width: '100%', padding: '4px 6px', fontSize: '12px',
                  border: userSelected ? '1px solid #0d8a72' : '1px solid #d1d5db',
                  borderRadius: '4px',
                }}
              >
                {g.options.map(o => (
                  <option key={o.value} value={o.value}>情形{o.value}：{o.label}</option>
                ))}
              </select>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function FieldsTable({ fields, readOnly, onSave }: {
  fields: StepFieldDto[];
  readOnly: boolean;
  onSave: (key: string, value: string) => Promise<void>;
}) {
  if (fields.length === 0) {
    return <p className="quiet" style={{ marginTop: '12px' }}>本步骤暂未抽取到字段。文件解析完成或人工补录后会出现在这里。</p>;
  }
  return (
    <div style={{ marginTop: '12px', overflowX: 'auto' }}>
      <table className="data-table">
        <thead>
          <tr>
            <th>字段名称</th>
            <th>解析值</th>
            <th>来源</th>
            {!readOnly && <th>操作</th>}
          </tr>
        </thead>
        <tbody>
          {fields.map(f => (
            <FieldRow key={f.key} field={f} readOnly={readOnly} onSave={onSave} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function FieldRow({ field, readOnly, onSave }: {
  field: StepFieldDto;
  readOnly: boolean;
  onSave: (key: string, value: string) => Promise<void>;
}) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(field.value);
  const [saving, setSaving] = useState(false);

  useEffect(() => { setValue(field.value); }, [field.value]);

  async function commit() {
    if (value === field.value) { setEditing(false); return; }
    setSaving(true);
    try {
      await onSave(field.key, value);
      setEditing(false);
    } finally {
      setSaving(false);
    }
  }

  const rowClass = field.override ? 'highlight-warn' : '';
  return (
    <tr className={rowClass}>
      <td><strong>{field.label || field.key}</strong>{field.override && <span className="required-dot" style={{ background: '#b67611' }} title="人工修改">改</span>}</td>
      <td>
        {editing ? (
          <input className="table-input" value={value} onChange={e => setValue(e.target.value)} autoFocus />
        ) : (
          <span>{field.value || <span className="quiet">待补</span>}</span>
        )}
      </td>
      <td>
        {field.sourceFileId ? (
          <button className="field-source" type="button" onClick={() => window.open(sourceFileUrl(field.sourceFileId!), '_blank')}>
            <Paperclip size={12} /> {field.source || '原文'}
          </button>
        ) : (
          <span className="quiet">{field.source || '-'}</span>
        )}
      </td>
      {!readOnly && (
        <td>
          <button className="modify-btn" type="button" onClick={editing ? commit : () => setEditing(true)} disabled={saving}>
            {saving ? <Loader2 className="spin" size={12} /> : null}
            {editing ? '保存' : '修改'}
          </button>
        </td>
      )}
    </tr>
  );
}

function FileDrawer({ detail, loading }: { detail: FileAnalysisDetail | undefined; loading: boolean }) {
  if (loading) {
    return (
      <div className="parsed-card" style={{ marginTop: '8px' }}>
        <Loader2 className="spin" size={16} /> <span style={{ marginLeft: '6px', fontSize: '13px', color: '#888' }}>加载分析详情…</span>
      </div>
    );
  }
  if (!detail) {
    return (
      <div className="parsed-card" style={{ marginTop: '8px', color: '#888', fontSize: '13px' }}>
        分析尚未完成或无详情。
      </div>
    );
  }
  const fields = parseExtractedFields(detail.extractedFieldsJson);
  return (
    <div className="parsed-card" style={{ marginTop: '8px' }}>
      <div className="parsed-card-head">
        <div>
          <strong style={{ fontSize: '13px' }}>{detail.detectedDocumentType || '未识别类型'}</strong>
          <p style={{ margin: '2px 0 0', fontSize: '12px', color: '#888' }}>
            {detail.ocrUsed ? 'OCR ' : ''}{detail.doubaoUsed ? '· 豆包视觉 ' : ''}
            · 文本 {(detail.extractedText ?? '').length} 字 · 字段 {fields.length} 个
          </p>
        </div>
      </div>
      {fields.length > 0 && (
        <table className="data-table" style={{ marginTop: '10px', fontSize: '13px' }}>
          <thead>
            <tr><th>字段</th><th>解析值</th><th>来源</th></tr>
          </thead>
          <tbody>
            {fields.map((f, i) => (
              <tr key={i}>
                <td><strong>{f.label ?? f.key ?? '-'}</strong></td>
                <td>{f.value || <span className="quiet">待补</span>}</td>
                <td>
                  {f.sourceFileId ? (
                    <button className="field-source" type="button" onClick={() => window.open(sourceFileUrl(f.sourceFileId!), '_blank')}>
                      <Paperclip size={12} /> {f.source || '原文'}
                    </button>
                  ) : (
                    <span className="quiet">{f.source || '-'}</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {detail.aiSummary && (
        <p className="ai-summary" style={{ marginTop: '10px' }}>
          <Bot size={14} /> {detail.aiSummary}
        </p>
      )}
      {detail.aiAdvice && (
        <details className="ai-raw" style={{ marginTop: '8px' }}>
          <summary>AI 完整审查建议</summary>
          <pre>{detail.aiAdvice}</pre>
        </details>
      )}
    </div>
  );
}

function FileRow({ file, expanded, onToggle, onRetract, onAssign, drawer, readOnly }: {
  file: ProjectFile;
  expanded: boolean;
  onToggle: () => void;
  onRetract: (fileId: string) => void;
  onAssign: (fileId: string, step: number) => void;
  drawer?: React.ReactNode;
  readOnly?: boolean;
}) {
  const [assigning, setAssigning] = useState<number | ''>('');
  return (
    <div>
      <div className="wb-file-row">
        <button
          type="button"
          onClick={onToggle}
          style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px', display: 'flex', alignItems: 'center' }}
          title={expanded ? '收起详情' : '展开详情'}
        >
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>
        <FileText size={14} style={{ flexShrink: 0, color: '#888' }} />
        <button type="button" className="file-title-link" onClick={onToggle} style={{ background: 'none', border: 'none', padding: 0, textAlign: 'left', cursor: 'pointer' }}>
          <span className="wb-file-name" title={file.originalName}>{file.originalName}</span>
        </button>
        <span className={`badge-${file.analysisStatus === 'DONE' ? 'uploaded' : file.analysisStatus === 'FAILED' ? 'required' : 'optional'}`} style={{ fontSize: '11px', padding: '2px 6px' }}>
          {file.analysisStatus === 'DONE' ? '已分析' : file.analysisStatus === 'FAILED' ? '失败' : '处理中'}
        </span>
        {file.aiSuggestedStep && (
          <span style={{ fontSize: '11px', color: '#888' }}>AI建议→{file.aiSuggestedStep}</span>
        )}
        {!readOnly && (
          <div style={{ display: 'flex', gap: '4px', marginLeft: 'auto', alignItems: 'center' }}>
            <select
              value={assigning}
              onChange={e => setAssigning(Number(e.target.value) as number | '')}
              style={{ fontSize: '12px', padding: '2px 4px', border: '1px solid #d1d5db', borderRadius: '4px' }}
            >
              <option value="">改挂到…</option>
              {[1,2,3,4,5,6,7,8].map(n => (
                <option key={n} value={n}>步骤{n}·{STEP_NAMES[n]}</option>
              ))}
            </select>
            {assigning !== '' && (
              <button className="modify-btn" type="button" onClick={() => { onAssign(file.id, assigning as number); setAssigning(''); }}>
                <ArrowRightLeft size={12} />改挂
              </button>
            )}
            <button className="withdraw-btn" type="button" onClick={() => onRetract(file.id)}>
              <Undo2 size={12} />撤回
            </button>
          </div>
        )}
      </div>
      {expanded && drawer}
    </div>
  );
}

export default function Workbench() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [project, setProject] = useState<ProjectRecord | null>(null);
  const [workspace, setWorkspace] = useState<WorkspaceV2 | null>(null);
  const [activeStep, setActiveStep] = useState(1);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [fileDetails, setFileDetails] = useState<Map<string, FileAnalysisDetail>>(new Map());
  const [detailLoading, setDetailLoading] = useState<Set<string>>(new Set());
  const [openDrawer, setOpenDrawer] = useState<string | null>(null);
  const [stepFields, setStepFields] = useState<Map<number, StepFieldDto[]>>(new Map());
  const [stepFieldsLoading, setStepFieldsLoading] = useState(false);
  const [situations, setSituations] = useState<SituationMap>({});
  const allCaseGroups = useMemo(() => flattenAllGroups(), []);
  const [uploadingSpec, setUploadingSpec] = useState<string | null>(null);
  const [autoDetecting, setAutoDetecting] = useState(false);
  const [zoneVerdict, setZoneVerdict] = useState<ZoneVerdictDto[] | null>(null);
  const [zoneVerdictLoading, setZoneVerdictLoading] = useState(false);
  const [zoneVerdictOpen, setZoneVerdictOpen] = useState(false);

  async function loadZoneVerdict() {
    if (!id) return;
    setZoneVerdictLoading(true);
    setError('');
    try {
      const result = await v2GetFunctionalZoneVerdict(id);
      setZoneVerdict(result);
      setZoneVerdictOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : '功能区合规分析失败');
    } finally {
      setZoneVerdictLoading(false);
    }
  }

  async function autoDetectSituations() {
    if (!id) return;
    const aiConfig = readAiConfig();
    if (!aiConfig.deepseekApiKey?.trim()) {
      setError('请先在 NavBar「AI 配置」中填写 DeepSeek API Key');
      return;
    }
    setAutoDetecting(true);
    setError('');
    setNotice('');
    try {
      const result = await v2AutoDetectSituations(id, aiConfig.deepseekApiKey, aiConfig.deepseekModel || 'deepseek-chat');
      const next: SituationMap = { ...situations };
      for (const s of result.situations) next[s.groupId] = s.value;
      setSituations(next);
      setNotice(result.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 识别失败');
    } finally {
      setAutoDetecting(false);
    }
  }

  async function quickUpload(specId: string, fileList: File[]) {
    if (!id || fileList.length === 0) return;
    setUploadingSpec(specId);
    setError('');
    setNotice('');
    try {
      const aiConfig = readAiConfig();
      await v2UploadFiles(id, fileList, aiConfig);
      setNotice(`已上传 ${fileList.length} 份材料，AI 正在后台解析…`);
      await loadWorkspace();

      // AI 解析完成后自动再刷新一次（轮询 60 秒上限）
      const startedAt = Date.now();
      const handle = window.setInterval(async () => {
        if (Date.now() - startedAt > 60_000) {
          window.clearInterval(handle);
          return;
        }
        try {
          const prog = await v2AnalysisStatus(id);
          if (prog.running === 0 && prog.total > 0) {
            window.clearInterval(handle);
            await loadWorkspace();
            // 同步刷新当前步骤字段
            try {
              const fresh = await v2GetStepFields(id, activeStep);
              setStepFields(prev => new Map(prev).set(activeStep, fresh));
            } catch { /* non-critical */ }
            setNotice('AI 解析完成，材料清单已自动刷新');
          }
        } catch { /* non-critical */ }
      }, 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploadingSpec(null);
    }
  }

  const loadWorkspace = useCallback(async () => {
    if (!id) return;
    const [proj, ws] = await Promise.all([v2GetProject(id), v2GetWorkspace(id)]);
    setProject(proj);
    setWorkspace(ws);
    if (ws.files.length > 0 && ws.verdicts.length === 0) {
      try {
        const verdicts = await v2RefreshVerdicts(id);
        setWorkspace(prev => prev ? { ...prev, verdicts } : { ...ws, verdicts });
      } catch { /* non-critical */ }
    }
  }, [id]);

  useEffect(() => {
    setLoading(true);
    loadWorkspace().catch(err => setError(err instanceof Error ? err.message : '加载失败')).finally(() => setLoading(false));
  }, [loadWorkspace]);

  // 切换步骤或工作台数据更新时，预拉该步骤已分析文件的详情（用于 MaterialChecklist 命中匹配）
  useEffect(() => {
    if (!id || !workspace) return;
    const targets = workspace.files
      .filter(f => f.currentStep === activeStep && f.analysisStatus === 'DONE')
      .filter(f => !fileDetails.has(f.id) && !detailLoading.has(f.id));
    if (targets.length === 0) return;

    setDetailLoading(prev => {
      const next = new Set(prev);
      targets.forEach(f => next.add(f.id));
      return next;
    });

    Promise.all(targets.map(f =>
      v2GetFileAnalysis(id, f.id)
        .then(detail => ({ fileId: f.id, detail, ok: true as const }))
        .catch(() => ({ fileId: f.id, detail: null, ok: false as const }))
    )).then(results => {
      setFileDetails(prev => {
        const next = new Map(prev);
        for (const r of results) {
          if (r.ok && r.detail) next.set(r.fileId, r.detail);
        }
        return next;
      });
      setDetailLoading(prev => {
        const next = new Set(prev);
        targets.forEach(f => next.delete(f.id));
        return next;
      });
    });
  }, [id, workspace, activeStep, fileDetails, detailLoading]);

  // 加载项目情形（一次拉全部）
  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    v2ListSituations(id).then(list => {
      if (cancelled) return;
      const map: SituationMap = {};
      for (const s of list) map[s.groupId] = s.value;
      setSituations(map);
    }).catch(() => { /* 非关键 */ });
    return () => { cancelled = true; };
  }, [id]);

  async function selectSituation(groupId: string, value: string) {
    if (!id) return;
    setSituations(prev => ({ ...prev, [groupId]: value }));
    try {
      await v2UpsertSituation(id, activeStep, groupId, value);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存情形失败');
    }
  }

  // 加载当前步骤字段聚合
  useEffect(() => {
    if (!id || loading) return;
    let cancelled = false;
    setStepFieldsLoading(true);
    v2GetStepFields(id, activeStep)
      .then(fields => {
        if (cancelled) return;
        setStepFields(prev => new Map(prev).set(activeStep, fields));
      })
      .catch(() => { /* 非关键 */ })
      .finally(() => { if (!cancelled) setStepFieldsLoading(false); });
    return () => { cancelled = true; };
  }, [id, activeStep, loading]);

  async function saveField(key: string, value: string) {
    if (!id) return;
    try {
      await v2UpdateStepField(id, activeStep, key, value);
      // 修改后重新拉取字段
      const fresh = await v2GetStepFields(id, activeStep);
      setStepFields(prev => new Map(prev).set(activeStep, fresh));
      setNotice('字段已保存');
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存字段失败');
    }
  }

  async function refreshVerdicts() {
    if (!id) return;
    setRefreshing(true);
    setError('');
    try {
      const verdicts = await v2RefreshVerdicts(id);
      setWorkspace(ws => ws ? { ...ws, verdicts } : ws);
      setNotice('审查结论已刷新');
    } catch (err) {
      setError(err instanceof Error ? err.message : '刷新失败');
    } finally {
      setRefreshing(false);
    }
  }

  async function retract(fileId: string) {
    if (!id) return;
    try {
      await v2RetractFile(id, fileId);
      await loadWorkspace();
    } catch (err) {
      setError(err instanceof Error ? err.message : '撤回失败');
    }
  }

  async function assign(fileId: string, step: number) {
    if (!id) return;
    try {
      await v2AssignStep(id, fileId, step);
      await loadWorkspace();
      setNotice(`已改挂到第 ${step} 步`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '改挂失败');
    }
  }

  async function initiatePreview() {
    if (!id) return;
    setPreviewing(true);
    setError('');
    try {
      await v2InitiatePreview(id);
      navigate(`/projects/${id}/preview`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '生成预览失败');
      setPreviewing(false);
    }
  }

  async function toggleDrawer(fileId: string) {
    if (!id) return;
    if (openDrawer === fileId) {
      setOpenDrawer(null);
      return;
    }
    setOpenDrawer(fileId);
    if (!fileDetails.has(fileId) && !detailLoading.has(fileId)) {
      setDetailLoading(prev => new Set(prev).add(fileId));
      try {
        const detail = await v2GetFileAnalysis(id, fileId);
        setFileDetails(prev => new Map(prev).set(fileId, detail));
      } catch {
        // 抽屉里会渲染"分析尚未完成"
      } finally {
        setDetailLoading(prev => {
          const next = new Set(prev);
          next.delete(fileId);
          return next;
        });
      }
    }
  }

  const pendingFiles = workspace?.files.filter(f => !f.currentStep) ?? [];
  const stepFiles = (n: number) => workspace?.files.filter(f => f.currentStep === n) ?? [];
  const verdictMap = new Map((workspace?.verdicts ?? []).map(v => [v.stepNo, v]));
  const currentStepFiles = useMemo(() => stepFiles(activeStep), [workspace, activeStep]);
  const readOnly = !canEdit();

  return (
    <div className="app-shell">
      <NavBar project={project} crumb="工作台" />
      {(error || notice) && (
        <div className={error ? 'message-strip error-strip' : 'message-strip notice-strip'}>
          {error ? <AlertTriangle size={16} /> : <CheckCircle2 size={16} />}
          {error || notice}
        </div>
      )}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px', color: '#888' }}>
          <Loader2 className="spin" size={28} />
          <div style={{ marginTop: '12px' }}>加载工作台…</div>
        </div>
      ) : (
        <>
          <div style={{ display: 'flex', gap: '8px', padding: '12px 24px', borderBottom: '1px solid #e5e7eb', alignItems: 'center' }}>
            <span style={{ fontSize: '13px', color: '#888' }}>
              全部文件：{workspace?.files.length ?? 0} 份，待分配：{pendingFiles.length} 份
              {project?.projectTypeLabel && (
                <span style={{ marginLeft: '12px', color: '#0d8a72' }}>· 当前标准包：{project.projectTypeLabel}</span>
              )}
            </span>
            <div style={{ marginLeft: 'auto', display: 'flex', gap: '8px' }}>
              {readOnly ? (
                <span style={{ fontSize: '12px', color: '#888' }}>当前以查看者身份登录，按钮已禁用</span>
              ) : (
                <>
                  <button className="btn btn-outline" type="button" onClick={refreshVerdicts} disabled={refreshing}>
                    {refreshing ? <Loader2 className="spin" size={15} /> : <RefreshCw size={15} />}刷新审查
                  </button>
                  <button className="btn btn-teal" type="button" onClick={initiatePreview} disabled={previewing}>
                    {previewing ? <Loader2 className="spin" size={15} /> : <Eye size={15} />}生成预览报告
                  </button>
                </>
              )}
            </div>
          </div>

          <div style={{ padding: '0 24px', marginTop: '12px' }}>
            <button
              type="button"
              className="btn btn-outline"
              onClick={() => zoneVerdictOpen ? setZoneVerdictOpen(false) : loadZoneVerdict()}
              disabled={zoneVerdictLoading}
              style={{ fontSize: '12px' }}
            >
              {zoneVerdictLoading ? <Loader2 className="spin" size={13} /> : <Layers size={13} />}
              {zoneVerdictOpen ? '收起功能区合规分析' : '功能区合规分析'}
            </button>
            {zoneVerdictOpen && (
              <div style={{
                marginTop: '10px', padding: '12px', background: '#f5f8fc',
                border: '1px solid #dde4ee', borderRadius: '10px',
              }}>
                <div style={{ fontSize: '13px', color: '#142033', fontWeight: 600, marginBottom: '10px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Layers size={14} /> 功能区合规分析
                  <span style={{ fontSize: '11px', color: '#888', fontWeight: 'normal' }}>
                    （基于已标注的标准表 + 项目抽取字段，逐项核对）
                  </span>
                </div>
                <ZoneVerdictPanel data={zoneVerdict} loading={zoneVerdictLoading} onRefresh={loadZoneVerdict} />
              </div>
            )}
          </div>

          {pendingFiles.length > 0 && (
            <div className="pending-zone">
              <div className="pending-zone-title"><Inbox size={16} />待分配材料（{pendingFiles.length} 份）</div>
              {pendingFiles.map(f => (
                <FileRow
                  key={f.id}
                  file={f}
                  expanded={openDrawer === f.id}
                  onToggle={() => toggleDrawer(f.id)}
                  onRetract={retract}
                  onAssign={assign}
                  drawer={<FileDrawer detail={fileDetails.get(f.id)} loading={detailLoading.has(f.id)} />}
                  readOnly={readOnly}
                />
              ))}
            </div>
          )}

          <div className="wb-body">
            <nav className="step-tabs">
              {[1,2,3,4,5,6,7,8].map(n => {
                const v = verdictMap.get(n);
                const cls = v ? (v.verdict === 'PASS' ? 'tab-pass' : v.verdict === 'FAIL' ? 'tab-fail' : 'tab-warn') : '';
                return (
                  <button
                    key={n}
                    type="button"
                    className={`step-tab ${activeStep === n ? 'active' : ''} ${cls}`}
                    onClick={() => setActiveStep(n)}
                  >
                    <span className="num">{n}</span>
                    <span>{STEP_NAMES[n]}</span>
                    {stepFiles(n).length > 0 && <span className="tab-count">{stepFiles(n).length}</span>}
                  </button>
                );
              })}

              {!readOnly && (
                <div className="step-tabs-footer">
                  <button className="btn btn-teal" style={{ width: '100%', justifyContent: 'center' }} type="button" onClick={initiatePreview} disabled={previewing}>
                    {previewing ? <Loader2 className="spin" size={15} /> : <Eye size={15} />}
                    生成预览报告
                  </button>
                </div>
              )}
            </nav>

            <div className="wb-step-panel">
              <div className="wb-step-header">
                <h3>第 {activeStep} 步 · {STEP_NAMES[activeStep]}</h3>
                <span style={{ fontSize: '13px', color: '#888' }}>{currentStepFiles.length} 份材料</span>
              </div>

              <CaseSelectors
                stepNo={activeStep}
                situations={situations}
                readOnly={readOnly}
                onSelect={selectSituation}
                onAutoDetect={autoDetectSituations}
                autoDetecting={autoDetecting}
              />

              <MaterialChecklist
                stepNo={activeStep}
                stepFiles={currentStepFiles}
                fileDetails={fileDetails}
                situations={situations}
                allGroups={allCaseGroups}
                onQuickUpload={quickUpload}
                uploadingSpec={uploadingSpec}
                readOnly={readOnly}
              />

              <VerdictCard verdict={verdictMap.get(activeStep)} />

              <div style={{ marginTop: '16px' }}>
                <div style={{ fontSize: '13px', color: '#666', fontWeight: 600, marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  字段抽取
                  {stepFieldsLoading && <Loader2 className="spin" size={12} />}
                </div>
                <FieldsTable
                  fields={stepFields.get(activeStep) ?? []}
                  readOnly={readOnly}
                  onSave={saveField}
                />
              </div>

              <div style={{ marginTop: '16px' }}>
                {currentStepFiles.length === 0 ? (
                  <>
                    <div style={{ fontSize: '13px', color: '#666', fontWeight: 600, marginBottom: '8px' }}>本步已归档文件</div>
                    <p className="quiet">本步骤暂无已归档材料。可从上方"待分配材料"区域改挂。</p>
                  </>
                ) : (
                  <>
                    {(() => {
                      const aiAuto = currentStepFiles.filter(f => !f.confirmedByUser);
                      const userAssigned = currentStepFiles.filter(f => f.confirmedByUser);
                      return (
                        <>
                          {aiAuto.length > 0 && (
                            <>
                              <div style={{ fontSize: '13px', color: '#666', fontWeight: 600, marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                                AI 已归类
                                <span style={{ fontSize: '11px', color: '#888', fontWeight: 'normal' }}>（{aiAuto.length} 份，AI 自动分配未经手动调整）</span>
                              </div>
                              {aiAuto.map(f => (
                                <FileRow
                                  key={f.id}
                                  file={f}
                                  expanded={openDrawer === f.id}
                                  onToggle={() => toggleDrawer(f.id)}
                                  onRetract={retract}
                                  onAssign={assign}
                                  drawer={<FileDrawer detail={fileDetails.get(f.id)} loading={detailLoading.has(f.id)} />}
                                  readOnly={readOnly}
                                />
                              ))}
                            </>
                          )}
                          {userAssigned.length > 0 && (
                            <>
                              <div style={{ fontSize: '13px', color: '#666', fontWeight: 600, margin: '14px 0 8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                                用户手动放置
                                <span style={{ fontSize: '11px', color: '#888', fontWeight: 'normal' }}>（{userAssigned.length} 份，已人工确认）</span>
                              </div>
                              {userAssigned.map(f => (
                                <FileRow
                                  key={f.id}
                                  file={f}
                                  expanded={openDrawer === f.id}
                                  onToggle={() => toggleDrawer(f.id)}
                                  onRetract={retract}
                                  onAssign={assign}
                                  drawer={<FileDrawer detail={fileDetails.get(f.id)} loading={detailLoading.has(f.id)} />}
                                  readOnly={readOnly}
                                />
                              ))}
                            </>
                          )}
                        </>
                      );
                    })()}
                  </>
                )}
              </div>
            </div>
          </div>

          {activeStep === 8 && !readOnly && (
            <div className="wb-finish-bar">
              <span style={{ fontSize: '14px', color: '#555' }}>
                已完成八步审查 — 可生成预览报告，确认后导出正式 Word 文档
              </span>
              <button className="btn btn-primary" style={{ minWidth: '160px', justifyContent: 'center' }} type="button" onClick={initiatePreview} disabled={previewing}>
                {previewing ? <Loader2 className="spin" size={15} /> : <Eye size={15} />}
                生成预览报告 →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
