import { getToken, clearToken } from './auth';
import type {
  AiConfig,
  AnalysisResponse,
  CreateProjectPayload,
  DemoProject,
  LandUseStandard,
  LandUseStandardMatch,
  OllamaStatus,
  PolicyCardUploadResponse,
  ProjectDashboard,
  ProjectRecord,
  ReportResponse,
  SynthesizeResponse,
  WorkspaceState,
  ProjectFile,
  AnalysisProgress,
  FileAnalysisDetail,
  VerdictResult,
  WorkspaceV2,
  PreviewStatus,
  StepFieldDto,
  SituationDto,
  ProjectTypeDto,
  StandardItemDto,
  StandardTableDto,
  ZoneVerdictDto,
} from './types';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request<T>(path: string, options?: RequestInit, timeoutMs = 480_000): Promise<T> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      signal: ctrl.signal,
      ...options,
      headers: { ...authHeaders(), ...(options?.headers ?? {}) },
    });
    if (!response.ok) {
      if (response.status === 401) {
        // 温和处理：清 token 让 AuthGuard 在下次路由切换时跳登录，避免在轮询中突然强跳
        clearToken();
      }
      const text = await response.text();
      throw new Error(text || `请求失败: ${response.status}`);
    }
    return response.json() as Promise<T>;
  } catch (err) {
    if (err instanceof Error && err.name === 'AbortError') {
      throw new Error('请求超时（文件过大或AI分析耗时过长），请分批上传或减少单次上传数量。');
    }
    throw err;
  } finally {
    clearTimeout(timer);
  }
}

// ── Auth ──

export async function apiLogin(username: string, password: string): Promise<{ token: string; username: string; role: string }> {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || '登录失败');
  return data;
}

export async function apiRegister(username: string, password: string, role = 'REPORTER'): Promise<{ token: string; username: string; role: string }> {
  const res = await fetch(`${API_BASE}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ username, password, role }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || '注册失败');
  return data;
}

export async function apiBootstrap(): Promise<{ hasAdmin: boolean }> {
  const res = await fetch(`${API_BASE}/api/auth/bootstrap`);
  if (!res.ok) return { hasAdmin: true };
  return res.json();
}

// ── 旧版 API（保持不动，供原始工作台使用）──

export function fetchProject(): Promise<DemoProject> {
  return request<DemoProject>('/api/demo/project');
}

export function fetchOllamaStatus(): Promise<OllamaStatus> {
  return request<OllamaStatus>('/api/ollama/status');
}

function withProject(path: string, projectId?: string | null): string {
  if (!projectId) return path;
  const separator = path.includes('?') ? '&' : '?';
  return `${path}${separator}projectId=${encodeURIComponent(projectId)}`;
}

export function fetchWorkspaceState(projectId?: string | null): Promise<WorkspaceState> {
  return request<WorkspaceState>(withProject('/api/workspace/state', projectId));
}

export function fetchProjectDashboard(): Promise<ProjectDashboard> {
  return request<ProjectDashboard>('/api/projects/dashboard');
}

export function createProject(payload: CreateProjectPayload): Promise<ProjectRecord> {
  return request<ProjectRecord>('/api/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function fetchLandStandards(projectType: string, query = '', limit = 30): Promise<LandUseStandard[]> {
  const params = new URLSearchParams({ projectType, query, limit: String(limit) });
  return request<LandUseStandard[]>(`/api/land-standards?${params.toString()}`);
}

export function fetchStandardMatches(projectId: string): Promise<LandUseStandardMatch[]> {
  return request<LandUseStandardMatch[]>(`/api/projects/${encodeURIComponent(projectId)}/standard-matches`);
}

export function refreshStandardMatches(projectId: string): Promise<LandUseStandardMatch[]> {
  return request<LandUseStandardMatch[]>(`/api/projects/${encodeURIComponent(projectId)}/standard-matches/refresh`, { method: 'POST' });
}

export function updateField(stepId: string, key: string, value: string, status: string, projectId?: string | null): Promise<WorkspaceState> {
  return request<WorkspaceState>(withProject('/api/workspace/fields', projectId), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ stepId, key, value, status }),
  });
}

export function uploadPolicyCard(file: File, projectId?: string | null): Promise<PolicyCardUploadResponse> {
  const formData = new FormData();
  formData.append('file', file);
  if (projectId) formData.append('projectId', projectId);
  return request<PolicyCardUploadResponse>('/api/policy-card/upload', { method: 'POST', body: formData });
}

export function analyzeDocument(file: File, targetStep: string, aiConfig: AiConfig, fallbackStep = '', projectId?: string | null): Promise<AnalysisResponse> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('targetStep', targetStep);
  formData.append('fallbackStep', fallbackStep);
  formData.append('aiProvider', aiConfig.provider);
  formData.append('deepseekModel', aiConfig.deepseekModel);
  if (projectId) formData.append('projectId', projectId);
  if (aiConfig.provider === 'deepseek' && aiConfig.deepseekApiKey.trim()) formData.append('deepseekApiKey', aiConfig.deepseekApiKey.trim());
  if (aiConfig.doubaoApiKey?.trim()) formData.append('doubaoApiKey', aiConfig.doubaoApiKey.trim());
  if (aiConfig.doubaoEndpoint?.trim()) formData.append('doubaoEndpoint', aiConfig.doubaoEndpoint.trim());
  return request<AnalysisResponse>('/api/documents/analyze', { method: 'POST', body: formData });
}

export function testAiKey(deepseekApiKey: string, deepseekModel = 'deepseek-chat'): Promise<{ ok: boolean; message: string }> {
  return request<{ ok: boolean; message: string }>('/api/ai/test-key', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deepseekApiKey, deepseekModel }),
  });
}

export function synthesizeAnalyses(analyses: AnalysisResponse[], aiConfig: AiConfig): Promise<SynthesizeResponse> {
  return request<SynthesizeResponse>('/api/documents/synthesize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ analyses, aiProvider: aiConfig.provider, deepseekApiKey: aiConfig.deepseekApiKey, deepseekModel: aiConfig.deepseekModel }),
  });
}

export function generateReport(analyses: AnalysisResponse[]): Promise<ReportResponse> {
  return request<ReportResponse>('/api/reports/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ analyses }),
  });
}

export async function exportReport(format: 'md' | 'docx', analyses: AnalysisResponse[], markdown?: string): Promise<Blob> {
  const response = await fetch(`${API_BASE}/api/reports/export/${format}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ analyses, markdown }),
  });
  if (!response.ok) {
    if (response.status === 401) {
      clearToken();
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.replace('/login');
      }
    }
    throw new Error(await response.text() || `导出失败: ${response.status}`);
  }
  return response.blob();
}

