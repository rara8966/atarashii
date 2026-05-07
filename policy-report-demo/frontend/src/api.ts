import type { AiConfig, AnalysisResponse, DemoProject, OllamaStatus, PolicyCardUploadResponse, ReportResponse, WorkspaceState } from './types';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, options);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `请求失败: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function fetchProject(): Promise<DemoProject> {
  return request<DemoProject>('/api/demo/project');
}

export function fetchOllamaStatus(): Promise<OllamaStatus> {
  return request<OllamaStatus>('/api/ollama/status');
}

export function fetchWorkspaceState(): Promise<WorkspaceState> {
  return request<WorkspaceState>('/api/workspace/state');
}

export function updateField(stepId: string, key: string, value: string, status: string): Promise<WorkspaceState> {
  return request<WorkspaceState>('/api/workspace/fields', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ stepId, key, value, status })
  });
}

export function uploadPolicyCard(file: File): Promise<PolicyCardUploadResponse> {
  const formData = new FormData();
  formData.append('file', file);
  return request<PolicyCardUploadResponse>('/api/policy-card/upload', {
    method: 'POST',
    body: formData
  });
}

export function analyzeDocument(file: File, targetStep: string, aiConfig: AiConfig, fallbackStep = ''): Promise<AnalysisResponse> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('targetStep', targetStep);
  formData.append('fallbackStep', fallbackStep);
  formData.append('aiProvider', aiConfig.provider);
  formData.append('deepseekModel', aiConfig.deepseekModel);
  if (aiConfig.provider === 'deepseek' && aiConfig.deepseekApiKey.trim()) {
    formData.append('deepseekApiKey', aiConfig.deepseekApiKey.trim());
  }
  return request<AnalysisResponse>('/api/documents/analyze', {
    method: 'POST',
    body: formData
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

export function updateSituation(stepId: string, groupId: string, value: string): Promise<WorkspaceState> {
  return request<WorkspaceState>('/api/workspace/situations', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ stepId, groupId, value })
  });
}

export function withdrawDocument(fileId: string): Promise<WorkspaceState> {
  return request<WorkspaceState>(`/api/documents/${encodeURIComponent(fileId)}`, {
    method: 'DELETE'
  });
}

export function sourceFileUrl(fileId: string): string {
  return `${API_BASE}/api/documents/source/${encodeURIComponent(fileId)}`;
}