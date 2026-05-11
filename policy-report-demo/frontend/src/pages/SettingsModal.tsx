import { useState, useEffect } from 'react';
import { X, Save, KeyRound } from 'lucide-react';
import type { AiConfig } from '../types';

const AI_CONFIG_KEY = 'policy-report-demo-ai-config';

function readAiConfig(): AiConfig {
  const fallback: AiConfig = { provider: 'deepseek', deepseekApiKey: '', deepseekModel: 'deepseek-chat', doubaoApiKey: '', doubaoEndpoint: '' };
  try { return { ...fallback, ...JSON.parse(localStorage.getItem(AI_CONFIG_KEY) || '{}') }; } catch { return fallback; }
}

export default function SettingsModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [config, setConfig] = useState<AiConfig>(readAiConfig);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (open) {
      setConfig(readAiConfig());
      setSaved(false);
    }
  }, [open]);

  if (!open) return null;

  function handleSave() {
    localStorage.setItem(AI_CONFIG_KEY, JSON.stringify(config));
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  }

  return (
    <div
      style={{
        position: 'fixed', inset: 0, background: 'rgba(20, 32, 51, 0.45)',
        zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}
      onClick={onClose}
    >
      <div
        style={{
          background: '#fff', borderRadius: '12px', padding: '20px 24px',
          width: 'min(560px, calc(100vw - 32px))', maxHeight: '90vh', overflow: 'auto',
          boxShadow: '0 18px 48px rgba(20, 32, 51, 0.2)',
        }}
        onClick={e => e.stopPropagation()}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <KeyRound size={18} />
            <strong style={{ fontSize: '15px' }}>AI 配置</strong>
          </div>
          <button type="button" onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '4px' }}>
            <X size={18} />
          </button>
        </div>

        <p className="quiet" style={{ fontSize: '12px', marginTop: 0, marginBottom: '16px' }}>
          配置后存储在本机浏览器中，不会上传到服务器。下次上传材料/重新分析时自动生效。
        </p>

        <div className="ai-config-label">文字分析模型</div>
        <div className="ai-config-row" style={{ flexDirection: 'column', alignItems: 'stretch', gap: '8px', marginBottom: '14px' }}>
          <div className="segmented" style={{ marginBottom: 0, alignSelf: 'flex-start' }}>
            <button type="button" className={config.provider === 'deepseek' ? 'active' : ''} onClick={() => setConfig({ ...config, provider: 'deepseek' })}>DeepSeek</button>
            <button type="button" className={config.provider === 'rules' ? 'active' : ''} onClick={() => setConfig({ ...config, provider: 'rules' })}>规则引擎</button>
          </div>
          {config.provider === 'deepseek' && (
            <>
              <input className="config-input" type="password" placeholder="DeepSeek API Key（sk-…）"
                     value={config.deepseekApiKey}
                     onChange={e => setConfig({ ...config, deepseekApiKey: e.target.value })} />
              <input className="config-input" type="text" placeholder="模型 (默认 deepseek-chat)"
                     value={config.deepseekModel}
                     onChange={e => setConfig({ ...config, deepseekModel: e.target.value })} />
            </>
          )}
        </div>

        <div className="ai-config-divider" />

        <div className="ai-config-label" style={{ marginTop: '14px' }}>视觉分析（图片 / 扫描件）— 豆包</div>
        <div className="ai-config-row" style={{ flexDirection: 'column', alignItems: 'stretch', gap: '8px' }}>
          <input className="config-input" type="password" placeholder="豆包 API Key"
                 value={config.doubaoApiKey}
                 onChange={e => setConfig({ ...config, doubaoApiKey: e.target.value })} />
          <input className="config-input" type="text" placeholder="豆包 Endpoint ID（ep-xxx）"
                 value={config.doubaoEndpoint}
                 onChange={e => setConfig({ ...config, doubaoEndpoint: e.target.value })} />
        </div>
        <div className="ai-config-hint" style={{ marginTop: '8px' }}>
          留空则跳过豆包视觉分析，扫描件 / 图片仅走本地 OCR；填好可显著提升表格、签章、手写件识别准确率。
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
          {saved && <span style={{ fontSize: '12px', color: '#0d8a72', alignSelf: 'center' }}>已保存到本机</span>}
          <button className="btn btn-outline" type="button" onClick={onClose}>关闭</button>
          <button className="btn btn-primary" type="button" onClick={handleSave}>
            <Save size={14} />保存
          </button>
        </div>
      </div>
    </div>
  );
}
