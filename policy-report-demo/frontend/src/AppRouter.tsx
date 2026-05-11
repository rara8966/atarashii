import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { isLoggedIn, clearToken } from './auth';
import LegacyApp from './App';
import Login from './pages/Login';
import ProjectList from './pages/ProjectList';
import ProjectUpload from './pages/ProjectUpload';
import TypeConfirm from './pages/TypeConfirm';
import InfoConfirm from './pages/InfoConfirm';
import Workbench from './pages/Workbench';
import Preview from './pages/Preview';
import StandardLibrary from './pages/StandardLibrary';

function AuthGuard({ children }: { children: React.ReactNode }) {
  // 演示模式：后端已 permitAll，前端不再强制登录。
  // token 过期时只清掉本地 token，不跳登录页——用户以"游客"身份继续使用，
  // 想要标记角色再点 NavBar 的"登录"。
  if (!isLoggedIn()) {
    clearToken();
  }
  return <>{children}</>;
}

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<AuthGuard><ProjectList /></AuthGuard>} />
        <Route path="/projects/:id/upload" element={<AuthGuard><ProjectUpload /></AuthGuard>} />
        <Route path="/projects/:id/type-confirm" element={<AuthGuard><TypeConfirm /></AuthGuard>} />
        <Route path="/projects/:id/info-confirm" element={<AuthGuard><InfoConfirm /></AuthGuard>} />
        <Route path="/projects/:id/workbench" element={<AuthGuard><Workbench /></AuthGuard>} />
        <Route path="/projects/:id/preview" element={<AuthGuard><Preview /></AuthGuard>} />
        <Route path="/standards" element={<AuthGuard><StandardLibrary /></AuthGuard>} />
        <Route path="/legacy" element={<LegacyApp />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
