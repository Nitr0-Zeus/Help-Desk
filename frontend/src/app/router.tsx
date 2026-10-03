import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute, PublicRoute } from '../auth/guards'
import { AppLayout } from '../components/layout/AppLayout'
import LoginPage from '../auth/pages/LoginPage'
import RegisterPage from '../auth/pages/RegisterPage'
import TicketsPage from '../tickets/pages/TicketsPage'
import NewTicketPage from '../tickets/pages/NewTicketPage'
import TicketDetailPage from '../tickets/pages/TicketDetailPage'
import MetricsPage from '../metrics/pages/MetricsPage'
import SlaPoliciesPage from '../sla/pages/SlaPoliciesPage'
import ProfilePage from '../profile/pages/ProfilePage'
import NotFoundPage from './NotFoundPage'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<PublicRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/tickets" replace />} />
          <Route path="/tickets" element={<TicketsPage />} />
          <Route path="/tickets/new" element={<NewTicketPage />} />
          <Route path="/tickets/:id" element={<TicketDetailPage />} />
          <Route path="/metrics" element={<MetricsPage />} />
          <Route path="/sla-policies" element={<SlaPoliciesPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