export function updateSituation(stepId: string, groupId: string, value: string, projectId?: string | null): Promise<WorkspaceState> {
  return request<WorkspaceState>(withProject('/api/workspace/situations', projectId), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ stepId, groupId, value }),
  });
}

export function withdrawDocument(fileId: string, projectId?: string | null): Promise<WorkspaceState> {
  return request<WorkspaceState>(withProject(`/api/documents/${encodeURIComponent(fileId)}`, projectId), { method: 'DELETE' });
}

export function sourceFileUrl(fileId: string): string {
  return `${API_BASE}/api/documents/source/${encodeURIComponent(fileId)}`;
}

// ── 新版 v2 API ──

export function v2ListProjects(): Promise<ProjectDashboard> {
  return request<ProjectDashboard>('/api/v2/projects');
}

export function v2CreateProject(payload: CreateProjectPayload): Promise<ProjectRecord> {
  return request<ProjectRecord>('/api/v2/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export function v2GetProject(id: string): Promise<ProjectRecord> {
  return request<ProjectRecord>(`/api/v2/projects/${id}`);
}

export function v2UploadFiles(projectId: string, files: File[], aiConfig: AiConfig): Promise<{ uploaded: number; message: string }> {
  const formData = new FormData();
  files.forEach(f => formData.append('files', f));
  formData.append('aiProvider', aiConfig.provider);
  if (aiConfig.provider === 'deepseek' && aiConfig.deepseekApiKey?.trim()) formData.append('deepseekApiKey', aiConfig.deepseekApiKey.trim());
  if (aiConfig.deepseekModel) formData.append('deepseekModel', aiConfig.deepseekModel);
  if (aiConfig.doubaoApiKey?.trim()) formData.append('doubaoApiKey', aiConfig.doubaoApiKey.trim());
  if (aiConfig.doubaoEndpoint?.trim()) formData.append('doubaoEndpoint', aiConfig.doubaoEndpoint.trim());
  return request(`/api/v2/projects/${projectId}/files/batch`, { method: 'POST', body: formData });
}

export function v2ListFiles(projectId: string): Promise<ProjectFile[]> {
  return request<ProjectFile[]>(`/api/v2/projects/${projectId}/files`);
}

export function v2AnalysisStatus(projectId: string): Promise<AnalysisProgress> {
  return request<AnalysisProgress>(`/api/v2/projects/${projectId}/analysis-status`);
}

export function v2AssignStep(projectId: string, fileId: string, step: number | null): Promise<ProjectFile> {
  return request<ProjectFile>(`/api/v2/projects/${projectId}/files/${fileId}/assign`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ step }),
  });
}

