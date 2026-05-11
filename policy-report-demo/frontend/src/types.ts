export type StepSummary = {
  id: string;
  title: string;
  goal: string;
  requiredMaterials: string[];
  ruleFocus: string[];
};

export type ReportSection = {
  title: string;
  content: string;
};

export type DemoProject = {
  projectName: string;
  projectCode: string;
  owner: string;
  location: string;
  baseFields: Record<string, string>;
  steps: StepSummary[];
  reportSections: ReportSection[];
};

export type ExtractedField = {
  label: string;
  value: string;
  source: string;
  confidence: number;
  sourceFileId: string | null;
};

export type PolicyCheck = {
  level: 'pass' | 'warn' | 'block' | 'info' | string;
  title: string;
  detail: string;
  source: string;
};

export type AiAdvice = {
  provider: string;
  model: string;
  usedOllama: boolean;
  summary: string;
  editablePoints: string[];
  missingMaterials: string[];
  riskPoints: string[];
  rawText: string;
};

export type AnalysisResponse = {
  fileId: string;
  fileName: string;
  contentType: string | null;
  size: number;
  detectedDocumentType: string;
  targetStep: string;
  textPreview: string;
  textLength: number;
  extractedFields: ExtractedField[];
  policyChecks: PolicyCheck[];
  aiAdvice: AiAdvice;
  visualContent?: boolean;
  thumbnailBase64?: string | null;
  doubaoAnalysis?: string | null;
};

export type ReportResponse = {
  title: string;
  markdown: string;
  highlights: string[];
};

export type OllamaStatus = {
  reachable: boolean;
  defaultModel: string;
  fallbackModel: string;
  installedModels: string[];
};

export type AiConfig = {
  provider: 'deepseek' | 'ollama' | 'rules';
  deepseekApiKey: string;
  deepseekModel: string;
  doubaoApiKey: string;
  doubaoEndpoint: string;
};

export type EditableField = {
  stepId: string;
  key: string;
  label: string;
  value: string;
  source: string;
  status: 'pass' | 'warn' | 'block' | 'todo' | string;
  required: boolean;
  sourceFileId: string | null;
};

export type StepChecklistItem = {
  id: string;
  title: string;
  status: 'pass' | 'warn' | 'block' | 'todo' | string;
  detail: string;
};

export type StepWorkspace = {
  stepId: string;
  fields: EditableField[];
  checklist: StepChecklistItem[];
  analyses: AnalysisResponse[];
  policyCardFileName: string;
  situations: Record<string, string>;
};

export type WorkspaceState = {
  projectName: string;
  steps: Record<string, StepWorkspace>;
};

export type PolicyCardUploadResponse = {
  fileName: string;
  textLength: number;
  message: string;
};

export type ProjectTypeOption = {
  value: string;
  label: string;
};

export type ProjectRecord = {
  id: string;
  projectCode: string;
  projectName: string;
  projectType: string;
  projectTypeLabel: string;
  owner: string;
  location: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type StandardSummary = {
  projectType: string;
  projectTypeLabel: string;
  count: number;
};

export type ProjectDashboard = {
  projectTypes: ProjectTypeOption[];
  projects: ProjectRecord[];
  standardSummaries: StandardSummary[];
};

export type CreateProjectPayload = {
  projectName: string;
  projectType: string;
  owner: string;
  location: string;
};

export type LandUseStandard = {
  id: string;
  projectType: string;
  projectTypeLabel: string;
  sourceFile: string;
  chapterTitle: string;
  content: string;
  keywords: string;
};

export type SynthesizeResponse = {
  summary: string;
  rawText: string;
  provider: string;
};

export type LandUseStandardMatch = {
  id: string;
  projectId: string;
  standardId: string;
  matchStatus: string;
  matchedFields: string;
  conclusion: string;
  createdAt: string;
  standard: LandUseStandard;
};

export type ProjectFile = {
  id: string;
  projectId: string;
  originalName: string;
  mimeType: string;
  aiSuggestedStep: number | null;
  currentStep: number | null;
  confirmedByUser: boolean;
  analysisStatus: string;
  uploadBatch: string;
  createdAt: string;
};

export type AnalysisProgress = {
  total: number;
  done: number;
  failed: number;
  running: number;
};

export type FileAnalysisDetail = {
  id: number;
  fileId: string;
  projectId: string;
  extractedText: string;
  extractedFieldsJson: string;
  ocrUsed: boolean;
  doubaoUsed: boolean;
  aiSummary: string;
  aiAdvice: string;
  detectedDocumentType: string;
  analyzedAt: string;
};

export type VerdictResult = {
  projectId: string;
  stepNo: number;
  verdict: string;
  passItemsJson: string;
  warnItemsJson: string;
  failItemsJson: string;
  generatedAt: string;
};

export type WorkspaceV2 = {
  files: ProjectFile[];
  verdicts: VerdictResult[];
};

export type PreviewStatus = {
  status: string;
  markdown: string;
  generatedAt: string;
};

export type StepFieldDto = {
  key: string;
  label: string;
  value: string;
  source: string;
  sourceFileId: string | null;
  override: boolean;
  confidence: number;
};

export type SituationDto = {
  stepNo: number;
  groupId: string;
  value: string;
};

export type ProjectTypeDto = {
  typeKey: string;
  label: string;
  enabled: boolean;
  sourceFile: string | null;
  sortOrder: number;
  standardCount: number;
  tableCount: number;
  createdAt: string;
  updatedAt: string;
};

export type StandardTableDto = {
  id: string;
  projectType: string;
  projectTypeLabel: string;
  sourceFile: string;
  chapter: string;
  tableCode: string;
  tableTitle: string;
  unit: string;
  headersJson: string;
  rowsJson: string;
  annotationJson: string;
  annotated: boolean;
  annotatedBy: string;
  annotatedAt: string;
  aiPrelabeled: boolean;
};

export type TableAnnotation = {
  queryKeys: Array<{ col: number; name: string }>;
  valueCols: Array<{ col: number; name: string; semantic: 'upper_bound' | 'lower_bound' | 'exact' }>;
  applicableSituations: Array<{ stepNo: number; groupId: string; value: string }>;
  notes?: string;
  source?: 'ai' | 'human';
  model?: string;
};

export type StandardItemDto = {
  id: string;
  projectType: string;
  projectTypeLabel: string;
  sourceFile: string;
  chapterTitle: string;
  content: string;
  keywords: string;
};