import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  Bot,
  CheckCircle2,
  Database,
  Download,
  Eye,
  FileCheck2,
  FileSearch,
  FileUp,
  FolderOpen,
  Home,
  KeyRound,
  Loader2,
  Plus,
  RefreshCw,
  Save,
  Search,
  Server,
  ShieldCheck,
  Undo2
} from 'lucide-react';
import {
  analyzeDocument,
  createProject,
  exportReport,
  fetchLandStandards,
  fetchOllamaStatus,
  fetchProject,
  fetchProjectDashboard,
  fetchStandardMatches,
  fetchWorkspaceState,
  generateReport,
  refreshStandardMatches,
  sourceFileUrl,
  synthesizeAnalyses,
  updateField,
  updateSituation,
  withdrawDocument,
  uploadPolicyCard
} from './api';
import type {
  AiConfig,
  AnalysisResponse,
  CreateProjectPayload,
  DemoProject,
  EditableField,
  LandUseStandard,
  LandUseStandardMatch,
  OllamaStatus,
  ProjectDashboard,
  ProjectRecord,
  ReportResponse,
  StepWorkspace,
  SynthesizeResponse,
  WorkspaceState
} from './types';

const AI_CONFIG_KEY = 'policy-report-demo-ai-config';
const REPORT_DRAFT_KEY = 'policy-report-demo-report-draft';
const ACTIVE_PROJECT_KEY = 'policy-report-demo-active-project-id';
const STATUS_TEXT: Record<string, string> = { pass: '通过', warn: '需关注', block: '未通过', todo: '待补', info: '已触发' };

type StatusKey = 'pass' | 'warn' | 'block' | 'todo' | 'info';

type MaterialSpec = {
  id: string;
  label: string;
  required: boolean;
  sub: string;
  keywords: string[];
  condition?: 'cultivatedLand' | 'forest' | 'petition';
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
    spec('design', '初步设计批复文件', false, '建设规模和标准，选传', ['初步设计', '初设']),
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
    spec('forest', '林地批复', true, '涉及林地时条件必传', ['林地', '林草'], 'forest'),
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
    spec('mine', '压覆矿查询表', false, '压覆矿产结论，选传', ['压覆矿查询', '压覆矿', '压覆', '矿产', '压矿']),
    spec('mineApproval', '压覆审批/补偿材料', false, '涉及压覆时上传', ['压覆审批', '补偿协议'])
  ],
  step8: [
    spec('petition', '信访处理说明', false, '有信访事项时条件选传', ['信访', '来信', '上访']),
    spec('illegal', '违法用地查处案卷', false, '存在违法用地时条件选传', ['违法用地', '行政处罚', '查处']),
    spec('rectification', '查处到位意见书', false, '需证明处罚执行到位时条件选传', ['查处到位', '整改到位', '罚款到账'])
  ]
};

