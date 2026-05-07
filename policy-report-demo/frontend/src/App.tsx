import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  Bot,
  CheckCircle2,
  Download,
  Eye,
  FileCheck2,
  FileSearch,
  FileUp,
  FolderOpen,
  KeyRound,
  Loader2,
  RefreshCw,
  Save,
  Server,
  ShieldCheck,
  Undo2
} from 'lucide-react';
import {
  analyzeDocument,
  exportReport,
  fetchOllamaStatus,
  fetchProject,
  fetchWorkspaceState,
  generateReport,
  sourceFileUrl,
  updateField,
  updateSituation,
  withdrawDocument,
  uploadPolicyCard
} from './api';
import type {
  AiConfig,
  AnalysisResponse,
  DemoProject,
  EditableField,
  OllamaStatus,
  ReportResponse,
  StepWorkspace,
  WorkspaceState
} from './types';

const AI_CONFIG_KEY = 'policy-report-demo-ai-config';
const REPORT_DRAFT_KEY = 'policy-report-demo-report-draft';
const STATUS_TEXT: Record<string, string> = { pass: '通过', warn: '需关注', block: '未通过', todo: '待补', info: '已触发' };

type StatusKey = 'pass' | 'warn' | 'block' | 'todo' | 'info';

type MaterialSpec = {
  id: string;
  label: string;
  required: boolean;
  sub: string;
  keywords: string[];
  condition?: 'cultivatedLand' | 'petition';
};

type CaseOption = {
  value: string;
  label: string;
};

type CaseGroup = {
  id: string;
  title: string;
  options: CaseOption[];
};

type ValidationRow = {
  item: string;
  status: StatusKey;
  detail: string;
};

const MATERIAL_SPECS: Record<string, MaterialSpec[]> = {
  step1: [
    spec('pre', '建设用地预审批复', true, '法定前置条件', ['预审', '用地预审', '预审批复']),
    spec('approval', '项目立项批复文件', true, '核准/备案/立项', ['核准', '立项', '备案', '项目批复']),
    spec('design', '初步设计批复文件', true, '建设规模和标准', ['初步设计', '初设']),
    spec('approvalChange', '可行性研究报告变更批复', false, '有变更时上传', ['可研', '可行性研究', '变更批复']),
    spec('designChange', '初步设计变更批复', false, '有初设变更时上传', ['初设变更', '初步设计变更'])
  ],
  step2: [
    spec('survey', '勘测定界报告', true, '面积和界址来源', ['勘测', '定界']),
    spec('landClass', '土地分类权属面积汇总表', true, '地类面积核对', ['分类', '权属面积', '汇总表']),
    spec('ownership', '权属情况汇总表', true, '国有/集体权属', ['权属', '所有权', '使用权']),
    spec('landChangeOverlay', '年度国土变更调查套合情况分析', true, '年度变更调查套合', ['年度国土变更', '国土变更调查', '套合情况', '套合分析']),
    spec('illegalBuilt', '已批准建设用地来源材料', false, '存在建设用地时上传', ['批准建设用地', '合法来源']),
    spec('flood', '水利水电淹没区说明', false, '涉及时上传', ['淹没区', '水利水电'])
  ],
  step3: [
    spec('plan', '规划佐证材料', true, '国土空间规划符合性', ['规划', '三区三线', '用途管制']),
    spec('quota', '年度计划指标文件', true, '计划指标来源', ['计划指标', '指标配置']),
    spec('forest', '林地批复', true, '本项目涉及林地', ['林地', '林草']),
    spec('eco', '生态/保护地意见', false, '涉及红线或保护区时上传', ['生态保护红线', '自然保护区', '保护地'])
  ],
  step4: [
    spec('supplement', '补充耕地方案审查表', true, '占用耕地时必传，本项目不占耕地无需上传', ['补充耕地', '方案审查'], 'cultivatedLand'),
    spec('balance', '耕地占补平衡挂钩信息单', true, '占用耕地时必传，本项目不占耕地无需上传', ['占补平衡', '挂钩信息'], 'cultivatedLand')
  ],
  step5: [
    spec('notice', '征收土地预公告', true, '征地程序起点', ['预公告', '征收土地预公告']),
    spec('survey', '土地现状调查材料', true, '现状确认', ['土地现状调查', '现状调查材料', '现状调查']),
    spec('risk', '社会稳定风险评估报告', true, '社稳/稳评程序', ['社稳', '社稳报告', '稳评', '社会稳定风险']),
    spec('agreement', '征地补偿安置公告及照片', true, '公告及照片', ['征地补偿安置公告', '补偿安置公告', '公告照片', '照片']),
    spec('hearing', '听证材料', true, '听证告知、笔录或放弃听证', ['听证', '听证告知', '听证笔录', '放弃听证']),
    spec('socialSecurity', '社保审核意见', true, '被征地农民保障', ['社保', '社会保障'])
  ],
  step6: [
    spec('intensive', '节约集约用地论证分析专章', false, '超指标时必传', ['节约集约', '论证分析专章']),
    spec('industry', '行业主管部门意见', false, '超标准或特殊项目上传', ['行业主管', '主管部门意见']),
    spec('fee', '土地有偿使用费材料', true, '新增建设用地费用', ['有偿使用费', '缴纳', '缴库'])
  ],
  step7: [
    spec('geo', '地灾评估报告批复', true, '地灾易发区核验', ['地质灾害', '地灾评估', '地灾批复']),
    spec('mine', '压覆矿查询表', true, '压覆矿产结论', ['压覆矿查询', '压覆矿', '压覆', '矿产', '压矿']),
    spec('mineApproval', '压覆审批/补偿材料', false, '涉及压覆时上传', ['压覆审批', '补偿协议'])
  ],
  step8: [
    spec('petition', '信访处理说明', true, '有信访事项时必传，无信访无需上传', ['信访', '来信', '上访'], 'petition'),
    spec('illegal', '违法用地查处案卷', true, '违法用地闭合', ['违法用地', '行政处罚', '查处']),
    spec('rectification', '查处到位意见书', true, '处罚执行到位', ['查处到位', '整改到位', '罚款到账'])
  ]
};

