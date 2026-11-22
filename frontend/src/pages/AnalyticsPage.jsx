import { useEffect, useState } from 'react';
import {
  Alert, Card, CardContent, Grid, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { api, errorMessage } from '../api/client';
import { MetricCard, formatMoney, formatNumber } from '../components/common';
import LineChart from '../components/LineChart';

export default function AnalyticsPage() {
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.get('/analytics/summary')
      .then(({ data }) => setSummary(data))
      .catch((err) => setError(errorMessage(err)));
  }, []);

  if (error) return <Alert severity="error">{error}</Alert>;
  if (!summary) return <Typography color="text.secondary">Loading…</Typography>;

  const equity = (summary.equityCurve || []).map((y, i) => ({ x: i + 1, y: Number(y) }));
  const pct = (v) => (v != null ? `${formatNumber(v)}%` : '—');

  return (
    <Stack spacing={3}>
      <Typography variant="h4">Analytics</Typography>

      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Closed trades" value={summary.trades} /></Grid>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Win rate" value={pct(summary.winRate)} /></Grid>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Profit factor" value={summary.profitFactor ?? '—'} /></Grid>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Expectancy / trade" value={formatMoney(summary.expectancy)} /></Grid>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Average winner" value={formatMoney(summary.averageWinner)} color="success.main" /></Grid>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Average loser" value={formatMoney(summary.averageLoser)} color="error.main" /></Grid>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Risk : reward" value={summary.riskRewardRatio ?? '—'} /></Grid>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Sharpe (per trade)" value={summary.sharpeRatio ?? '—'} /></Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" gutterBottom>Equity curve</Typography>
          <LineChart points={equity} xLabel="Trade #" yLabel="Cumulative P&L" />
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="h6" gutterBottom>Monthly P&L</Typography>
              <BreakdownTable data={summary.monthlyPnl} labelHeader="Month" />
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={6}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="h6" gutterBottom>P&L by strategy</Typography>
              <BreakdownTable data={summary.pnlByStrategy} labelHeader="Strategy" />
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}

function BreakdownTable({ data, labelHeader }) {
  const rows = Object.entries(data || {});
  if (rows.length === 0) return <Typography color="text.secondary">No data yet.</Typography>;
  return (
    <Table size="small">
      <TableHead>
        <TableRow><TableCell>{labelHeader}</TableCell><TableCell align="right">P&L</TableCell></TableRow>
      </TableHead>
      <TableBody>
        {rows.map(([key, value]) => (
          <TableRow key={key}>
            <TableCell>{key}</TableCell>
            <TableCell align="right" sx={{ color: Number(value) < 0 ? 'error.main' : 'success.main' }}>
              {formatMoney(value)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