export function v2RetractFile(projectId: string, fileId: string): Promise<ProjectFile> {
  return request<ProjectFile>(`/api/v2/projects/${projectId}/files/${fileId}/retract`, { method: 'POST' });
}

export function v2DeleteFile(projectId: string, fileId: string): Promise<{ status: string; fileId: string }> {
  return request(`/api/v2/projects/${projectId}/files/${fileId}`, { method: 'DELETE' });
}

export function v2DeleteProject(projectId: string): Promise<{ status: string; projectId: string }> {
  return request(`/api/v2/projects/${projectId}`, { method: 'DELETE' });
}

export function v2GetFileAnalysis(projectId: string, fileId: string): Promise<FileAnalysisDetail> {
  return request<FileAnalysisDetail>(`/api/v2/projects/${projectId}/files/${fileId}/analysis`);
}

export function v2ConfirmType(projectId: string, projectType: string): Promise<ProjectRecord> {
  return request<ProjectRecord>(`/api/v2/projects/${projectId}/confirm-type`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectType }),
  });
}

export function v2ConfirmInfo(projectId: string, name: string, owner: string, location: string): Promise<ProjectRecord> {
  return request<ProjectRecord>(`/api/v2/projects/${projectId}/confirm-info`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, owner, location }),
  });
}

export function v2GetWorkspace(projectId: string): Promise<WorkspaceV2> {
  return request<WorkspaceV2>(`/api/v2/projects/${projectId}/workspace`);
}

export function v2RefreshVerdicts(projectId: string): Promise<VerdictResult[]> {
  return request<VerdictResult[]>(`/api/v2/projects/${projectId}/verdicts/refresh`, { method: 'POST' });
}

export function v2GetStepVerdict(projectId: string, stepNo: number): Promise<VerdictResult> {
  return request<VerdictResult>(`/api/v2/projects/${projectId}/steps/${stepNo}/verdict`);
}

export function v2GetStepFields(projectId: string, stepNo: number): Promise<StepFieldDto[]> {
  return request<StepFieldDto[]>(`/api/v2/projects/${projectId}/steps/${stepNo}/fields`);
}

export function v2UpdateStepField(projectId: string, stepNo: number, key: string, value: string): Promise<StepFieldDto> {
  return request<StepFieldDto>(`/api/v2/projects/${projectId}/steps/${stepNo}/fields/${encodeURIComponent(key)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ value }),
  });
}

export function v2ListSituations(projectId: string): Promise<SituationDto[]> {
  return request<SituationDto[]>(`/api/v2/projects/${projectId}/situations`);
}

export function v2UpsertSituation(projectId: string, stepNo: number, groupId: string, value: string): Promise<SituationDto> {
  return request<SituationDto>(`/api/v2/projects/${projectId}/situations/${stepNo}/${encodeURIComponent(groupId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ value }),
  });
}

export function v2AutoDetectSituations(projectId: string, deepseekApiKey: string, deepseekModel: string): Promise<{ detected: number; situations: SituationDto[]; message: string }> {
  return request(`/api/v2/projects/${projectId}/situations/auto-detect`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deepseekApiKey, deepseekModel }),
  });
}

export function v2GetFunctionalZoneVerdict(projectId: string): Promise<ZoneVerdictDto[]> {
  return request<ZoneVerdictDto[]>(`/api/v2/projects/${projectId}/functional-zone-verdict`);
}

// ── 用地标准管理 ──

