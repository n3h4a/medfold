import { createBrowserRouter, type RouteObject } from 'react-router'
import { GuestRoute } from '../auth/GuestRoute.tsx'
import { LoginPage } from '../auth/LoginPage.tsx'
import { ProtectedRoute } from '../auth/ProtectedRoute.tsx'
import { RegisterPage } from '../auth/RegisterPage.tsx'
import { DocumentsPage } from '../features/documents/DocumentsPage.tsx'
import { SystemStatus } from '../features/status/SystemStatus.tsx'
import { AppLayout } from './AppLayout.tsx'
import { NotFoundPage } from './NotFoundPage.tsx'

export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      {
        element: <GuestRoute />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
        ],
      },
      {
        element: <ProtectedRoute />,
        children: [
          { path: '/', element: <DocumentsPage /> },
          { path: '/status', element: <SystemStatus /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
