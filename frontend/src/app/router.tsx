import { createBrowserRouter } from 'react-router'
import { HomePage } from '../features/home/HomePage.tsx'
import { AppLayout } from './AppLayout.tsx'
import { NotFoundPage } from './NotFoundPage.tsx'

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
