// 激活窗口的预加载脚本：把激活相关的能力安全地暴露给页面。
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('activationApi', {
  // 提交授权码做激活，返回 { ok, message }
  activate: (key) => ipcRenderer.invoke('license:activate', key),
  // 激活成功后通知主进程继续启动
  proceed: (key) => ipcRenderer.send('license:proceed', key),
});
