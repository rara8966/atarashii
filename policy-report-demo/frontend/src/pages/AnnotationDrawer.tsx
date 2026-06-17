import { useState, useEffect } from 'react';
import {
  X, Loader2, Save, RotateCcw, Tag, Sparkles, CheckCircle as CheckIcon,
} from 'lucide-react';
import { saveTableAnnotation, aiPrelabelTable } from '../api';
import type { StandardTableDto, TableAnnotation } from '../types';
import { CASE_GROUPS, zonesOf } from '../materialSpecs';
import { readAiConfig } from '../aiConfig';

type ColState = 'none' | 'key' | 'value';
type Semantic = 'upper_bound' | 'lower_bound' | 'exact';

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

function parseJsonGrid(json: string): string[][] {
  if (!json) return [];
  try {
    const v = JSON.parse(json);
    if (!Array.isArray(v)) return [];
    return v.map(row => Array.isArray(row) ? row.map(c => c == null ? '' : String(c)) : []);
  } catch { return []; }
}

/**
 * 全屏标注抽屉：左栏完整表格（表头列可点击切换 查询键/标准值，长表可滚动看全），
 * 右栏标注配置（列语义、功能区、适用情形、备注）。两栏并排，解决原先卡片内上下叠加 + 多层滚动看不全的问题。
 */
