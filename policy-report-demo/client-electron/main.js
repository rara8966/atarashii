// Electron 外壳：
//   每次启动都必须输入授权码并联网激活成功，才拉起后端进主界面（不记住、不缓存）。
//   关闭窗口 → 强制结束后端进程树，不留残留。
// 机器指纹由本进程统一计算：激活调用与后端校验用同一个值。

const { app, BrowserWindow, dialog, ipcMain } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const http = require('http');

const PORT = 8080;
const BASE_URL = `http://127.0.0.1:${PORT}`;
const CLOUD_URL = 'https://policy.xieyuchen.xyz';

let javaProc = null;
let mainWindow = null;
let activationWindow = null;

/** 资源根：打包后在 process.resourcesPath，开发时在 ./runtime。 */
function resourcePath(...segs) {
  const base = app.isPackaged ? process.resourcesPath : path.join(__dirname, 'runtime');
  return path.join(base, ...segs);
}

/** 程序目录：打包后是 exe 同级，开发时是工程目录。 */
function appDir() {
  return app.isPackaged ? path.dirname(process.execPath) : __dirname;
}

/** 机器指纹：网卡 MAC（排序）+ 平台 + 架构。激活和后端校验都用这个值。 */
function machineId() {
  const macs = new Set();
  const ifaces = os.networkInterfaces();
  for (const list of Object.values(ifaces)) {
    for (const ni of list || []) {
      if (!ni.internal && ni.mac && ni.mac !== '00:00:00:00:00:00') {
        macs.add(ni.mac.toLowerCase());
      }
    }
  }
  return 'MFP|' + [...macs].sort().join(',') + '|' + os.platform() + '|' + os.arch();
}

/** 调云端 /api/license/activate 激活校验。每次启动都要走一遍。 */
async function callActivate(licenseKey) {
  try {
    const resp = await fetch(CLOUD_URL + '/api/license/activate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ licenseKey: String(licenseKey).trim(), machineId: machineId() }),
    });
    let data = {};
    try {
      data = await resp.json();
    } catch (_) {
      // 响应体不是 JSON
    }
    return {
      ok: !!data.ok,
      message: data.message || data.error || ('服务器返回 ' + resp.status),
    };
  } catch (_) {
    return { ok: false, message: '无法连接授权服务器，请检查网络后重试' };
  }
}

// ── 后端进程 ──

function startBackend(licenseKey) {
  const javaExe = resourcePath('jre', 'bin', 'java.exe');
  const jar = resourcePath('policy-report-client.jar');
  const dataDir = path.join(appDir(), 'data');
  fs.mkdirSync(dataDir, { recursive: true });

  javaProc = spawn(javaExe, ['-jar', jar], {
    cwd: dataDir,
    env: { ...process.env, CLOUD_LICENSE_KEY: String(licenseKey).trim(), CLOUD_MACHINE_ID: machineId() },
    windowsHide: true,
  });
  javaProc.on('exit', (code) => {
    const wasRunning = javaProc !== null;
    javaProc = null;
    if (wasRunning && !app.isQuitting && code !== 0 && code !== 143) {
      dialog.showErrorBox('后端异常退出', '系统后台进程已停止（退出码 ' + code + '），请重启软件。');
      app.quit();
    }
  });
}

/** 强制结束后端进程树，确保关窗后不留残留 java 进程。 */
function stopBackend() {
  if (javaProc && javaProc.pid) {
    const pid = javaProc.pid;
    javaProc = null;
    try {
      if (process.platform === 'win32') {
        // /T 连子进程一起杀，/F 强制
        spawn('taskkill', ['/PID', String(pid), '/T', '/F']);
      } else {
        process.kill(pid, 'SIGKILL');
      }
    } catch (_) {
      // 忽略
    }
  }
}

function waitForBackend(timeoutMs) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs;
    const probe = () => {
      const req = http.get(BASE_URL + '/', (res) => {
        res.destroy();
        resolve();
      });
      req.on('error', () => {
        if (Date.now() > deadline) reject(new Error('timeout'));
        else setTimeout(probe, 800);
      });
    };
    probe();
  });
}

// ── 窗口 ──

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    title: '建设用地报批审查报告智能生成系统',
    webPreferences: { contextIsolation: true },
  });
  mainWindow.setMenuBarVisibility(false);
  mainWindow.loadURL(BASE_URL);
  // 点右上角 X 关闭 → 收掉后端 → 整个应用退出
  mainWindow.on('closed', () => {
    mainWindow = null;
    app.isQuitting = true;
    stopBackend();
    app.quit();
  });
}

function showActivationWindow() {
  activationWindow = new BrowserWindow({
    width: 480,
    height: 380,
    resizable: false,
    fullscreenable: false,
    maximizable: false,
    title: '软件激活',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
    },
  });
  activationWindow.setMenuBarVisibility(false);
  activationWindow.loadFile(path.join(__dirname, 'activation.html'));
  activationWindow.on('closed', () => {
    activationWindow = null;
    // 没进主界面就关了激活窗 = 放弃，整个退出
    if (!mainWindow) {
      app.isQuitting = true;
      stopBackend();
      app.quit();
    }
  });
}

/** 拉起后端并打开主界面。 */
async function launchApp(licenseKey) {
  startBackend(licenseKey);
  try {
    await waitForBackend(120000);
  } catch (_) {
    stopBackend();
    dialog.showErrorBox('启动失败', '后端服务启动超时，请重试，或联系供应方。');
    app.isQuitting = true;
    app.quit();
    return;
  }
  createMainWindow();
  if (activationWindow) {
    activationWindow.close();
  }
}

// ── IPC：激活窗口与主进程通信 ──

// 渲染进程请求激活：每次都实打实调云端校验，不缓存
ipcMain.handle('license:activate', async (_e, key) => {
  return callActivate(key);
});

// 激活成功，渲染进程通知主进程继续启动
ipcMain.on('license:proceed', (_e, key) => {
  launchApp(String(key).trim());
});

// ── 启动：每次都要求重新输入授权码 ──

app.whenReady().then(() => {
  showActivationWindow();
});

app.on('before-quit', () => {
  app.isQuitting = true;
  stopBackend();
});
app.on('window-all-closed', () => {
  app.isQuitting = true;
  stopBackend();
  app.quit();
});
app.on('quit', () => {
  stopBackend();
});
