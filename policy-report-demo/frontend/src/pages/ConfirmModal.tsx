import { KeyRound, X } from 'lucide-react';

/** 居中确认弹窗：用于"需要配置 Key"等需要用户明确选择「去配置/取消」的场景。
 *  zIndex 1300，高于全屏标注抽屉(1100)，确保抽屉打开时也能盖在最上层、不被遮挡。 */
export default function ConfirmModal({ open, title, message, confirmText = '去配置', cancelText = '取消', onConfirm, onCancel }: {
  open: boolean;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  if (!open) return null;
  return (
    <div
      style={{ position: 'fixed', inset: 0, background: 'rgba(20, 32, 51, 0.5)', zIndex: 1300, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px' }}
      onClick={onCancel}
    >
      <div
        style={{ background: '#fff', borderRadius: '12px', padding: '22px 24px', width: 'min(420px, calc(100vw - 32px))', boxShadow: '0 18px 48px rgba(20, 32, 51, 0.25)' }}
        onClick={e => e.stopPropagation()}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <KeyRound size={18} style={{ color: '#b45309' }} />
            <strong style={{ fontSize: '15px' }}>{title}</strong>
          </div>
          <button type="button" onClick={onCancel} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '4px', color: '#64748b' }}>
            <X size={18} />
          </button>
        </div>
        <p style={{ fontSize: '13.5px', color: '#44506a', lineHeight: 1.6, margin: '0 0 20px' }}>{message}</p>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
          <button className="btn btn-outline" type="button" onClick={onCancel}>{cancelText}</button>
          <button className="btn btn-primary" type="button" onClick={onConfirm}>
            <KeyRound size={14} />{confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
