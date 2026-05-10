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
  WorkspaceState
} from './types';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

async function request<T>(path: string, options?: RequestInit, timeoutMs = 480_000): Promise<T> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const response = await fetch(`${API_BASE}${path}`, { signal: ctrl.signal, ...options });
    if (!response.ok) {
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
    body: JSON.stringify(payload)
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
  return request<LandUseStandardMatch[]>(`/api/projects/${encodeURIComponent(projectId)}/standard-matches/refresh`, {
    method: 'POST'
  });
}

export function updateField(stepId: string, key: string, value: string, status: string, projectId?: string | null): Promise<WorkspaceState> {
  return request<WorkspaceState>(withProject('/api/workspace/fields', projectId), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ stepId, key, value, status })
  });
}

export function uploadPolicyCard(file: File, projectId?: string | null): Promise<PolicyCardUploadResponse> {
  const formData = new FormData();
  formData.append('file', file);
  if (projectId) formData.append('projectId', projectId);
  return request<PolicyCardUploadResponse>('/api/policy-card/upload', {
    method: 'POST',
    body: formData
  });
}

export function analyzeDocument(file: File, targetStep: string, aiConfig: AiConfig, fallbackStep = '', projectId?: string | null): Promise<AnalysisResponse> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('targetStep', targetStep);
  formData.append('fallbackStep', fallbackStep);
  formData.append('aiProvider', aiConfig.provider);
  formData.append('deepseekModel', aiConfig.deepseekModel);
  if (projectId) formData.append('projectId', projectId);
  if (aiConfig.provider === 'deepseek' && aiConfig.deepseekApiKey.trim()) {
    formData.append('deepseekApiKey', aiConfig.deepseekApiKey.trim());
  }
  if (aiConfig.doubaoApiKey?.trim()) formData.append('doubaoApiKey', aiConfig.doubaoApiKey.trim());
  if (aiConfig.doubaoEndpoint?.trim()) formData.append('doubaoEndpoint', aiConfig.doubaoEndpoint.trim());
  return request<AnalysisResponse>('/api/documents/analyze', {
    method: 'POST',
    body: formData
  });
}

export function synthesizeAnalyses(analyses: AnalysisResponse[], aiConfig: AiConfig): Promise<SynthesizeResponse> {
  return request<SynthesizeResponse>('/api/documents/synthesize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      analyses,
      aiProvider: aiConfig.provider,
      deepseekApiKey: aiConfig.deepseekApiKey,
      deepseekModel: aiConfig.deepseekModel
    })
  });
}

export function generateReport(analyses: AnalysisResponse[]): Promise<ReportResponse> {
  return request<ReportResponse>('/api/reports/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ analyses })
  });
}

export async function exportReport(format: 'md' | 'docx', analyses: AnalysisResponse[], markdown?: string): Promise<Blob> {
  const response = await fetch(`${API_BASE}/api/reports/export/${format}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ analyses, markdown })
  });
  if (!response.ok) {
    throw new Error(await response.text() || `导出失败: ${response.status}`);
  }
  return response.blob();
}

export function updateSituation(stepId: string, groupId: string, value: string, projectId?: string | null): Promise<WorkspaceState> {
  return request<WorkspaceState>(withProject('/api/workspace/situations', projectId), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ stepId, groupId, value })
  });
}

export function withdrawDocument(fileId: string, projectId?: string | null): Promise<WorkspaceState> {
  return request<WorkspaceState>(withProject(`/api/documents/${encodeURIComponent(fileId)}`, projectId), {
    method: 'DELETE'
  });
}

export function sourceFileUrl(fileId: string): string {
  return `${API_BASE}/api/documents/source/${encodeURIComponent(fileId)}`;
}