const CASE_GROUPS: Record<string, CaseGroup[]> = {
  step1: [
    group('approvalSituation', '可研批复情形选择', ['预审在可研批复后 已出具检讨', '已超核准有效期 已延期', '可研变更 已批复', '无可研/核准变更']),
    group('designChange', '初步设计变更情形选择', ['普通变更 已批复', '审批权下放 地方办理', '无初步设计变更']),
    group('projectPhase', '分期/分段报批情形选择', ['分段报批 多城市', '分期报批 已确定期数']),
    group('landUseType', '单独选址情形选择', ['完全在规划范围外', '部分在规划范围内', '符合规划范围']),
    group('forestryApproval', '林地审批情形选择', ['涉及林地 已审批', '不涉及林地']),
    group('constructionStatus', '动工用地情形选择', ['未动工，不存在违法用地问题', '未动工，但项目范围内存在经批准的临时用地', '已动工，未超出经批准的先行用地范围', '项目主体未动工，但存在非本项目主体的违法用地行为', '已动工，超出经批准的先行用地范围', '已动工，存在违法用地问题']),
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

async function collectDropFiles(event: React.DragEvent): Promise<File[]> {
  const entries = Array.from(event.dataTransfer.items)
    .map((item) => item.webkitGetAsEntry?.())
    .filter((e): e is FileSystemEntry => e != null);
  if (entries.length === 0) return Array.from(event.dataTransfer.files);

  async function readEntry(entry: FileSystemEntry): Promise<File[]> {
    if (entry.isFile) {
      return new Promise((resolve) => (entry as FileSystemFileEntry).file((f) => resolve([f]), () => resolve([])));
    }
    if (entry.isDirectory) {
      const reader = (entry as FileSystemDirectoryEntry).createReader();
      const all: FileSystemEntry[] = [];
      await new Promise<void>((resolve) => {
        const next = () => reader.readEntries((batch) => { if (batch.length === 0) { resolve(); } else { all.push(...batch); next(); } }, () => resolve());
        next();
      });
      return (await Promise.all(all.map(readEntry))).flat();
    }
    return [];
  }
  return (await Promise.all(entries.map(readEntry))).flat();
}

function App() {
  const [view, setView] = useState<'home' | 'workspace'>('home');
  const [dashboard, setDashboard] = useState<ProjectDashboard | null>(null);
  const [activeProjectRecord, setActiveProjectRecord] = useState<ProjectRecord | null>(null);
  const [projectForm, setProjectForm] = useState<CreateProjectPayload>({ projectName: '', projectType: 'wind-power', owner: '', location: '' });
  const [project, setProject] = useState<DemoProject | null>(null);
  const [workspace, setWorkspace] = useState<WorkspaceState | null>(null);
  const [ollama, setOllama] = useState<OllamaStatus | null>(null);
  const [selectedStep, setSelectedStep] = useState('step1');
  const [standards, setStandards] = useState<LandUseStandard[]>([]);
  const [standardMatches, setStandardMatches] = useState<LandUseStandardMatch[]>([]);
  const [standardQuery, setStandardQuery] = useState('');
  const [report, setReport] = useState<ReportResponse | null>(null);
  const [reportDraft, setReportDraft] = useState(() => localStorage.getItem(REPORT_DRAFT_KEY) ?? '');
  const [previewOpen, setPreviewOpen] = useState(false);
  const [loading, setLoading] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [dragTarget, setDragTarget] = useState('');
  const [uploadProgress, setUploadProgress] = useState<{ current: number; total: number; fileName: string } | null>(null);
  const [synthesisResult, setSynthesisResult] = useState<SynthesizeResponse | null>(null);
  const [aiConfig, setAiConfig] = useState<AiConfig>(() => readAiConfig());
  const policyInputRef = useRef<HTMLInputElement | null>(null);
  const bulkInputRef = useRef<HTMLInputElement | null>(null);
  const lastFilesRef = useRef<File[]>([]);

  useEffect(() => {
    void refreshAll();
  }, []);

  useEffect(() => {
    setReportDraft(localStorage.getItem(reportDraftKey(activeProjectRecord?.id)) ?? '');
    setReport(null);
    setPreviewOpen(false);
  }, [activeProjectRecord?.id]);

  const activeStep = useMemo(
    () => project?.steps.find((step) => step.id === selectedStep) ?? project?.steps[0],
    [project, selectedStep]
  );
  const activeWorkspace = workspace?.steps[selectedStep];
  const activeMaterials = useMemo(() => buildStepMaterials(selectedStep, MATERIAL_SPECS[selectedStep] ?? [], standardMatches), [selectedStep, standardMatches]);
  const activeCases = CASE_GROUPS[selectedStep] ?? [];
  const activeAnalyses = activeWorkspace?.analyses ?? [];
  const allAnalyses = useMemo(() => Object.values(workspace?.steps ?? {}).flatMap((step) => step.analyses), [workspace]);
  const validationRows = useMemo(() => addStandardMatchRows(buildValidationRows(activeWorkspace, activeMaterials, activeCases), selectedStep, standardMatches), [activeWorkspace, activeMaterials, activeCases, selectedStep, standardMatches]);
  const statusCounts = useMemo(() => countValidation(validationRows), [validationRows]);

  useEffect(() => {
    if (selectedStep === 'step6' && activeProjectRecord) {
      void loadStandardsForActiveProject(standardQuery, true);
    }
  }, [selectedStep, activeProjectRecord?.id]);

  async function refreshAll() {
    setError('');
    try {
      const activeProjectId = localStorage.getItem(ACTIVE_PROJECT_KEY);
      const [dashboardData, projectData, ollamaStatus, stateData] = await Promise.all([fetchProjectDashboard(), fetchProject(), fetchOllamaStatus(), fetchWorkspaceState(activeProjectId)]);
      setDashboard(dashboardData);
      setProject(projectData);
      setOllama(ollamaStatus);
      setWorkspace(stateData);
      const active = dashboardData.projects.find((item) => item.id === activeProjectId) ?? null;
      setActiveProjectRecord(active);
    } catch (err) {
      setError(err instanceof Error ? err.message : '后端暂时不可用');
    }
  }

  async function reloadDashboard(nextActiveId?: string) {
    const dashboardData = await fetchProjectDashboard();
    setDashboard(dashboardData);
    if (nextActiveId) {
      setActiveProjectRecord(dashboardData.projects.find((item) => item.id === nextActiveId) ?? null);
    }
  }

  async function handleCreateProject() {
    if (!projectForm.projectName.trim()) {
      setError('请先填写项目名称。');
      return;
    }
    setLoading('create-project');
    setError('');
    try {
      const created = await createProject(projectForm);
      localStorage.setItem(ACTIVE_PROJECT_KEY, created.id);
      setActiveProjectRecord(created);
      setWorkspace(await fetchWorkspaceState(created.id));
      setView('workspace');
      setProjectForm({ projectName: '', projectType: created.projectType, owner: '', location: '' });
      await reloadDashboard(created.id);
      setNotice(`项目 ${created.projectCode} 已创建并落库。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '项目创建失败');
    } finally {
      setLoading('');
    }
  }

  async function openProject(record: ProjectRecord) {
    localStorage.setItem(ACTIVE_PROJECT_KEY, record.id);
    setActiveProjectRecord(record);
    setWorkspace(await fetchWorkspaceState(record.id));
    setView('workspace');
    setNotice(`已打开项目：${record.projectName}`);
  }

  async function loadStandardsForActiveProject(query = standardQuery, ensureWorkspaceWriteback = false) {
    if (!activeProjectRecord) return;
    setLoading((current) => current || 'standards');
    try {
      const [standardData, fetchedMatches] = await Promise.all([
        fetchLandStandards(activeProjectRecord.projectType, query, 30),
        fetchStandardMatches(activeProjectRecord.id)
      ]);
      let matchData = fetchedMatches;
      const step6Fields = workspace?.steps.step6?.fields ?? [];
      const needsWriteback = ensureWorkspaceWriteback && !query.trim() && !step6Fields.some((field) => field.key.startsWith('standardReview.'));
      if (needsWriteback) {
        matchData = await refreshStandardMatches(activeProjectRecord.id);
        setWorkspace(await fetchWorkspaceState(activeProjectRecord.id));
      }
      setStandards(standardData);
      setStandardMatches(matchData);
    } catch (err) {
      setError(err instanceof Error ? err.message : '标准库加载失败');
    } finally {
      setLoading((current) => current === 'standards' ? '' : current);
    }
  }

  async function refreshProjectStandards() {
    if (!activeProjectRecord) return;
    setLoading('standard-match');
    setError('');
    try {
      await refreshStandardReview(activeProjectRecord.id);
      setNotice('已根据当前项目类型刷新并落库标准匹配结果。');
    } catch (err) {
      setError(err instanceof Error ? err.message : '标准匹配失败');
    } finally {
      setLoading('');
    }
  }

  async function refreshStandardReview(projectId: string) {
    const matches = await refreshStandardMatches(projectId);
    setStandardMatches(matches);
    const state = await fetchWorkspaceState(projectId);
    setWorkspace(state);
    return { matches, state };
  }

  function saveAiConfig() {
    localStorage.setItem(AI_CONFIG_KEY, JSON.stringify(aiConfig));
    const parts: string[] = [];
    if (aiConfig.provider === 'deepseek') parts.push('DeepSeek');
    else if (aiConfig.provider === 'ollama') parts.push('Ollama');
    if (aiConfig.doubaoApiKey?.trim()) parts.push('豆包视觉');
    setNotice((parts.join(' + ') || 'AI') + ' 配置已保存到本机浏览器。');
  }

  async function handlePolicyCard(files: FileList | File[]) {
    const file = Array.from(files)[0];
    if (!file) return;
    setLoading('policy');
    setError('');
    try {
      const result = await uploadPolicyCard(file, activeProjectRecord?.id);
      setWorkspace(await fetchWorkspaceState(activeProjectRecord?.id));
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
    setUploadProgress({ current: 0, total: list.length, fileName: '' });
    try {
      const CONCURRENCY = 20;
      const results: (AnalysisResponse | null)[] = new Array(list.length).fill(null);
      const failed: string[] = [];
      let completed = 0;

      const pool = list.map((file, i) => async () => {
        try {
          results[i] = await analyzeDocument(file, '', aiConfig, selectedStep, activeProjectRecord?.id);
        } catch {
          failed.push(file.name);
        } finally {
          completed++;
          setUploadProgress({ current: completed, total: list.length, fileName: file.name });
        }
      });

      const worker = async () => { while (pool.length > 0) await pool.shift()!(); };
      await Promise.all(Array.from({ length: Math.min(CONCURRENCY, pool.length) }, worker));

      const valid = results.filter((r): r is AnalysisResponse => r !== null);
      let nextWorkspace = await fetchWorkspaceState(activeProjectRecord?.id);
      if (activeProjectRecord && (selectedStep === 'step6' || valid.some((r) => r.targetStep === 'step6'))) {
        nextWorkspace = (await refreshStandardReview(activeProjectRecord.id)).state;
      } else {
        setWorkspace(nextWorkspace);
      }
      const summary = `已解析 ${valid.length} 份材料，并自动归档：${summarizeRouting(valid)}${failed.length > 0 ? `；${failed.length} 份失败：${failed.slice(0, 3).join('、')}${failed.length > 3 ? ' 等' : ''}` : ''}。`;
      if (failed.length > 0 && valid.length === 0) setError(summary); else setNotice(summary);
      const firstStep = valid.find((r) => r.targetStep)?.targetStep;
      if (firstStep) setSelectedStep(firstStep);
      const hasVisual = valid.some((r) => r.visualContent);
      const hasText = valid.some((r) => !r.visualContent && r.extractedFields.length > 0);
      if (valid.length > 0 && (hasVisual || hasText)) {
        try {
          const synthesis = await synthesizeAnalyses(valid, aiConfig);
          setSynthesisResult(synthesis);
        } catch {
          // 综合分析失败不阻断主流程
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '批量解析失败');
    } finally {
      setLoading('');
      setDragTarget('');
      setUploadProgress(null);
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
      const updated = await updateField(field.stepId, field.key, value, 'warn', activeProjectRecord?.id);
      setWorkspace(updated);
      if (activeProjectRecord && (field.stepId === 'step6' || selectedStep === 'step6')) {
        await refreshStandardReview(activeProjectRecord.id);
      }
      setNotice(`${field.label} 已保存并触发实时校验。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '字段保存失败');
    }
  }

  async function selectCase(groupId: string, value: string) {
    setWorkspace((current) => patchSituation(current, selectedStep, groupId, value));
    try {
      setWorkspace(await updateSituation(selectedStep, groupId, value, activeProjectRecord?.id));
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
      setWorkspace(await withdrawDocument(fileId, activeProjectRecord?.id));
      setNotice('已撤回该上传文件，并同步刷新字段、材料清单和校验结果。');
    } catch (err) {
      setError(err instanceof Error ? err.message : '材料撤回失败');
    } finally {
      setLoading('');
    }
  }

  function saveDraft() {
    localStorage.setItem(AI_CONFIG_KEY, JSON.stringify(aiConfig));
    localStorage.setItem(reportDraftKey(activeProjectRecord?.id), reportDraft);
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
      localStorage.setItem(reportDraftKey(activeProjectRecord?.id), nextReport.markdown);
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
        localStorage.setItem(reportDraftKey(activeProjectRecord?.id), markdown);
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

  if (view === 'home') {
    return (
      <main className="app-shell">
        <HomeDashboard
          dashboard={dashboard}
          form={projectForm}
          loading={loading === 'create-project'}
          error={error}
          notice={notice}
          onFormChange={setProjectForm}
          onCreate={() => void handleCreateProject()}
          onOpen={(record) => void openProject(record)}
          onRefresh={() => void refreshAll()}
        />
      </main>
    );
  }

  return (
    <main className="app-shell">
      <header className="top-bar">
        <div className="brand-block">
          <div className="brand-icon"><ShieldCheck size={21} /></div>
          <div>
            <h1>建设用地报批审查报告智能生成系统</h1>
            <p>{activeProjectRecord?.projectName ?? project?.projectName ?? '未选择项目'} · {activeProjectRecord?.projectCode ?? '项目未落库'} · 一次导入 · 自动归档 · 实时校验</p>
          </div>
        </div>
        <div className="top-meta">
          <button className="btn btn-outline" type="button" onClick={() => setView('home')}><Home size={16} />首页</button>
          <StatusPill ollama={ollama} />
          <span className="project-badge">{activeProjectRecord?.projectTypeLabel ?? '单独选址建设项目'}</span>
        </div>
      </header>

      <StepIndicator project={project} workspace={workspace} selectedStep={selectedStep} onSelect={setSelectedStep} />

      {(error || notice) && <div className={error ? 'message-strip error-strip' : 'message-strip notice-strip'}>{error ? <AlertTriangle size={18} /> : <CheckCircle2 size={18} />}{error || notice}</div>}

      <section className="main-container">
        <aside className="upload-panel">
          <section className="card ai-card">
            <div className="card-header"><KeyRound size={17} />AI 配置</div>
            <div className="card-body compact-body">
              <div className="ai-config-label">文字分析模型</div>
              <div className="segmented">
                <button className={aiConfig.provider === 'deepseek' ? 'active' : ''} type="button" onClick={() => setAiConfig({ ...aiConfig, provider: 'deepseek' })}>DeepSeek</button>
                <button className={aiConfig.provider === 'ollama' ? 'active' : ''} type="button" onClick={() => setAiConfig({ ...aiConfig, provider: 'ollama' })}>Ollama</button>
              </div>
              <input className="config-input" type="password" placeholder="DeepSeek API Key" value={aiConfig.deepseekApiKey} onChange={(event) => setAiConfig({ ...aiConfig, deepseekApiKey: event.target.value })} />
              <input className="config-input" type="text" value={aiConfig.deepseekModel} onChange={(event) => setAiConfig({ ...aiConfig, deepseekModel: event.target.value })} />
              <div className="ai-config-divider" />
              <div className="ai-config-label">视觉分析（图片/扫描件）— 豆包</div>
              <input className="config-input" type="password" placeholder="豆包 API Key" value={aiConfig.doubaoApiKey} onChange={(event) => setAiConfig({ ...aiConfig, doubaoApiKey: event.target.value })} />
              <input className="config-input" type="text" placeholder="豆包 Endpoint ID（ep-xxx）" value={aiConfig.doubaoEndpoint} onChange={(event) => setAiConfig({ ...aiConfig, doubaoEndpoint: event.target.value })} />
              <button className="btn btn-outline full" type="button" onClick={saveAiConfig}><Save size={16} />保存配置</button>
            </div>
          </section>

          <UploadDropZone title="政策明白卡" subtitle={activeWorkspace?.policyCardFileName ?? '根目录明白卡'} loading={loading === 'policy'} dragging={dragTarget === 'policy'} onPick={() => policyInputRef.current?.click()} onFiles={handlePolicyCard} onDrag={(value) => setDragTarget(value ? 'policy' : '')} />
          <input ref={policyInputRef} className="hidden-input" type="file" accept=".pdf,.doc,.docx,.txt,.md" onChange={(event) => { void handlePolicyCard(event.target.files ?? []); event.currentTarget.value = ''; }} />

          <UploadDropZone title="一把导入全部材料" subtitle="把 PDF、Word、图片、txt 全部拖进来，系统按文件名和内容归档到八步" loading={loading === 'bulk'} dragging={dragTarget === 'bulk'} progress={uploadProgress} onPick={() => bulkInputRef.current?.click()} onFiles={handleBulkFiles} onDrag={(value) => setDragTarget(value ? 'bulk' : '')} />
          <input ref={bulkInputRef} className="hidden-input" type="file" multiple accept=".pdf,.doc,.docx,.txt,.md,.html,.png,.jpg,.jpeg" onChange={(event) => { void handleBulkFiles(event.target.files); event.currentTarget.value = ''; }} />

          <section className="card material-ledger">
            <div className="card-header"><FolderOpen size={17} />本步骤材料清单</div>
            <div className="card-body">
              <MaterialGroup title="必传文件" specs={activeMaterials.filter((item) => !item.condition && isMaterialRequired(item, activeWorkspace))} analyses={activeAnalyses} workspaceStep={activeWorkspace} onWithdraw={withdrawUploadedFile} />
              <MaterialGroup title="条件必传文件" specs={activeMaterials.filter((item) => item.condition && isMaterialRequired(item, activeWorkspace))} analyses={activeAnalyses} workspaceStep={activeWorkspace} onWithdraw={withdrawUploadedFile} />
              <MaterialGroup title="条件必传（当前无需上传）" specs={activeMaterials.filter((item) => isMaterialSkipped(item, activeWorkspace))} analyses={activeAnalyses} workspaceStep={activeWorkspace} onWithdraw={withdrawUploadedFile} />
              <MaterialGroup title="选传/条件选传文件" specs={activeMaterials.filter((item) => !isMaterialRequired(item, activeWorkspace) && !isMaterialSkipped(item, activeWorkspace))} analyses={activeAnalyses} workspaceStep={activeWorkspace} onWithdraw={withdrawUploadedFile} />
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

          <ParsedFileCards analyses={activeAnalyses} synthesisResult={synthesisResult} onWithdraw={withdrawUploadedFile} />

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

          {selectedStep === 'step6' && (
            <LandStandardPanel
              project={activeProjectRecord}
              standards={standards}
              matches={standardMatches}
              query={standardQuery}
              loading={loading === 'standards' || loading === 'standard-match'}
              onQueryChange={setStandardQuery}
              onSearch={() => void loadStandardsForActiveProject()}
              onRefreshMatch={() => void refreshProjectStandards()}
            />
          )}

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

const STEP_LABELS: Record<string, string> = { step1: '项目基本情况', step2: '申请用地现状', step3: '农用地转用', step4: '补充耕地', step5: '土地征收', step6: '土地利用', step7: '地灾压矿', step8: '信访违法' };

function HomeDashboard({ dashboard, form, loading, error, notice, onFormChange, onCreate, onOpen, onRefresh }: {
  dashboard: ProjectDashboard | null;
  form: CreateProjectPayload;
  loading: boolean;
  error: string;
  notice: string;
  onFormChange: (form: CreateProjectPayload) => void;
  onCreate: () => void;
  onOpen: (project: ProjectRecord) => void;
  onRefresh: () => void;
}) {
  const [previewId, setPreviewId] = useState<string | null>(null);
  const [previewWorkspace, setPreviewWorkspace] = useState<WorkspaceState | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  const projectTypes = dashboard?.projectTypes ?? [];
  const projects = dashboard?.projects ?? [];
  const standardCount = (dashboard?.standardSummaries ?? []).reduce((sum, item) => sum + item.count, 0);
  const previewProject = projects.find((item) => item.id === previewId) ?? null;

  async function togglePreview(project: ProjectRecord) {
    if (previewId === project.id) {
      setPreviewId(null);
      setPreviewWorkspace(null);
      return;
    }
    setPreviewId(project.id);
    setPreviewWorkspace(null);
    setPreviewLoading(true);
    try {
      setPreviewWorkspace(await fetchWorkspaceState(project.id));
    } finally {
      setPreviewLoading(false);
    }
  }

  return (
    <div className="home-shell">
      <header className="home-header">
        <div className="brand-block">
          <div className="brand-icon"><ShieldCheck size={22} /></div>
          <div>
            <h1>建设用地报批审查报告智能生成系统</h1>
            <p>项目建档 · 标准库落库 · 材料解析 · 八步审查</p>
          </div>
        </div>
        <button className="btn btn-outline" type="button" onClick={onRefresh}><RefreshCw size={16} />刷新</button>
      </header>

      {(error || notice) && <div className={error ? 'message-strip error-strip' : 'message-strip notice-strip'}>{error ? <AlertTriangle size={18} /> : <CheckCircle2 size={18} />}{error || notice}</div>}

      <section className="home-grid">
        <section className="home-panel create-project-panel">
          <div className="panel-title"><Plus size={18} />新建项目草稿</div>
          <div className="home-form-grid">
            <label>项目名称<input value={form.projectName} onChange={(event) => onFormChange({ ...form, projectName: event.target.value })} placeholder="例如：兴宁五塘风电场一期工程" /></label>
            <label>项目类型<select value={form.projectType} onChange={(event) => onFormChange({ ...form, projectType: event.target.value })}>{projectTypes.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}</select></label>
            <label>建设单位<input value={form.owner} onChange={(event) => onFormChange({ ...form, owner: event.target.value })} placeholder="建设单位/业主单位" /></label>
            <label>建设地点<input value={form.location} onChange={(event) => onFormChange({ ...form, location: event.target.value })} placeholder="市、县、乡镇或具体位置" /></label>
          </div>
          <button className="btn btn-primary create-btn" type="button" onClick={onCreate} disabled={loading}>{loading ? <Loader2 className="spin" size={18} /> : <Plus size={18} />}创建项目并进入工作台</button>
        </section>

        <section className="home-panel stats-panel">
          <div className="panel-title"><Database size={18} />系统库状态</div>
          <div className="metric-grid">
            <div className="metric-cell"><strong>{projects.length}</strong><span>落库项目</span></div>
            <div className="metric-cell"><strong>{standardCount}</strong><span>用地标准条目</span></div>
            <div className="metric-cell"><strong>{dashboard?.standardSummaries.length ?? 0}</strong><span>项目类型</span></div>
          </div>
          <div className="standard-summary-list">
            {(dashboard?.standardSummaries ?? []).map((item) => <span key={item.projectType}>{item.projectTypeLabel}：{item.count}</span>)}
          </div>
        </section>
      </section>

      <section className="home-panel project-list-panel">
        <div className="panel-title"><FolderOpen size={18} />草稿箱 / 历史项目</div>
        {projects.length === 0 ? <p className="quiet">还没有落库项目，先从上方创建一个项目草稿。</p> : (
          <div className="project-table-wrap">
            <table className="project-table">
              <thead><tr><th>系统编号</th><th>项目名称</th><th>项目类型</th><th>建设单位</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>
              <tbody>{projects.map((item) => (
                <tr key={item.id} className={previewId === item.id ? 'project-row-active' : ''}>
                  <td>{item.projectCode}</td>
                  <td><strong>{item.projectName}</strong><div className="subtle-text">{item.location || '未填写地点'}</div></td>
                  <td>{item.projectTypeLabel}</td>
                  <td>{item.owner || '未填写'}</td>
                  <td><StatusBadge status={item.status === '草稿' ? 'todo' : 'pass'} /></td>
                  <td>{formatTime(item.updatedAt)}</td>
                  <td className="project-actions-cell">
                    <button className={`modify-btn ${previewId === item.id ? 'active' : ''}`} type="button" onClick={() => void togglePreview(item)}><FileSearch size={13} />{previewId === item.id ? '收起' : '查看'}</button>
                    <button className="modify-btn open-btn" type="button" onClick={() => onOpen(item)}><FolderOpen size={13} />工作台</button>
                  </td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </section>

      {previewProject && (
        <section className="home-panel project-preview-panel">
          <div className="panel-title">
            <FileSearch size={18} />项目分析摘要 — {previewProject.projectName}
            <span className="preview-badge">{previewProject.projectCode}</span>
            <button className="btn btn-primary preview-enter-btn" type="button" onClick={() => onOpen(previewProject)}><FolderOpen size={15} />进入工作台</button>
          </div>
          {previewLoading ? (
            <div className="preview-loading"><Loader2 className="spin" size={20} />正在加载项目分析数据…</div>
          ) : previewWorkspace ? (
            <ProjectPreviewContent workspace={previewWorkspace} project={previewProject} />
          ) : (
            <p className="quiet">加载失败，请重试。</p>
          )}
        </section>
      )}
    </div>
  );
}

function ProjectPreviewContent({ workspace, project }: { workspace: WorkspaceState; project: ProjectRecord }) {
  const stepIds = ['step1', 'step2', 'step3', 'step4', 'step5', 'step6', 'step7', 'step8'];
  const totalAnalyses = stepIds.reduce((sum, id) => sum + (workspace.steps[id]?.analyses.length ?? 0), 0);
  const stepsWithContent = stepIds.filter((id) => (workspace.steps[id]?.analyses.length ?? 0) > 0);
  const allFields = stepIds.flatMap((id) => workspace.steps[id]?.fields ?? []).filter((field) => field.value && !field.key.startsWith('standardReview.'));

  return (
    <div className="project-preview-content">
      <div className="preview-meta-row">
        <span><strong>项目类型：</strong>{project.projectTypeLabel}</span>
        <span><strong>建设单位：</strong>{project.owner || '未填写'}</span>
        <span><strong>建设地点：</strong>{project.location || '未填写'}</span>
        <span><strong>项目状态：</strong>{project.status}</span>
      </div>

      <div className="preview-step-grid">
        {stepIds.map((id) => {
          const count = workspace.steps[id]?.analyses.length ?? 0;
          return (
            <div key={id} className={`preview-step-cell ${count > 0 ? 'has-content' : 'no-content'}`}>
              <span className="preview-step-num">{id.replace('step', '')}</span>
              <span className="preview-step-name">{STEP_LABELS[id]}</span>
              <span className="preview-step-count">{count > 0 ? `${count} 份材料` : '未上传'}</span>
            </div>
          );
        })}
      </div>

      <div className="preview-summary-row">
        <span>共上传 <strong>{totalAnalyses}</strong> 份材料，覆盖 <strong>{stepsWithContent.length}</strong>/8 个审查步骤</span>
        {stepsWithContent.length === 0 && <span className="quiet-inline">（本项目尚未上传任何材料，请进入工作台开始分析）</span>}
      </div>

      {allFields.length > 0 && (
        <div className="preview-fields-section">
          <div className="preview-section-title">已抽取的关键字段</div>
          <div className="preview-fields-grid">
            {allFields.slice(0, 12).map((field) => (
              <div key={`${field.stepId}-${field.key}`} className="preview-field-item">
                <span className="preview-field-label">{field.label}</span>
                <span className="preview-field-value">{field.value}</span>
              </div>
            ))}
            {allFields.length > 12 && <div className="preview-field-more">…还有 {allFields.length - 12} 个字段，进入工作台查看</div>}
          </div>
        </div>
      )}
    </div>
  );
}

function LandStandardPanel({ project, standards, matches, query, loading, onQueryChange, onSearch, onRefreshMatch }: {
  project: ProjectRecord | null;
  standards: LandUseStandard[];
  matches: LandUseStandardMatch[];
  query: string;
  loading: boolean;
  onQueryChange: (value: string) => void;
  onSearch: () => void;
  onRefreshMatch: () => void;
}) {
  const matchByStandardId = new Map(matches.map((match) => [match.standardId, match]));
  return (
    <section className="preview-section standard-panel">
      <div className="standard-panel-head">
        <h3><span className="dot dot-blue" />用地标准库匹配</h3>
        <div className="standard-actions">
          <div className="search-box"><Search size={15} /><input value={query} onChange={(event) => onQueryChange(event.target.value)} placeholder="搜索表名、章节、指标关键词" /></div>
          <button className="btn btn-outline" type="button" onClick={onSearch} disabled={loading}>{loading ? <Loader2 className="spin" size={16} /> : <Search size={16} />}查询</button>
          <button className="btn btn-primary" type="button" onClick={onRefreshMatch} disabled={!project || loading}>{loading ? <Loader2 className="spin" size={16} /> : <Database size={16} />}刷新并落库</button>
        </div>
      </div>
      {!project ? <p className="quiet">请先回首页创建或打开项目，系统会按项目类型匹配标准库。</p> : (
        <>
          <div className="standard-context">
            <span>当前项目类型：{project.projectTypeLabel}</span>
            <span>已查到标准条目：{standards.length}</span>
            <span>已落库匹配：{matches.length}</span>
            {matches.length === 0 && !loading && <span className="standard-hint">点击"刷新并落库"自动按项目类型匹配标准指标，匹配后结果持久保存。</span>}
          </div>
          {matches.length === 0 && !loading && standards.length > 0 && (
            <div className="standard-empty-prompt">
              <Database size={20} />
              <div>
                <strong>标准库已就绪（{standards.length} 条），尚未为本项目生成匹配结论。</strong>
                <span>点击右上方"刷新并落库"按钮，系统会自动按项目类型、申报面积、装机容量等参数匹配用地指标，并写入第六步校验结果。</span>
              </div>
            </div>
          )}
          {matches.length > 0 && <div className="match-list">{matches.map((match) => {
            const status = standardMatchStatus(match.matchStatus);
            const groupedFields = groupMatchedFields(match.matchedFields);
            return <div className="match-item" key={match.id}>
              <StatusBadge status={status} />
              <div className="match-main">
                <div className="match-title-row"><strong>{match.standard.chapterTitle}</strong><span>{standardMatchLabel(match)}</span></div>
                <p>{match.conclusion}</p>
                <div className="match-field-groups">
                  {groupedFields.project.length > 0 && <ChipGroup title="项目识别参数" items={groupedFields.project} />}
                  {groupedFields.standard.length > 0 && <ChipGroup title="标准库指标" items={groupedFields.standard} />}
                  {groupedFields.missing.length > 0 && <ChipGroup title="待补项目参数" items={groupedFields.missing} />}
                </div>
              </div>
            </div>;
          })}</div>}
          <div className="standard-list">
            {standards.length === 0 ? <p className="quiet">当前项目类型暂无标准条目，或搜索条件过窄。</p> : standards.map((standard) => {
              const match = matchByStandardId.get(standard.id);
              return <article className="standard-card" key={standard.id}>
              <div className="standard-card-title">
                <div className="standard-card-heading"><strong>{standard.chapterTitle}</strong>{match && <StatusBadge status={standardMatchStatus(match.matchStatus)} />}</div>
                <span>{standard.sourceFile}</span>
              </div>
              <p>{formatStandardContent(standard.content)}</p>
            </article>;
            })}
          </div>
        </>
      )}
    </section>
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

function UploadDropZone({ title, subtitle, loading, dragging, progress, onPick, onFiles, onDrag }: { title: string; subtitle: string; loading: boolean; dragging: boolean; progress?: { current: number; total: number; fileName: string } | null; onPick: () => void; onFiles: (files: FileList | File[]) => void; onDrag: (value: boolean) => void }) {
  const pct = progress ? Math.round((progress.current / progress.total) * 100) : 0;
  return (
    <section className={dragging ? 'upload-dropzone dragging' : 'upload-dropzone'} onDragOver={(event) => { event.preventDefault(); onDrag(true); }} onDragLeave={() => onDrag(false)} onDrop={(event) => { event.preventDefault(); onDrag(false); void collectDropFiles(event).then(onFiles); }}>
      <FileUp size={24} />
      <div><strong>{title}</strong><span>{subtitle}</span></div>
      <button className="btn btn-outline full" type="button" onClick={onPick} disabled={loading}>{loading ? <Loader2 className="spin" size={16} /> : <FileSearch size={16} />}选择/拖入</button>
      {progress && (
        <div className="upload-progress">
          <div className="upload-progress-bar" style={{ width: `${pct}%` }} />
          <span className="upload-progress-label">{progress.current} / {progress.total} — {progress.fileName}</span>
        </div>
      )}
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
        {uploaded ? matches.map((analysis) => <span className="source-file-actions" key={analysis.fileId || analysis.fileName}><button className="source-link" type="button" onClick={() => openSourceFile(analysis.fileId)}>{safeFileName(analysis.fileName)}</button><button className="withdraw-btn" type="button" onClick={() => onWithdraw(analysis.fileId)} title="撤回该材料"><Undo2 size={13} />撤回</button></span>) : <span className="upload-hint">{skipped ? '当前项目情形无需上传' : required ? '尚未识别到该必传材料' : '项目涉及时上传'}</span>}
        <StatusBadge status={uploaded || skipped ? 'pass' : required ? 'block' : 'todo'} />
      </div>
    </div>
  );
}

function ChipGroup({ title, items }: { title: string; items: string[] }) {
  return <div className="match-chip-group"><span className="match-chip-title">{title}</span><div className="match-fields">{items.map((item) => <span key={item}>{item}</span>)}</div></div>;
}

function ParsedFileCards({ analyses, synthesisResult, onWithdraw }: { analyses: AnalysisResponse[]; synthesisResult: SynthesizeResponse | null; onWithdraw: (fileId?: string | null) => void }) {
  return (
    <section className="preview-section">
      <h3><span className="dot dot-blue" />自动归档文件解析</h3>
      {analyses.length === 0 ? <p className="quiet">把材料一股脑拖进左侧上传框后，每个文件会在这里单独生成一个解析卡片。</p> : (
        <div className="parsed-stack">
          {analyses.map((analysis) => <article className="parsed-card" key={analysis.fileId || `${analysis.fileName}-${analysis.size}`}>
            <div className="parsed-card-head"><div><button className="file-title-link" type="button" onClick={() => openSourceFile(analysis.fileId)}>{safeFileName(analysis.fileName)}</button><p>{analysis.detectedDocumentType} · 自动归档到 {analysis.targetStep}{analysis.visualContent ? ' · 视觉材料' : ''}</p></div><div className="parsed-card-actions"><StatusBadge status={analysis.extractedFields.length > 0 ? 'pass' : 'warn'} /><button className="withdraw-btn" type="button" onClick={() => onWithdraw(analysis.fileId)} title="撤回该文件"><Undo2 size={13} />撤回</button></div></div>
            {analysis.thumbnailBase64 && (
              <img className="visual-thumbnail" src={`data:image/jpeg;base64,${analysis.thumbnailBase64}`} alt={safeFileName(analysis.fileName)} />
            )}
            <div className="parsed-metrics"><span>文本 {analysis.textLength} 字</span><span>字段 {analysis.extractedFields.length} 个</span><span>校验 {analysis.policyChecks.length} 条</span><span>{analysis.aiAdvice.provider} · {analysis.aiAdvice.model}</span></div>
            <p className="ai-summary"><Bot size={15} />{analysis.aiAdvice.summary}</p>
            {analysis.doubaoAnalysis && (
              <details className="doubao-panel">
                <summary><span className="doubao-badge">豆包视觉</span>识别结果（点击展开）</summary>
                <pre>{analysis.doubaoAnalysis}</pre>
              </details>
            )}
            <AiAdvicePanel advice={analysis.aiAdvice} />
          </article>)}
        </div>
      )}
      {synthesisResult && synthesisResult.provider !== 'none' && (
        <div className="synthesis-panel">
          <div className="synthesis-header"><Bot size={16} />AI综合分析 <span className="synthesis-provider">{synthesisResult.provider}</span></div>
          <p className="synthesis-summary">{synthesisResult.summary}</p>
          {synthesisResult.rawText && (
            <details className="ai-raw">
              <summary>完整综合建议（字段 + 情形 + 摘要）</summary>
              <pre>{synthesisResult.rawText}</pre>
            </details>
          )}
        </div>
      )}
    </section>
  );
}

function AiAdvicePanel({ advice }: { advice: AnalysisResponse['aiAdvice'] }) {
  const groups = [
    { title: '可改', items: advice.editablePoints },
    { title: '待补', items: advice.missingMaterials },
    { title: '风险', items: advice.riskPoints }
  ].filter((group) => group.items.length > 0);
  return <div className="ai-advice-panel">
    {groups.length > 0 && <div className="ai-advice-lists">{groups.map((group) => <div className="ai-advice-list" key={group.title}><strong>{group.title}</strong>{group.items.map((item) => <span key={item}>{item}</span>)}</div>)}</div>}
    {advice.rawText && <details className="ai-raw" open><summary>AI完整审查意见</summary><pre>{advice.rawText}</pre></details>}
  </div>;
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
  const fallback: AiConfig = { provider: 'deepseek', deepseekApiKey: '', deepseekModel: 'deepseek-chat', doubaoApiKey: '', doubaoEndpoint: '' };
  try {
    return { ...fallback, ...JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || '{}') };
  } catch {
    return fallback;
  }
}

function reportDraftKey(projectId?: string | null) {
  return projectId ? `${REPORT_DRAFT_KEY}:${projectId}` : REPORT_DRAFT_KEY;
}

function spec(id: string, label: string, required: boolean, sub: string, keywords: string[], condition?: MaterialSpec['condition']): MaterialSpec {
  return { id, label, required, sub, keywords, condition };
}

function group(id: string, title: string, labels: string[]): CaseGroup {
  return { id, title, options: labels.map((label, index) => ({ value: String(index + 1), label })) };
}

function matchesMaterial(analysis: AnalysisResponse, material: MaterialSpec) {
  const fieldText = analysis.extractedFields.map((field) => `${field.label} ${field.value}`).join(' ');
  const haystack = `${analysis.fileName} ${analysis.detectedDocumentType} ${analysis.textPreview} ${fieldText}`.toLowerCase();
  return material.keywords.some((keyword) => haystack.includes(keyword.toLowerCase()));
}

function buildStepMaterials(stepId: string, baseMaterials: MaterialSpec[], matches: LandUseStandardMatch[]) {
  if (stepId !== 'step6' || matches.length === 0) return baseMaterials;
  const text = matches.map((match) => `${match.standard.chapterTitle} ${match.standard.content} ${match.matchedFields} ${match.conclusion}`).join(' ');
  const dynamic: MaterialSpec[] = [
    spec('standard-scale', '建设规模及功能分区说明', true, '标准库触发：支撑指标适用范围、分项面积和计算口径', ['建设规模', '功能分区', '主要工程数量', '用地统计', '申报面积'])
  ];
  if (/线路|路线|正线|道路|路基|区间/.test(text)) {
    dynamic.push(spec('standard-line-length', '线路/道路长度测算表', true, '标准库触发：用于 hm²/km、桥隧比、路基长度等指标测算', ['线路长度', '正线长度', '路线全长', '线路全长', '建设长度', '道路长度', 'km', '公里']));
  }
  if (/桥梁|桥隧/.test(text)) {
    dynamic.push(spec('standard-bridge-length', '桥梁长度及桥隧比材料', true, '标准库触发：核对桥梁计算单量、正线长度和地形条件', ['桥梁长度', '桥梁总长', '桥隧比', '桥长']));
  }
  if (/隧道/.test(text)) {
    dynamic.push(spec('standard-tunnel-length', '隧道长度及地形条件材料', true, '标准库触发：核对隧道计算单量、正线长度和地形条件', ['隧道长度', '隧道总长', '隧长', '地形类型']));
  }
  if (/车站|中间站|站场/.test(text)) {
    dynamic.push(spec('standard-station-scale', '车站/站场规模说明', true, '标准库触发：核对站型、股道数和站场面积', ['车站', '站场', '中间站', '股道', '站型']));
  }
  return [...baseMaterials, ...dynamic.filter((item) => !baseMaterials.some((base) => base.id === item.id))];
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

function addStandardMatchRows(rows: ValidationRow[], selectedStep: string, matches: LandUseStandardMatch[]): ValidationRow[] {
  if (selectedStep !== 'step6' || matches.length === 0 || rows.some((row) => row.item.startsWith('标准复核：'))) {
    return rows;
  }
  return [
    ...rows,
    ...matches.map((match) => ({
      item: `标准复核：${match.standard.chapterTitle}`,
      status: standardMatchValidationStatus(match.matchStatus),
      detail: match.conclusion
    }))
  ];
}

function isMaterialRequired(material: MaterialSpec, step?: StepWorkspace) {
  return material.required && !isMaterialSkipped(material, step);
}

function isMaterialSkipped(material: MaterialSpec, step?: StepWorkspace) {
  if (!material.condition || !step) return false;
  if (material.condition === 'cultivatedLand') return !hasPositiveCondition(step, ['占用耕地', '耕地'], 'caseSupplement');
  if (material.condition === 'forest') return !hasPositiveCondition(step, ['林地'], 'forestryApproval');
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

function formatTime(value: string) {
  if (!value) return '未记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function normalizeStatus(status: string): StatusKey {
  if (status === 'pass' || status === 'warn' || status === 'block' || status === 'todo' || status === 'info') return status;
  return 'todo';
}

function standardMatchStatus(status: string): StatusKey {
  if (status.includes('通过')) return 'pass';
  if (status.includes('缺参数')) return 'todo';
  if (status.includes('未通过')) return 'block';
  return 'warn';
}

function standardMatchValidationStatus(status: string): StatusKey {
  if (status.includes('通过')) return 'pass';
  if (status.includes('未通过')) return 'block';
  return 'warn';
}

function standardMatchLabel(match: LandUseStandardMatch) {
  if (match.matchStatus.includes('通过')) return '项目测算通过';
  if (match.matchStatus.includes('缺参数')) return '标准库指标：待补项目参数';
  if (match.matchStatus.includes('需关注')) return '标准库指标：需复核适用性';
  return match.matchStatus;
}

function splitMatchedFields(value: string) {
  return value.split(';').map((item) => item.trim()).filter(Boolean);
}

function groupMatchedFields(value: string) {
  const grouped = { project: [] as string[], standard: [] as string[], missing: [] as string[] };
  for (const item of splitMatchedFields(value)) {
    if (item.startsWith('标准指标=')) {
      grouped.standard.push(item.replace(/^标准指标=/, ''));
    } else if (item.includes('缺少') || item.includes('暂未')) {
      grouped.missing.push(item);
    } else {
      grouped.project.push(item);
    }
  }
  return grouped;
}

function formatStandardContent(value: string) {
  return value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).join('\n');
}

function safeFileName(name: string): string {
  if (!name || name.includes('�')) return '[文件名无法显示，请重新上传]';
  return name;
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