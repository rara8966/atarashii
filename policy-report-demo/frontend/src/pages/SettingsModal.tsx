import { useState, useEffect } from 'react';
import { X, Save, KeyRound, Loader2, CheckCircle2, AlertTriangle, Plug } from 'lucide-react';
import type { AiConfig } from '../types';
import { readAiConfig, writeAiConfig } from '../aiConfig';
import { testAiKey } from '../api';

type TestState = { status: 'idle' | 'testing' | 'ok' | 'fail'; message: string };

export default function SettingsModal({ open, onClose, requireKey = false, onSaved }: {
  open: boolean;
  onClose: () => void;
  /** 引导模式：必须先测试连通通过才能保存，用于新建项目前强制配置 Key。 */
  requireKey?: boolean;
  /** 保存成功后回调（引导模式下用于继续创建项目流程）。 */
  onSaved?: (config: AiConfig) => void;
}) {
  const [config, setConfig] = useState<AiConfig>(readAiConfig);
  const [test, setTest] = useState<TestState>({ status: 'idle', message: '' });

  useEffect(() => {
    if (open) {
      setConfig(readAiConfig());
      setTest({ status: 'idle', message: '' });
    }
  }, [open]);

  if (!open) return null;

  const hasKey = !!config.deepseekApiKey.trim();
  // 引导模式下，必须测试连通通过才允许保存；普通模式随时可存。
  const canSave = requireKey ? test.status === 'ok' : true;

  async function handleTest() {
    if (!config.deepseekApiKey.trim()) {
      setTest({ status: 'fail', message: '请先填写 DeepSeek API Key' });
      return;
    }
    setTest({ status: 'testing', message: '' });
    try {
      const res = await testAiKey(config.deepseekApiKey.trim(), config.deepseekModel.trim() || 'deepseek-chat');
      setTest({ status: res.ok ? 'ok' : 'fail', message: res.message });
    } catch (err) {
      setTest({ status: 'fail', message: err instanceof Error ? err.message : '测试失败' });
    }
  }

  function handleSave() {
    writeAiConfig(config);
    onSaved?.(config);
    onClose();
  }

  // 改动 key/模型后重置测试状态，避免拿旧结果误判
  function patch(p: Partial<AiConfig>) {
    setConfig(c => ({ ...c, ...p }));
    setTest({ status: 'idle', message: '' });
  }

  return (
    <div
      style={{
        position: 'fixed', inset: 0, background: 'rgba(20, 32, 51, 0.45)',
        zIndex: 1400, display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}
      onClick={() => { if (!requireKey) onClose(); }}
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
            <strong style={{ fontSize: '15px' }}>{requireKey ? '配置 AI Key 后继续' : 'AI 配置'}</strong>
          </div>
          <button type="button" onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '4px' }}>
            <X size={18} />
          </button>
        </div>

        {requireKey && (
          <div className="message-strip" style={{ background: '#fef3c7', color: '#78350f', margin: '0 0 14px', minHeight: 'auto', padding: '10px 12px', fontWeight: 600 }}>
            <AlertTriangle size={15} />
            新建项目需要 DeepSeek API Key 才能进行 AI 材料分析。请填写并测试连通后继续。
          </div>
        )}

        <p className="quiet" style={{ fontSize: '12px', marginTop: 0, marginBottom: '16px' }}>
          配置后存储在本机浏览器中，不会上传到服务器。下次上传材料/重新分析时自动生效。
        </p>

        <div className="ai-config-label">文字分析模型</div>
        <div className="ai-config-row" style={{ flexDirection: 'column', alignItems: 'stretch', gap: '8px', marginBottom: '10px' }}>
          <div className="segmented" style={{ marginBottom: 0, alignSelf: 'flex-start' }}>
            <button type="button" className={config.provider === 'deepseek' ? 'active' : ''} onClick={() => patch({ provider: 'deepseek' })}>DeepSeek</button>
            <button type="button" className={config.provider === 'rules' ? 'active' : ''} onClick={() => patch({ provider: 'rules' })}>规则引擎</button>
          </div>
          {config.provider === 'deepseek' && (
            <>
              <input className="config-input" type="password" placeholder="DeepSeek API Key（sk-…）"
                     value={config.deepseekApiKey}
                     onChange={e => patch({ deepseekApiKey: e.target.value })} />
              <input className="config-input" type="text" placeholder="模型 (默认 deepseek-chat)"
                     value={config.deepseekModel}
                     onChange={e => patch({ deepseekModel: e.target.value })} />
            </>
          )}
        </div>

        {/* 测试连通 */}
        {config.provider === 'deepseek' && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '14px', flexWrap: 'wrap' }}>
            <button type="button" className="btn btn-outline" onClick={handleTest} disabled={test.status === 'testing' || !hasKey} style={{ fontSize: '13px' }}>
              {test.status === 'testing' ? <Loader2 className="spin" size={14} /> : <Plug size={14} />}
              测试连通
            </button>
            {test.status === 'ok' && <span style={{ fontSize: '12px', color: '#0d8a72', display: 'inline-flex', alignItems: 'center', gap: '4px' }}><CheckCircle2 size={14} />{test.message}</span>}
            {test.status === 'fail' && <span style={{ fontSize: '12px', color: '#bb4b5b', display: 'inline-flex', alignItems: 'center', gap: '4px' }}><AlertTriangle size={14} />{test.message}</span>}
          </div>
        )}

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

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px', alignItems: 'center' }}>
          {requireKey && test.status !== 'ok' && (
            <span style={{ fontSize: '12px', color: '#888', marginRight: 'auto' }}>请先测试连通通过后保存</span>
          )}
          {!requireKey && <button className="btn btn-outline" type="button" onClick={onClose}>关闭</button>}
          <button className="btn btn-primary" type="button" onClick={handleSave} disabled={!canSave} title={canSave ? '' : '请先测试连通通过'}>
            <Save size={14} />{requireKey ? '保存并继续' : '保存'}
          </button>
        </div>
      </div>
    </div>
  );
}
