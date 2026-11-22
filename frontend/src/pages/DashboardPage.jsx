import { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  Alert, Card, CardContent, Chip, Grid, List, ListItem, ListItemText, Stack, Typography,
} from '@mui/material';
import { api, errorMessage } from '../api/client';
import { fetchRiskStatus } from '../store/riskSlice';
import { MetricCard, formatMoney, formatNumber } from '../components/common';

const STATUS_COLOR = { GREEN: 'success.main', YELLOW: 'warning.main', RED: 'error.main' };
const STATUS_TEXT = { GREEN: 'Safe', YELLOW: 'Warning', RED: 'Trading locked' };

export default function DashboardPage() {
  const dispatch = useDispatch();
  const risk = useSelector((state) => state.risk);
  const [summary, setSummary] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    dispatch(fetchRiskStatus());
    Promise.all([api.get('/analytics/summary'), api.get('/journal/analysis')])
      .then(([s, a]) => {
        setSummary(s.data);
        setAnalysis(a.data);
      })
      .catch((err) => setError(errorMessage(err)));
  }, [dispatch]);

  const reasons = [...risk.locks, ...risk.warnings];

  return (
    <Stack spacing={3}>
      <Typography variant="h4">Dashboard</Typography>
      {error && <Alert severity="error">{error}</Alert>}

      <Card variant="outlined">
        <CardContent>
          <Typography variant="overline" color="text.secondary">Discipline status</Typography>
          <Typography variant="h4" sx={{ color: STATUS_COLOR[risk.status] || 'text.primary', fontWeight: 700 }}>
            {risk.status ? STATUS_TEXT[risk.status] : 'Loading…'}
          </Typography>
          {reasons.length > 0 && (
            <List dense>
              {reasons.map((r) => (
                <ListItem key={r} disableGutters>
                  <ListItemText primary={r} />
                </ListItem>
              ))}
            </List>
          )}
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="Total P&L" value={formatMoney(summary?.totalPnl)}
            color={summary && Number(summary.totalPnl) < 0 ? 'error.main' : 'success.main'} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="Win rate" value={summary?.winRate != null ? `${formatNumber(summary.winRate)}%` : '—'} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="Max drawdown" value={formatMoney(summary?.maxDrawdown)} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="Discipline score" value={analysis ? `${analysis.disciplineScore} / 100` : '—'} />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" gutterBottom>Coaching</Typography>
          <Stack direction="row" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
            {analysis && <Chip label={`Emotional trades: ${analysis.emotionalTrades}`} />}
            {analysis && <Chip label={`Overtrading days: ${analysis.overtradingDays}`} />}
            {analysis && <Chip label={`Revenge trades: ${analysis.revengeTrades}`} />}
          </Stack>
          <List dense>
            {(analysis?.suggestions || []).map((s) => (
              <ListItem key={s} disableGutters>
                <ListItemText primary={s} />
              </ListItem>
            ))}
          </List>
        </CardContent>
      </Card>
    </Stack>
  );
}