const CASE_GROUPS: Record<string, CaseGroup[]> = {
  step1: [
    group('approvalSituation', '可研批复情形选择', ['预审在可研批复后 已出具检讨', '已超核准有效期 已延期', '可研变更 已批复', '无可研/核准变更']),
    group('designChange', '初步设计变更情形选择', ['普通变更 已批复', '审批权下放 地方办理', '无初步设计变更']),
    group('projectPhase', '分期/分段报批情形选择', ['分段报批 多城市', '分期报批 已确定期数']),
    group('landUseType', '单独选址情形选择', ['完全在规划范围外', '部分在规划范围内', '符合规划范围']),
    group('forestryApproval', '林地审批情形选择', ['涉及林地 已审批', '不涉及林地']),
    group('constructionStatus', '动工用地情形选择', ['项目未动工', '已动工 不超先行用地', '已动工 存在违法用地']),
    group('reductionStatus', '核减用地情形选择', ['未核减用地', '已核减用地'])
  ],
  step2: [
    group('caseInconsistency', '国土变更调查套合情况', ['与实际申请用地情况一致', '存在无合法来源建设用地', '存在已依法批准建设用地', '其他不一致情况']),
    group('caseNature53', '自然资发〔2023〕53号文', ['不涉及该文件', '涉及该文件']),
    group('caseFlood', '水利水电项目淹没区', ['不涉及淹没区', '涉及淹没区'])
  ],
  step3: [
    group('caseNatureReserve', '自然保护区情形选择', ['不位于自然保护区', '穿越/跨越保护区 不申请用地', '用地位于实验区 已同意']),
    group('caseEcoRedline', '生态保护红线情形选择', ['不位于生态保护红线', '穿越/跨越红线 不申请用地', '有限人为活动 已认定', '国家重大项目 不可避让']),
    group('casePlan', '计划指标配置情形选择', ['国家/省级重大项目配置', '使用省级存量处置规模指标']),
    group('caseBasicFarmland', '永久基本农田补划情形选择', ['占用永农 已补划', '不涉及占用永久基本农田'])
  ],
  step4: [
    group('caseSupplement', '耕地补充情形选择', ['不涉及占用耕地', '已足额补充耕地', '承诺补充耕地', '无法就地补充耕地']),
    group('casePaddy', '水田补充情形选择', ['不涉及占用水田', '已足额补充水田', '承诺补充水田', '无法补充水田'])
  ],
  step5: [
    group('casePublicInterest', '征地公共利益情形选择', ['能源基础设施', '交通基础设施', '水利基础设施', '其他公共利益']),
    group('caseAgreement', '补偿协议签订率情形选择', ['全部签订', '部分签订 ≥90%', '部分签订 <90%'])
  ],
  step6: [
    group('caseIndustry', '产业政策分类情形选择', ['鼓励类建设项目', '允许类建设项目', '限制类建设项目']),
    group('caseSupply', '供地方式情形选择', ['划拨方式供地', '出让方式供地', '租赁方式供地']),
    group('supplyMethod', '有偿使用费情形选择', ['涉及新增建设用地 已测算费用', '划拨且不涉及新增建设用地'])
  ],
  step7: [
    group('caseGeo', '地质灾害评估情形选择', ['位于易发区 已评估', '不在易发区']),
    group('caseMineral', '压覆矿产情形选择', ['不压覆重要矿产', '压覆 已协商补偿', '压覆 已办理审批'])
  ],
  step8: [
    group('petitionType', '信访处理情形选择', ['无信访事项', '涉及信访 已妥善处理']),
    group('selectedCase', '违法用地情形选择', ['未动工无违法', '未动工 有临时用地', '范围内他人违法 已处罚', '2020年前违法 已处罚承诺履行', '已动工超先行用地 已处罚', '涉及生态红线/保护区 从重处罚'])
  ]
};

