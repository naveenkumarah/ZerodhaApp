import { Navigate, Route, Routes } from 'react-router-dom';
import { useSelector } from 'react-redux';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import TradingPage from './pages/TradingPage';
import OptionChainPage from './pages/OptionChainPage';
import RiskCalculatorPage from './pages/RiskCalculatorPage';
import StrategyBuilderPage from './pages/StrategyBuilderPage';
import JournalPage from './pages/JournalPage';
import AnalyticsPage from './pages/AnalyticsPage';
import SettingsPage from './pages/SettingsPage';

function RequireAuth({ children }) {
  const token = useSelector((state) => state.auth.token);
  return token ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="trading" element={<TradingPage />} />
        <Route path="option-chain" element={<OptionChainPage />} />

        <Route path="risk-calculator" element={<RiskCalculatorPage />} />
        <Route path="strategy" element={<StrategyBuilderPage />} />
        <Route path="journal" element={<JournalPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

