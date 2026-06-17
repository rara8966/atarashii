import type { AiConfig } from './types';

export const AI_CONFIG_KEY = 'policy-report-demo-ai-config';

const FALLBACK: AiConfig = {
  provider: 'deepseek',
  deepseekApiKey: '',
  deepseekModel: 'deepseek-chat',
  doubaoApiKey: '',
  doubaoEndpoint: '',
};

export function readAiConfig(): AiConfig {
  try {
    return { ...FALLBACK, ...JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || '{}') };
  } catch {
    return { ...FALLBACK };
  }
}

export function writeAiConfig(config: AiConfig): void {
  localStorage.setItem(AI_CONFIG_KEY, JSON.stringify(config));
}

/** 是否已配置可用于 AI 文字分析的 DeepSeek Key。 */
export function aiConfigHasKey(): boolean {
  return !!readAiConfig().deepseekApiKey.trim();
}