export function listProjectTypes(): Promise<ProjectTypeDto[]> {
  return request<ProjectTypeDto[]>('/api/standards/types');
}

export function toggleProjectType(typeKey: string): Promise<ProjectTypeDto> {
  return request<ProjectTypeDto>(`/api/standards/types/${encodeURIComponent(typeKey)}/toggle`, { method: 'PUT' });
}

export function listStandardItems(typeKey: string): Promise<StandardItemDto[]> {
  return request<StandardItemDto[]>(`/api/standards/types/${encodeURIComponent(typeKey)}/items`);
}

export function clearStandardItems(typeKey: string): Promise<{ typeKey: string; removed: number }> {
  return request(`/api/standards/types/${encodeURIComponent(typeKey)}/items`, { method: 'DELETE' });
}

export function listStandardTables(typeKey: string): Promise<StandardTableDto[]> {
  return request<StandardTableDto[]>(`/api/standards/types/${encodeURIComponent(typeKey)}/tables`);
}

export function clearStandardTables(typeKey: string): Promise<{ typeKey: string; removed: number }> {
  return request(`/api/standards/types/${encodeURIComponent(typeKey)}/tables`, { method: 'DELETE' });
}

export function saveTableAnnotation(tableId: string, annotation: unknown): Promise<StandardTableDto> {
  return request<StandardTableDto>(`/api/standards/tables/${encodeURIComponent(tableId)}/annotation`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ annotation }),
  });
}

export function aiPrelabelTable(tableId: string, deepseekApiKey: string, deepseekModel: string): Promise<StandardTableDto> {
  return request<StandardTableDto>(`/api/standards/tables/${encodeURIComponent(tableId)}/ai-prelabel`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deepseekApiKey, deepseekModel }),
  });
}

export function aiPrelabelAllTables(typeKey: string, deepseekApiKey: string, deepseekModel: string, skipAlreadyAnnotated = true): Promise<{ typeKey: string; succeeded: number; failed: number; total: number; message: string }> {
  return request(`/api/standards/types/${encodeURIComponent(typeKey)}/tables/ai-prelabel-all`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deepseekApiKey, deepseekModel, skipAlreadyAnnotated }),
  });
}

export async function uploadStandardTablesDocx(typeKey: string, file: File, replace = false, typeLabel?: string): Promise<{ typeKey: string; imported: number; total: number; message: string }> {
  const form = new FormData();
  form.append('file', file);
  form.append('typeKey', typeKey);
  if (typeLabel) form.append('typeLabel', typeLabel);
  form.append('replace', String(replace));
  const res = await fetch(`${API_BASE}/api/standards/tables/upload`, {
    method: 'POST',
    headers: { ...authHeaders() },
    body: form,
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || '上传失败');
  return data;
}

export async function uploadStandardDocx(typeKey: string, typeLabel: string, file: File, replace = true): Promise<{ typeKey: string; typeLabel: string; sourceFile: string; imported: number; message: string }> {
  const form = new FormData();
  form.append('file', file);
  form.append('typeKey', typeKey);
  form.append('typeLabel', typeLabel);
  form.append('replace', String(replace));
  const res = await fetch(`${API_BASE}/api/standards/upload`, {
    method: 'POST',
    headers: { ...authHeaders() },
    body: form,
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || '上传失败');
  return data;
}

export function v2InitiatePreview(projectId: string): Promise<{ status: string; message: string }> {
  return request(`/api/v2/projects/${projectId}/preview`, { method: 'POST' });
}

export function v2GetPreview(projectId: string): Promise<PreviewStatus> {
  return request<PreviewStatus>(`/api/v2/projects/${projectId}/preview`);
}

export function v2ConfirmPreview(projectId: string): Promise<{ status: string }> {
  return request(`/api/v2/projects/${projectId}/preview/confirm`, { method: 'POST' });
}

export async function v2ExportReport(projectId: string, format: 'docx' | 'md'): Promise<Blob> {
  const response = await fetch(`${API_BASE}/api/v2/projects/${projectId}/export/${format}`, {
    method: 'POST',
    headers: authHeaders(),
  });
  if (!response.ok) {
    if (response.status === 401) {
      clearToken();
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.replace('/login');
      }
    }
    throw new Error(await response.text() || `导出失败: ${response.status}`);
  }
  return response.blob();
}