function App() {
  const [project, setProject] = useState<DemoProject | null>(null);
  const [workspace, setWorkspace] = useState<WorkspaceState | null>(null);
  const [ollama, setOllama] = useState<OllamaStatus | null>(null);
  const [selectedStep, setSelectedStep] = useState('step1');
  const [report, setReport] = useState<ReportResponse | null>(null);
  const [reportDraft, setReportDraft] = useState(() => localStorage.getItem(REPORT_DRAFT_KEY) ?? '');
  const [previewOpen, setPreviewOpen] = useState(false);
  const [loading, setLoading] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [dragTarget, setDragTarget] = useState('');
  const [aiConfig, setAiConfig] = useState<AiConfig>(() => readAiConfig());
  const policyInputRef = useRef<HTMLInputElement | null>(null);
  const bulkInputRef = useRef<HTMLInputElement | null>(null);
  const lastFilesRef = useRef<File[]>([]);

  useEffect(() => {
    void refreshAll();
  }, []);

  const activeStep = useMemo(
    () => project?.steps.find((step) => step.id === selectedStep) ?? project?.steps[0],
    [project, selectedStep]
  );
  const activeWorkspace = workspace?.steps[selectedStep];
  const activeMaterials = MATERIAL_SPECS[selectedStep] ?? [];
  const activeCases = CASE_GROUPS[selectedStep] ?? [];
  const activeAnalyses = activeWorkspace?.analyses ?? [];
  const allAnalyses = useMemo(() => Object.values(workspace?.steps ?? {}).flatMap((step) => step.analyses), [workspace]);
  const validationRows = useMemo(() => buildValidationRows(activeWorkspace, activeMaterials, activeCases), [activeWorkspace, activeMaterials, activeCases]);
  const statusCounts = useMemo(() => countValidation(validationRows), [validationRows]);

  async function refreshAll() {
    setError('');
    try {
      const [projectData, ollamaStatus, stateData] = await Promise.all([fetchProject(), fetchOllamaStatus(), fetchWorkspaceState()]);
      setProject(projectData);
      setOllama(ollamaStatus);
      setWorkspace(stateData);
    } catch (err) {
      setError(err instanceof Error ? err.message : '后端暂时不可用');
    }
  }

  function saveAiConfig() {
    localStorage.setItem(AI_CONFIG_KEY, JSON.stringify(aiConfig));
    setNotice(aiConfig.provider === 'deepseek' ? 'DeepSeek 配置已保存到本机浏览器。' : '已切换为本机 Ollama。');
  }

  async function handlePolicyCard(files: FileList | File[]) {
    const file = Array.from(files)[0];
    if (!file) return;
    setLoading('policy');
    setError('');
    try {
      const result = await uploadPolicyCard(file);
      setWorkspace(await fetchWorkspaceState());
      setNotice(`${result.fileName} 已持久化为当前政策明白卡，解析 ${result.textLength} 字。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '明白卡解析失败');
    } finally {
      setLoading('');
      setDragTarget('');
    }
  }

  async function handleBulkFiles(files: FileList | File[] | null) {
    const list = Array.from(files ?? []).filter(Boolean);
    if (list.length === 0) return;
    lastFilesRef.current = list;
    setLoading('bulk');
    setError('');
    try {
      const results: AnalysisResponse[] = [];
      for (const file of list) {
        results.push(await analyzeDocument(file, '', aiConfig, selectedStep));
      }
      setWorkspace(await fetchWorkspaceState());
      setNotice(`已解析 ${list.length} 份材料，并自动归档：${summarizeRouting(results)}。`);
      if (results[0]?.targetStep) {
        setSelectedStep(results[0].targetStep);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '批量解析失败');
    } finally {
      setLoading('');
      setDragTarget('');
    }
  }

  async function reparseAllFiles() {
    if (lastFilesRef.current.length === 0) {
      setNotice('当前浏览器会话没有可重新解析的 File 对象，请把材料包再拖入一次。');
      return;
    }
    await handleBulkFiles(lastFilesRef.current);
  }

  async function saveFieldValue(field: EditableField, value: string) {
    setWorkspace((current) => patchField(current, field.stepId, field.key, value));
    try {
      setWorkspace(await updateField(field.stepId, field.key, value, 'warn'));
      setNotice(`${field.label} 已保存并触发实时校验。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '字段保存失败');
    }
  }

  async function selectCase(groupId: string, value: string) {
    setWorkspace((current) => patchSituation(current, selectedStep, groupId, value));
    try {
      setWorkspace(await updateSituation(selectedStep, groupId, value));
      setNotice('情形选择已保存，校验表已实时刷新。');
    } catch (err) {
      setError(err instanceof Error ? err.message : '情形保存失败');
    }
  }

  async function withdrawUploadedFile(fileId?: string | null) {
    if (!fileId) return;
    setLoading(`withdraw-${fileId}`);
    setError('');
    try {
      setWorkspace(await withdrawDocument(fileId));
      setNotice('已撤回该上传文件，并同步刷新字段、材料清单和校验结果。');
    } catch (err) {
      setError(err instanceof Error ? err.message : '材料撤回失败');
    } finally {
      setLoading('');
    }
  }

  function saveDraft() {
    localStorage.setItem(AI_CONFIG_KEY, JSON.stringify(aiConfig));
    localStorage.setItem(REPORT_DRAFT_KEY, reportDraft);
    setNotice('草稿已保存。字段和情形选择已落到本机后端状态库。');
  }

  async function previewReport() {
    setLoading('report');
    setError('');
    try {
      const nextReport = await generateReport(allAnalyses);
      setReport(nextReport);
      setReportDraft(nextReport.markdown);
      setPreviewOpen(true);
      localStorage.setItem(REPORT_DRAFT_KEY, nextReport.markdown);
      setNotice('已生成在线 Word 预览，预览区可直接修改。');
    } catch (err) {
      setError(err instanceof Error ? err.message : '报告生成失败');
    } finally {
      setLoading('');
    }
  }

  async function downloadReport(format: 'md' | 'docx') {
    setLoading(`export-${format}`);
    setError('');
    try {
      let markdown = reportDraft;
      if (!markdown.trim()) {
        const nextReport = await generateReport(allAnalyses);
        markdown = nextReport.markdown;
        setReport(nextReport);
        setReportDraft(markdown);
        setPreviewOpen(true);
        localStorage.setItem(REPORT_DRAFT_KEY, markdown);
      }
      const blob = await exportReport(format, allAnalyses, markdown);
      downloadBlob(blob, reportFileName(project?.projectName, format));
      setNotice(`已导出为 ${format === 'md' ? 'Markdown' : 'Word'} 文件。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '报告导出失败');
    } finally {
      setLoading('');
    }
  }

  function saveAndNext() {
    saveDraft();
    const steps = project?.steps ?? [];
    const index = steps.findIndex((step) => step.id === selectedStep);
    const nextStep = steps[Math.min(index + 1, steps.length - 1)];
    if (nextStep) {
      setSelectedStep(nextStep.id);
    }
  }

  return (
    <main className="app-shell">
      <header className="top-bar">
        <div className="brand-block">
          <div className="brand-icon"><ShieldCheck size={21} /></div>
          <div>
            <h1>建设用地报批审查报告智能生成系统</h1>
            <p>{project?.projectName ?? '兴宁五塘风电场一期工程'} · 一次导入 · 自动归档 · 实时校验</p>
          </div>
        </div>
        <div className="top-meta">
          <StatusPill ollama={ollama} />
          <span className="project-badge">单独选址建设项目</span>
        </div>
      </header>

      <StepIndicator project={project} workspace={workspace} selectedStep={selectedStep} onSelect={setSelectedStep} />

      {(error || notice) && <div className={error ? 'message-strip error-strip' : 'message-strip notice-strip'}>{error ? <AlertTriangle size={18} /> : <CheckCircle2 size={18} />}{error || notice}</div>}

      <section className="main-container">
        <aside className="upload-panel">
          <section className="card ai-card">
            <div className="card-header"><KeyRound size={17} />AI 配置</div>
            <div className="card-body compact-body">
              <div className="segmented">
                <button className={aiConfig.provider === 'deepseek' ? 'active' : ''} type="button" onClick={() => setAiConfig({ ...aiConfig, provider: 'deepseek' })}>DeepSeek</button>
                <button className={aiConfig.provider === 'ollama' ? 'active' : ''} type="button" onClick={() => setAiConfig({ ...aiConfig, provider: 'ollama' })}>Ollama</button>
              </div>
              <input className="config-input" type="password" placeholder="DeepSeek API Key" value={aiConfig.deepseekApiKey} onChange={(event) => setAiConfig({ ...aiConfig, deepseekApiKey: event.target.value })} />
              <input className="config-input" type="text" value={aiConfig.deepseekModel} onChange={(event) => setAiConfig({ ...aiConfig, deepseekModel: event.target.value })} />
              <button className="btn btn-outline full" type="button" onClick={saveAiConfig}><Save size={16} />保存配置</button>
            </div>
          </section>

          <UploadDropZone title="政策明白卡" subtitle={activeWorkspace?.policyCardFileName ?? '根目录明白卡'} loading={loading === 'policy'} dragging={dragTarget === 'policy'} onPick={() => policyInputRef.current?.click()} onFiles={handlePolicyCard} onDrag={(value) => setDragTarget(value ? 'policy' : '')} />
          <input ref={policyInputRef} className="hidden-input" type="file" accept=".pdf,.doc,.docx,.txt,.md" onChange={(event) => { void handlePolicyCard(event.target.files ?? []); event.currentTarget.value = ''; }} />

          <UploadDropZone title="一把导入全部材料" subtitle="把 PDF、Word、图片、txt 全部拖进来，系统按文件名和内容归档到八步" loading={loading === 'bulk'} dragging={dragTarget === 'bulk'} onPick={() => bulkInputRef.current?.click()} onFiles={handleBulkFiles} onDrag={(value) => setDragTarget(value ? 'bulk' : '')} />
          <input ref={bulkInputRef} className="hidden-input" type="file" multiple accept=".pdf,.doc,.docx,.txt,.md,.html,.png,.jpg,.jpeg" onChange={(event) => { void handleBulkFiles(event.target.files); event.currentTarget.value = ''; }} />

          <section className="card material-ledger">
            <div className="card-header"><FolderOpen size={17} />本步骤材料清单</div>
            <div className="card-body">
              <MaterialGroup title="必传文件" specs={activeMaterials.filter((item) => isMaterialRequired(item, activeWorkspace))} analyses={activeAnalyses} workspaceStep={activeWorkspace} onWithdraw={withdrawUploadedFile} />
              <MaterialGroup title="条件必传（当前无需上传）" specs={activeMaterials.filter((item) => isMaterialSkipped(item, activeWorkspace))} analyses={activeAnalyses} workspaceStep={activeWorkspace} onWithdraw={withdrawUploadedFile} />
              <MaterialGroup title="选传文件" specs={activeMaterials.filter((item) => !isMaterialRequired(item, activeWorkspace) && !isMaterialSkipped(item, activeWorkspace))} analyses={activeAnalyses} workspaceStep={activeWorkspace} onWithdraw={withdrawUploadedFile} />
            </div>
          </section>
        </aside>

        <section className="preview-panel">
          <div className="step-headline">
            <div>
              <span className="muted-label">当前步骤 {activeStep?.id?.replace('step', '')}</span>
              <h2>{activeStep?.title}</h2>
              <p>{activeStep?.goal}</p>
            </div>
            <div className="status-tally">
              <StatusCount label="通过" value={statusCounts.pass} status="pass" />
              <StatusCount label="需关注" value={statusCounts.warn} status="warn" />
              <StatusCount label="未通过" value={statusCounts.block} status="block" />
              <StatusCount label="待补" value={statusCounts.todo} status="todo" />
            </div>
          </div>

          <ParsedFileCards analyses={activeAnalyses} onWithdraw={withdrawUploadedFile} />

          <section className="preview-section">
            <h3><span className="dot dot-blue" />解析字段核对</h3>
            <FieldsTable fields={activeWorkspace?.fields ?? []} onSave={saveFieldValue} />
          </section>

          <section className="preview-section">
            <h3><span className="dot dot-orange" />情形选择</h3>
            <CaseSelectors groups={activeCases} values={activeWorkspace?.situations ?? {}} onSelect={selectCase} />
          </section>

          <section className="preview-section">
            <h3><span className="dot dot-orange" />系统实时校验结果</h3>
            <ValidationTable rows={validationRows} />
          </section>

          {previewOpen && (
            <section className="preview-section word-preview-section">
              <h3><span className="dot dot-blue" />在线审查报告预览</h3>
              <div className="word-shell">
                <textarea value={reportDraft} onChange={(event) => setReportDraft(event.target.value)} spellCheck={false} />
              </div>
              <div className="word-actions">
                <button className="btn btn-outline" type="button" onClick={() => void downloadReport('md')} disabled={!!loading}>{loading === 'export-md' ? <Loader2 className="spin" size={16} /> : <Download size={16} />}导出为 .md</button>
                <button className="btn btn-primary" type="button" onClick={() => void downloadReport('docx')} disabled={!!loading}>{loading === 'export-docx' ? <Loader2 className="spin" size={16} /> : <Download size={16} />}导出为 .docx</button>
              </div>
              {report && <div className="report-highlights">{report.highlights.map((item) => <span key={item}>{item}</span>)}</div>}
            </section>
          )}
        </section>
      </section>

      <footer className="action-bar">
        <button className="btn btn-outline" type="button" onClick={() => void reparseAllFiles()} disabled={!!loading}>{loading === 'bulk' ? <Loader2 className="spin" size={18} /> : <RefreshCw size={18} />}重新解析全部文件</button>
        <button className="btn btn-outline" type="button" onClick={saveDraft}><Save size={18} />保存草稿</button>
        <button className="btn btn-teal" type="button" onClick={() => void previewReport()} disabled={!!loading}>{loading === 'report' ? <Loader2 className="spin" size={18} /> : <Eye size={18} />}预览审查报告</button>
        <button className="btn btn-primary" type="button" onClick={saveAndNext}><FileCheck2 size={18} />保存并进入下一步 →</button>
      </footer>
    </main>
  );
}

function StepIndicator({ project, workspace, selectedStep, onSelect }: { project: DemoProject | null; workspace: WorkspaceState | null; selectedStep: string; onSelect: (stepId: string) => void }) {
  return (
    <nav className="step-indicator" aria-label="八步审查">
      {project?.steps.map((step, index) => {
        const workspaceStep = workspace?.steps[step.id];
        const materials = MATERIAL_SPECS[step.id] ?? [];
        const done = missingRequired(materials, workspaceStep?.analyses ?? [], workspaceStep).length === 0 && (workspaceStep?.analyses.length ?? 0) > 0;
        return (
          <button key={step.id} className={`step-node ${step.id === selectedStep ? 'active' : ''} ${done ? 'done' : ''}`} type="button" onClick={() => onSelect(step.id)}>
            <span className="num">{done ? '✓' : index + 1}</span>
            <span>{step.title}</span>
          </button>
        );
      })}
    </nav>
  );
}

function UploadDropZone({ title, subtitle, loading, dragging, onPick, onFiles, onDrag }: { title: string; subtitle: string; loading: boolean; dragging: boolean; onPick: () => void; onFiles: (files: FileList | File[]) => void; onDrag: (value: boolean) => void }) {
  return (
    <section className={dragging ? 'upload-dropzone dragging' : 'upload-dropzone'} onDragOver={(event) => { event.preventDefault(); onDrag(true); }} onDragLeave={() => onDrag(false)} onDrop={(event) => { event.preventDefault(); onDrag(false); void onFiles(event.dataTransfer.files); }}>
      <FileUp size={24} />
      <div><strong>{title}</strong><span>{subtitle}</span></div>
      <button className="btn btn-outline full" type="button" onClick={onPick} disabled={loading}>{loading ? <Loader2 className="spin" size={16} /> : <FileSearch size={16} />}选择/拖入</button>
    </section>
  );
}

function MaterialGroup({ title, specs, analyses, workspaceStep, onWithdraw }: { title: string; specs: MaterialSpec[]; analyses: AnalysisResponse[]; workspaceStep?: StepWorkspace; onWithdraw: (fileId?: string | null) => void }) {
  if (specs.length === 0) return null;
  return <div className="material-group"><div className="material-title">{title}</div>{specs.map((material) => <MaterialItem key={material.id} spec={material} analyses={analyses} workspaceStep={workspaceStep} onWithdraw={onWithdraw} />)}</div>;
}

function MaterialItem({ spec: material, analyses, workspaceStep, onWithdraw }: { spec: MaterialSpec; analyses: AnalysisResponse[]; workspaceStep?: StepWorkspace; onWithdraw: (fileId?: string | null) => void }) {
  const required = isMaterialRequired(material, workspaceStep);
  const skipped = isMaterialSkipped(material, workspaceStep);
  const matches = skipped ? [] : analyses.filter((analysis) => matchesMaterial(analysis, material));
  const uploaded = matches.length > 0;
  const statusClass = uploaded ? 'uploaded' : skipped ? 'skipped' : required ? 'missing' : 'optional';
  return (
    <div className={`upload-item ${statusClass}`}>
      <div className="item-header">
        <div className="item-title-area"><span className="item-icon">📄</span><div><span className="item-label">{material.label}</span><div className="item-sub">{material.sub}</div></div></div>
        <span className={uploaded ? 'badge-uploaded' : skipped ? 'badge-skipped' : required ? 'badge-required' : 'badge-optional'}>{uploaded ? '已传' : skipped ? '无需上传' : required ? '必传' : '选传'}</span>
      </div>
      <div className="file-info">
        {uploaded ? matches.map((analysis) => <span className="source-file-actions" key={analysis.fileId || analysis.fileName}><button className="source-link" type="button" onClick={() => openSourceFile(analysis.fileId)}>{analysis.fileName}</button><button className="withdraw-btn" type="button" onClick={() => onWithdraw(analysis.fileId)} title="撤回该材料"><Undo2 size={13} />撤回</button></span>) : <span className="upload-hint">{skipped ? '当前项目情形无需上传' : required ? '尚未识别到该必传材料' : '项目涉及时上传'}</span>}
        <StatusBadge status={uploaded || skipped ? 'pass' : required ? 'block' : 'todo'} />
      </div>
    </div>
  );
}

function ParsedFileCards({ analyses, onWithdraw }: { analyses: AnalysisResponse[]; onWithdraw: (fileId?: string | null) => void }) {
  return (
    <section className="preview-section">
      <h3><span className="dot dot-blue" />自动归档文件解析</h3>
      {analyses.length === 0 ? <p className="quiet">把材料一股脑拖进左侧上传框后，每个文件会在这里单独生成一个解析卡片。</p> : (
        <div className="parsed-stack">
          {analyses.map((analysis) => <article className="parsed-card" key={analysis.fileId || `${analysis.fileName}-${analysis.size}`}>
            <div className="parsed-card-head"><div><button className="file-title-link" type="button" onClick={() => openSourceFile(analysis.fileId)}>{analysis.fileName}</button><p>{analysis.detectedDocumentType} · 自动归档到 {analysis.targetStep}</p></div><div className="parsed-card-actions"><StatusBadge status={analysis.extractedFields.length > 0 ? 'pass' : 'warn'} /><button className="withdraw-btn" type="button" onClick={() => onWithdraw(analysis.fileId)} title="撤回该文件"><Undo2 size={13} />撤回</button></div></div>
            <div className="parsed-metrics"><span>文本 {analysis.textLength} 字</span><span>字段 {analysis.extractedFields.length} 个</span><span>校验 {analysis.policyChecks.length} 条</span><span>{analysis.aiAdvice.provider} · {analysis.aiAdvice.model}</span></div>
            <p className="ai-summary"><Bot size={15} />{analysis.aiAdvice.summary}</p>
          </article>)}
        </div>
      )}
    </section>
  );
}

function FieldsTable({ fields, onSave }: { fields: EditableField[]; onSave: (field: EditableField, value: string) => void }) {
  return <table className="data-table"><thead><tr><th>字段名称</th><th>解析值</th><th>数据来源</th><th>操作</th></tr></thead><tbody>{fields.map((field) => <FieldRow key={`${field.stepId}-${field.key}`} field={field} onSave={onSave} />)}</tbody></table>;
}

function FieldRow({ field, onSave }: { field: EditableField; onSave: (field: EditableField, value: string) => void }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(field.value);
  useEffect(() => setValue(field.value), [field.value]);

  function save() {
    if (value !== field.value) onSave(field, value);
    setEditing(false);
  }

  return (
    <tr className={field.status === 'warn' ? 'highlight-warn' : field.status === 'block' ? 'highlight-block' : ''}>
      <td><strong>{field.label}</strong>{field.required && <span className="required-dot">必</span>}</td>
      <td>{editing ? <input className="table-input" value={value} onChange={(event) => setValue(event.target.value)} autoFocus /> : <span>{field.value || '待补'}</span>}</td>
      <td><button className="field-source" type="button" onClick={() => openSourceFile(field.sourceFileId)} disabled={!field.sourceFileId}>📎 {field.source}</button></td>
      <td><button className="modify-btn" type="button" onClick={editing ? save : () => setEditing(true)}>{editing ? '保存' : '修改'}</button></td>
    </tr>
  );
}

function CaseSelectors({ groups, values, onSelect }: { groups: CaseGroup[]; values: Record<string, string>; onSelect: (groupId: string, value: string) => void }) {
  return (
    <div className="case-blocks">
      {groups.map((groupItem) => {
        const selected = values[groupItem.id] ?? groupItem.options[0]?.value;
        return <div className="case-group" key={groupItem.id}><h4>{groupItem.title}</h4><div className="case-selector">{groupItem.options.map((option, index) => <button key={option.value} className={selected === option.value ? 'case-card selected' : 'case-card'} type="button" onClick={() => onSelect(groupItem.id, option.value)}><span className="case-num">{index + 1}</span><span>{option.label}</span></button>)}</div><p className="current-case">当前选择：情形{toCircled(selected)} — {groupItem.options.find((option) => option.value === selected)?.label}</p></div>;
      })}
    </div>
  );
}

function ValidationTable({ rows }: { rows: ValidationRow[] }) {
  return <table className="validation-table"><thead><tr><th>校验项</th><th>结果</th><th>说明</th></tr></thead><tbody>{rows.map((row) => <tr key={row.item}><td>{row.item}</td><td><StatusBadge status={row.status} /></td><td>{row.detail}</td></tr>)}</tbody></table>;
}

function StatusPill({ ollama }: { ollama: OllamaStatus | null }) {
  if (!ollama) return <div className="status-pill info"><Server size={16} />模型检测中</div>;
  return <div className={ollama.reachable ? 'status-pill pass' : 'status-pill warn'} title={ollama.installedModels.join('\n')}><Server size={16} />{ollama.reachable ? `Ollama ${ollama.installedModels.length} 个` : 'Ollama 未连接'}</div>;
}

function StatusCount({ label, value, status }: { label: string; value: number; status: string }) {
  return <div className={`count-box ${status}`}><strong>{value}</strong><span>{label}</span></div>;
}

function StatusBadge({ status }: { status: string }) {
  const normalized = status in STATUS_TEXT ? status : 'todo';
  return <span className={`status-badge ${normalized}`}>{statusIcon(normalized)} {STATUS_TEXT[normalized]}</span>;
}

function readAiConfig(): AiConfig {
  const fallback: AiConfig = { provider: 'deepseek', deepseekApiKey: '', deepseekModel: 'deepseek-chat' };
  try {
    return { ...fallback, ...JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || '{}') };
  } catch {
    return fallback;
  }
}

function spec(id: string, label: string, required: boolean, sub: string, keywords: string[], condition?: MaterialSpec['condition']): MaterialSpec {
  return { id, label, required, sub, keywords, condition };
}

function group(id: string, title: string, labels: string[]): CaseGroup {
  return { id, title, options: labels.map((label, index) => ({ value: String(index + 1), label })) };
}

function matchesMaterial(analysis: AnalysisResponse, material: MaterialSpec) {
  const haystack = `${analysis.fileName} ${analysis.detectedDocumentType} ${analysis.textPreview}`.toLowerCase();
  return material.keywords.some((keyword) => haystack.includes(keyword.toLowerCase()));
}

function missingRequired(materials: MaterialSpec[], analyses: AnalysisResponse[], step?: StepWorkspace) {
  return materials.filter((material) => isMaterialRequired(material, step) && !analyses.some((analysis) => matchesMaterial(analysis, material)));
}

function buildValidationRows(step: StepWorkspace | undefined, materials: MaterialSpec[], cases: CaseGroup[]): ValidationRow[] {
  const analyses = step?.analyses ?? [];
  const missing = missingRequired(materials, analyses, step);
  const skipped = materials.filter((material) => isMaterialSkipped(material, step));
  const optionalUploaded = materials.filter((material) => !isMaterialRequired(material, step) && !isMaterialSkipped(material, step) && analyses.some((analysis) => matchesMaterial(analysis, material)));
  const fieldWarnings = (step?.fields ?? []).filter((field) => field.status === 'warn' || field.status === 'block');
  const situationCount = Object.keys(step?.situations ?? {}).length;
  const rows: ValidationRow[] = [
    { item: '必传材料齐套', status: missing.length === 0 ? 'pass' : 'block', detail: missing.length === 0 ? `已识别 ${materials.filter((item) => isMaterialRequired(item, step)).length} 类必传材料。` : `缺少：${missing.map((item) => item.label).join('、')}` },
    { item: '选传材料识别', status: optionalUploaded.length > 0 ? 'info' : 'todo', detail: optionalUploaded.length > 0 ? `已自动识别选传材料：${optionalUploaded.map((item) => item.label).join('、')}` : '未识别到选传材料，项目涉及时再上传。' },
    { item: '条件必传材料', status: skipped.length > 0 ? 'pass' : 'todo', detail: skipped.length > 0 ? `当前情形无需上传：${skipped.map((item) => item.label).join('、')}` : '当前步骤没有可免传的条件必传材料。' },
    { item: '字段核对状态', status: fieldWarnings.length === 0 ? 'pass' : 'warn', detail: fieldWarnings.length === 0 ? '当前字段未发现需人工关注项。' : `${fieldWarnings.length} 个字段需要确认或已人工修改。` },
    { item: '情形选择确认', status: situationCount >= cases.length ? 'pass' : 'warn', detail: `已保存 ${situationCount}/${cases.length} 组情形选择，上方修改会实时刷新本表。` }
  ];
  for (const check of step?.checklist ?? []) rows.push({ item: check.title, status: normalizeStatus(check.status), detail: check.detail });
  return rows;
}

function isMaterialRequired(material: MaterialSpec, step?: StepWorkspace) {
  return material.required && !isMaterialSkipped(material, step);
}

function isMaterialSkipped(material: MaterialSpec, step?: StepWorkspace) {
  if (!material.condition || !step) return false;
  if (material.condition === 'cultivatedLand') return !hasPositiveCondition(step, ['占用耕地', '耕地'], 'caseSupplement');
  if (material.condition === 'petition') return !hasPositiveCondition(step, ['信访事项', '信访'], 'petitionType');
  return false;
}

function hasPositiveCondition(step: StepWorkspace, labels: string[], situationKey: string) {
  const value = step.fields.find((field) => labels.some((label) => field.label.includes(label)))?.value ?? '';
  if (value) return !isNegativeValue(value);
  return (step.situations?.[situationKey] ?? '1') !== '1';
}

function isNegativeValue(value: string) {
  const normalized = value.replace(/\s+/g, '');
  return /不涉及|不存在|无/.test(normalized) || /(^|[^0-9])0(?:\.0+)?公顷/.test(normalized);
}

function countValidation(rows: ValidationRow[]) {
  return rows.reduce((counts, row) => {
    if (row.status === 'info') counts.pass += 1;
    else counts[row.status] += 1;
    return counts;
  }, { pass: 0, warn: 0, block: 0, todo: 0 });
}

function patchField(current: WorkspaceState | null, stepId: string, key: string, value: string): WorkspaceState | null {
  if (!current || !current.steps[stepId]) return current;
  const step = current.steps[stepId];
  return { ...current, steps: { ...current.steps, [stepId]: { ...step, fields: step.fields.map((field) => field.key === key ? { ...field, value, source: '人工修改', status: 'warn' } : field) } } };
}

function patchSituation(current: WorkspaceState | null, stepId: string, groupId: string, value: string): WorkspaceState | null {
  if (!current || !current.steps[stepId]) return current;
  const step = current.steps[stepId];
  return { ...current, steps: { ...current.steps, [stepId]: { ...step, situations: { ...(step.situations ?? {}), [groupId]: value } } } };
}

function summarizeRouting(results: AnalysisResponse[]) {
  const counts = results.reduce<Record<string, number>>((map, analysis) => {
    map[analysis.targetStep] = (map[analysis.targetStep] ?? 0) + 1;
    return map;
  }, {});
  return Object.entries(counts).map(([stepId, count]) => `${stepId} ${count}份`).join('，');
}

function openSourceFile(fileId?: string | null) {
  if (!fileId) return;
  window.open(sourceFileUrl(fileId), '_blank', 'noopener,noreferrer');
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function reportFileName(projectName: string | undefined, format: 'md' | 'docx') {
  return `${projectName || '建设用地报批审查报告'}.${format}`.replace(/[\\/:*?"<>|]/g, '_');
}

function normalizeStatus(status: string): StatusKey {
  if (status === 'pass' || status === 'warn' || status === 'block' || status === 'todo' || status === 'info') return status;
  return 'todo';
}

function statusIcon(status: string) {
  if (status === 'pass') return '✅';
  if (status === 'warn') return '⚠';
  if (status === 'block') return '❌';
  if (status === 'info') return '📝';
  return '○';
}

function toCircled(value?: string) {
  const map: Record<string, string> = { '1': '①', '2': '②', '3': '③', '4': '④', '5': '⑤', '6': '⑥' };
  return map[value ?? '1'] ?? value;
}

export default App;