export default function AnnotationDrawer({ table, onUpdate, onError, onNotice, onClose, onNeedKey }: {
  table: StandardTableDto;
  onUpdate: (t: StandardTableDto) => void;
  onError: (msg: string) => void;
  onNotice: (msg: string) => void;
  onClose: () => void;
  /** 未配置 DeepSeek Key 时调用，由父级弹出"去配置"引导弹窗（替代直接报错文字）。 */
  onNeedKey?: () => void;
}) {
  const headers = parseJsonGrid(table.headersJson);
  const rows = parseJsonGrid(table.rowsJson);
  const colCount = headers[0]?.length ?? (rows[0]?.length ?? 0);

  function deriveFromTable(t: StandardTableDto) {
    const ann = parseAnnotation(t.annotationJson);
    const cs: Record<number, ColState> = {};
    const cn: Record<number, string> = {};
    const csem: Record<number, Semantic> = {};
    if (ann) {
      for (const k of ann.queryKeys) { cs[k.col] = 'key'; cn[k.col] = k.name; }
      for (const v of ann.valueCols) { cs[v.col] = 'value'; cn[v.col] = v.name; csem[v.col] = v.semantic ?? 'upper_bound'; }
    }
    return { cs, cn, csem, situations: ann?.applicableSituations ?? [], notes: ann?.notes ?? '', zone: ann?.functionalZone ?? '' };
  }

  const init = deriveFromTable(table);
  const [colState, setColState] = useState<Record<number, ColState>>(init.cs);
  const [colName, setColName] = useState<Record<number, string>>(init.cn);
  const [colSemantic, setColSemantic] = useState<Record<number, Semantic>>(init.csem);
  const [situations, setSituations] = useState<Array<{ stepNo: number; groupId: string; value: string }>>(init.situations);
  const [notes, setNotes] = useState(init.notes);
  const [functionalZone, setFunctionalZone] = useState(init.zone);
  const [saving, setSaving] = useState(false);
  const [prelabeling, setPrelabeling] = useState(false);

  const zoneOptions = zonesOf(table.projectType);

  // Esc 关闭
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  function reload(fresh: StandardTableDto) {
    const d = deriveFromTable(fresh);
    setColState(d.cs); setColName(d.cn); setColSemantic(d.csem);
    setSituations(d.situations); setNotes(d.notes); setFunctionalZone(d.zone);
  }

  function cycleCol(c: number) {
    setColState(prev => {
      const cur = prev[c] ?? 'none';
      const next: ColState = cur === 'none' ? 'key' : cur === 'key' ? 'value' : 'none';
      const updated = { ...prev, [c]: next };
      if (next === 'none') {
        const nn = { ...colName }; delete nn[c]; setColName(nn);
        const ns = { ...colSemantic }; delete ns[c]; setColSemantic(ns);
      } else if (!colName[c] && headers[0]?.[c]) {
        setColName(n => ({ ...n, [c]: headers[0][c] }));
      }
      return updated;
    });
  }

  function buildAnnotation(): TableAnnotation {
    const queryKeys: TableAnnotation['queryKeys'] = [];
    const valueCols: TableAnnotation['valueCols'] = [];
    for (let c = 0; c < colCount; c++) {
      const st = colState[c];
      const name = (colName[c] ?? headers[0]?.[c] ?? `列${c}`).trim();
      if (st === 'key') queryKeys.push({ col: c, name });
      else if (st === 'value') valueCols.push({ col: c, name, semantic: colSemantic[c] ?? 'upper_bound' });
    }
    return { queryKeys, valueCols, applicableSituations: situations, functionalZone, notes, source: 'human' };
  }

  async function save() {
    setSaving(true);
    try {
      const fresh = await saveTableAnnotation(table.id, buildAnnotation());
      onUpdate(fresh);
      reload(fresh);
      onNotice('标注已保存');
      onClose();
    } catch (err) {
      onError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  }

  async function aiPrelabel() {
    const cfg = readAiConfig();
    if (!cfg.deepseekApiKey) { onNeedKey?.(); return; }
    setPrelabeling(true);
    try {
      const fresh = await aiPrelabelTable(table.id, cfg.deepseekApiKey, cfg.deepseekModel || 'deepseek-chat');
      onUpdate(fresh);
      reload(fresh);
      onNotice('AI 已预标注，请人工复核后点击「保存」确认');
    } catch (err) {
      onError(err instanceof Error ? err.message : 'AI 预标注失败');
    } finally {
      setPrelabeling(false);
    }
  }

  function colStyle(c: number): React.CSSProperties {
    const st = colState[c];
    if (st === 'key') return { background: '#dbeafe', borderColor: '#3b82f6' };
    if (st === 'value') return { background: '#dcfce7', borderColor: '#16a34a' };
    return {};
  }

  function toggleSituation(stepNo: number, groupId: string, value: string) {
    setSituations(prev => {
      const idx = prev.findIndex(s => s.groupId === groupId && s.value === value);
      if (idx >= 0) return prev.filter((_, i) => i !== idx);
      const filtered = prev.filter(s => s.groupId !== groupId);
      return [...filtered, { stepNo, groupId, value }];
    });
  }

  const annotatedCols = Array.from({ length: colCount }, (_, c) => c).filter(c => colState[c] === 'key' || colState[c] === 'value');

  return (
    <div className="anno-drawer-overlay" onClick={onClose}>
      <div className="anno-drawer" onClick={e => e.stopPropagation()}>
        {/* 顶部条 */}
        <div className="anno-drawer-head">
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: '14px', fontWeight: 700, color: '#142033', display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
              {table.tableCode && <span style={{ color: '#0d8a72' }}>{table.tableCode}</span>}
              <span>{(table.tableTitle || '').replace(table.tableCode || '', '').trim()}</span>
              {table.annotated && (
                <span style={{ fontSize: '11px', padding: '2px 8px', borderRadius: '999px', background: '#0d8a721a', color: '#0d8a72', display: 'inline-flex', alignItems: 'center', gap: '3px', fontWeight: 600 }}>
                  <CheckIcon size={11} />已标注
                </span>
              )}
            </div>
            {(table.chapter || table.unit) && (
              <div style={{ fontSize: '11px', color: '#888', marginTop: '2px' }}>
                {table.chapter}{table.chapter && table.unit ? ' · ' : ''}{table.unit ? `单位：${table.unit}` : ''}
              </div>
            )}
          </div>
          <button type="button" className="anno-drawer-close" onClick={onClose} title="关闭（Esc）"><X size={20} /></button>
        </div>

        {/* 主体：左表格 右配置 */}
        <div className="anno-drawer-body">
          {/* 左栏：完整表格 */}
          <div className="anno-table-pane">
            <div className="anno-pane-hint">点击表头列切换：<span style={{ color: '#3b82f6' }}>🔑 查询键</span> → <span style={{ color: '#16a34a' }}>📊 标准值</span> → 取消</div>
            {headers.length === 0 && rows.length === 0 ? (
              <p style={{ fontSize: '13px', color: '#888' }}>表格内容为空</p>
            ) : (
              <div className="anno-table-scroll">
                <table style={{ fontSize: '12px', borderCollapse: 'collapse', minWidth: '100%' }}>
                  {headers.length > 0 && (
                    <thead style={{ background: '#f5f8fc', position: 'sticky', top: 0, zIndex: 1 }}>
                      {headers.map((hr, hi) => (
                        <tr key={`h${hi}`}>
                          {hr.map((cell, ci) => (
                            <th
                              key={ci}
                              onClick={hi === 0 ? () => cycleCol(ci) : undefined}
                              style={{
                                border: '1px solid #d1d5db', padding: '6px 10px',
                                textAlign: 'left', fontWeight: 600, whiteSpace: 'nowrap',
                                cursor: hi === 0 ? 'pointer' : 'default',
                                ...(hi === 0 ? colStyle(ci) : {}),
                              }}
                              title={hi === 0 ? '点击切换：查询键 → 标准值 → 取消' : ''}
                            >
                              {cell}
                              {hi === 0 && colState[ci] === 'key' && <span style={{ marginLeft: '4px', fontSize: '10px', color: '#3b82f6' }}>🔑</span>}
                              {hi === 0 && colState[ci] === 'value' && <span style={{ marginLeft: '4px', fontSize: '10px', color: '#16a34a' }}>📊</span>}
                            </th>
                          ))}
                        </tr>
                      ))}
                    </thead>
                  )}
                  <tbody>
                    {rows.map((row, ri) => (
                      <tr key={ri} style={{ background: ri % 2 === 0 ? '#fff' : '#fafbfc' }}>
                        {row.map((cell, ci) => (
                          <td key={ci} style={{
                            border: '1px solid #e5e7eb', padding: '5px 10px', whiteSpace: 'nowrap',
                            ...(colState[ci] === 'key' ? { background: '#eff6ff' } : {}),
                            ...(colState[ci] === 'value' ? { background: '#f0fdf4' } : {}),
                          }}>{cell}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* 右栏：标注配置 */}
          <div className="anno-config-pane">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px', gap: '6px', flexWrap: 'wrap' }}>
              <div style={{ fontSize: '13px', fontWeight: 700, color: '#142033' }}>标注配置</div>
              <button type="button" className="btn btn-outline" onClick={aiPrelabel} disabled={prelabeling} style={{ fontSize: '12px', padding: '3px 10px' }}>
                {prelabeling ? <Loader2 className="spin" size={12} /> : <Sparkles size={12} />}AI 预标注
              </button>
            </div>

            <div style={{ display: 'flex', gap: '6px', marginBottom: '12px', flexWrap: 'wrap' }}>
              <button
                type="button" className="btn btn-outline"
                onClick={() => {
                  const next: Record<number, ColState> = {};
                  const nextName: Record<number, string> = { ...colName };
                  const nextSem: Record<number, Semantic> = { ...colSemantic };
                  for (let c = 0; c < colCount; c++) {
                    if (colState[c] === 'key') next[c] = 'key';
                    else {
                      next[c] = 'value';
                      if (!nextName[c] && headers[0]?.[c]) nextName[c] = headers[0][c];
                      if (!nextSem[c]) nextSem[c] = 'upper_bound';
                    }
                  }
                  setColState(next); setColName(nextName); setColSemantic(nextSem);
                }}
                style={{ fontSize: '11px', padding: '3px 8px' }}
                title="行查表模式：先标好主键，再把剩余列全部设为标准值（默认上限）"
              >
                <Tag size={11} />剩余列全标为标准值
              </button>
              <button type="button" className="btn btn-outline" onClick={() => { setColState({}); setColName({}); setColSemantic({}); }} style={{ fontSize: '11px', padding: '3px 8px' }}>
                <X size={11} />清空列标注
              </button>
            </div>

            {/* 列语义配置 */}
            <div style={{ display: 'grid', gap: '8px', marginBottom: '14px' }}>
              {annotatedCols.length === 0 ? (
                <div style={{ fontSize: '12px', color: '#888', padding: '8px', background: '#f8fafc', borderRadius: '5px' }}>
                  请点击左侧表头列设置「查询键」和「标准值」
                </div>
              ) : annotatedCols.map(c => (
                <div key={c} style={{
                  padding: '7px 9px', borderRadius: '6px',
                  border: '1px solid ' + (colState[c] === 'key' ? '#3b82f6' : '#16a34a'),
                  background: colState[c] === 'key' ? '#eff6ff' : '#f0fdf4',
                }}>
                  <div style={{ fontSize: '10px', color: '#666', marginBottom: '3px' }}>
                    列 {c}（{headers[0]?.[c] ?? ''}）— {colState[c] === 'key' ? '🔑 查询键' : '📊 标准值'}
                  </div>
                  <input
                    type="text" value={colName[c] ?? ''}
                    onChange={e => setColName(n => ({ ...n, [c]: e.target.value }))}
                    placeholder="语义名（如 机组容量 / 直流供水管线）"
                    style={{ width: '100%', fontSize: '12px', padding: '4px 6px', border: '1px solid #d1d5db', borderRadius: '4px' }}
                  />
                  {colState[c] === 'value' && (
                    <select
                      value={colSemantic[c] ?? 'upper_bound'}
                      onChange={e => setColSemantic(s => ({ ...s, [c]: e.target.value as Semantic }))}
                      style={{ marginTop: '4px', width: '100%', fontSize: '12px', padding: '4px', border: '1px solid #d1d5db', borderRadius: '4px' }}
                    >
                      <option value="upper_bound">上限（项目实际 ≤ 此值即通过）</option>
                      <option value="lower_bound">下限（项目实际 ≥ 此值即通过）</option>
                      <option value="exact">精确匹配</option>
                    </select>
                  )}
                </div>
              ))}
            </div>

            {/* 功能区 */}
            <div style={{ marginBottom: '12px' }}>
              <div style={{ fontSize: '12px', fontWeight: 600, color: '#444', marginBottom: '4px' }}>功能区</div>
              <select value={functionalZone} onChange={e => setFunctionalZone(e.target.value)}
                style={{ width: '100%', fontSize: '12px', padding: '5px 6px', border: '1px solid #d1d5db', borderRadius: '4px' }}>
                <option value="">— 通用 / 未归类 —</option>
                {zoneOptions.map(z => <option key={z} value={z}>{z}</option>)}
              </select>
              {zoneOptions.length === 0 && (
                <div style={{ fontSize: '10px', color: '#bb4b5b', marginTop: '3px' }}>
                  此项目类型未在 FunctionalZoneCatalog 配置功能区
                </div>
              )}
            </div>

            {/* 适用情形 */}
            <div style={{ marginBottom: '12px' }}>
              <div style={{ fontSize: '12px', fontWeight: 600, color: '#444', marginBottom: '5px' }}>
                适用情形 <span style={{ color: '#888', fontWeight: 'normal' }}>（同组只能选一项；不选表示全局适用）</span>
              </div>
              <div style={{ maxHeight: '200px', overflowY: 'auto', border: '1px solid #e5e7eb', borderRadius: '4px', padding: '6px', background: '#fff' }}>
                {Object.entries(CASE_GROUPS).map(([stepStr, groups]) => {
                  const stepNo = Number(stepStr);
                  return groups.map(g => (
                    <div key={g.id} style={{ marginBottom: '6px' }}>
                      <div style={{ fontSize: '10px', color: '#666', marginBottom: '2px' }}>[第{stepNo}步] {g.title}</div>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                        {g.options.map(o => {
                          const selected = situations.some(s => s.groupId === g.id && s.value === o.value);
                          return (
                            <button key={o.value} type="button" onClick={() => toggleSituation(stepNo, g.id, o.value)}
                              style={{
                                fontSize: '10px', padding: '2px 6px',
                                border: '1px solid ' + (selected ? '#0d8a72' : '#d1d5db'),
                                background: selected ? '#0d8a72' : '#fff', color: selected ? '#fff' : '#444',
                                borderRadius: '3px', cursor: 'pointer',
                              }}>
                              {o.value}: {o.label.length > 14 ? o.label.slice(0, 14) + '…' : o.label}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  ));
                })}
              </div>
            </div>

            {/* 备注 */}
            <div style={{ marginBottom: '12px' }}>
              <div style={{ fontSize: '12px', fontWeight: 600, color: '#444', marginBottom: '4px' }}>备注</div>
              <textarea value={notes} onChange={e => setNotes(e.target.value)} rows={2}
                placeholder="可选：写一两句这张表的用途、注意事项..."
                style={{ width: '100%', fontSize: '12px', padding: '5px 6px', border: '1px solid #d1d5db', borderRadius: '4px' }} />
            </div>
          </div>
        </div>

        {/* 底部操作条 */}
        <div className="anno-drawer-foot">
          <button type="button" className="btn btn-outline" onClick={() => { reload(table); onNotice('已重置为上次保存的状态'); }} style={{ fontSize: '13px' }}>
            <RotateCcw size={13} />重置
          </button>
          <button type="button" className="btn btn-primary" onClick={save} disabled={saving} style={{ fontSize: '13px' }}>
            {saving ? <Loader2 className="spin" size={13} /> : <Save size={13} />}保存标注
          </button>
        </div>
      </div>
    </div>
  );
}
