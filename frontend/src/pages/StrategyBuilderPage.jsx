import { useState } from 'react';
import {
  Alert, Button, Card, CardContent, Grid, IconButton, MenuItem, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { api, errorMessage } from '../api/client';
import { MetricCard, formatMoney, formatNumber } from '../components/common';
import LineChart from '../components/LineChart';

const leg = (type, side, strike, premium) => ({ type, side, strike: String(strike), premium: String(premium) });

// Sample legs are only starting points; the user edits strikes and premiums to match the live chain.
const STRATEGIES = [
  {
    slug: 'iron-condor', label: 'Iron Condor',
    legs: [leg('CE', 'SELL', 22200, 80), leg('CE', 'BUY', 22400, 30), leg('PE', 'SELL', 21800, 70), leg('PE', 'BUY', 21600, 25)],
  },
  {
    slug: 'hedged-strangle', label: 'Hedged Strangle',
    legs: [leg('CE', 'SELL', 22200, 80), leg('CE', 'BUY', 22500, 20), leg('PE', 'SELL', 21800, 70), leg('PE', 'BUY', 21500, 25)],
  },
  {
    slug: 'iron-fly', label: 'Iron Fly',
    legs: [leg('CE', 'SELL', 22000, 300), leg('PE', 'SELL', 22000, 280), leg('CE', 'BUY', 22200, 60), leg('PE', 'BUY', 21800, 55)],
  },
  {
    slug: 'bull-put', label: 'Bull Put Spread',
    legs: [leg('PE', 'SELL', 21800, 70), leg('PE', 'BUY', 21600, 25)],
  },
  {
    slug: 'bear-call', label: 'Bear Call Spread',
    legs: [leg('CE', 'SELL', 22200, 80), leg('CE', 'BUY', 22400, 30)],
  },
];

const INITIAL = { underlying: 'NIFTY', spot: '22000', lotSize: '50', lots: '1', daysToExpiry: '7', ivPercent: '15', capital: '500000' };

export default function StrategyBuilderPage() {
  const [strategy, setStrategy] = useState(STRATEGIES[0]);
  const [form, setForm] = useState(INITIAL);
  const [legs, setLegs] = useState(STRATEGIES[0].legs);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const pickStrategy = (slug) => {
    const next = STRATEGIES.find((s) => s.slug === slug);
    setStrategy(next);
    setLegs(next.legs);
    setResult(null);
  };

  const updateForm = (field) => (e) => setForm({ ...form, [field]: e.target.value });
  const updateLeg = (index, field, value) =>
    setLegs(legs.map((l, i) => (i === index ? { ...l, [field]: value } : l)));

  const analyze = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    const body = {
      underlying: form.underlying,
      spot: Number(form.spot),
      lotSize: Number(form.lotSize),
      lots: Number(form.lots),
      daysToExpiry: Number(form.daysToExpiry),
      ivPercent: Number(form.ivPercent),
      capital: form.capital ? Number(form.capital) : null,
      legs: legs.map((l) => ({ type: l.type, side: l.side, strike: Number(l.strike), premium: Number(l.premium) })),
    };
    try {
      const { data } = await api.post(`/strategy/${strategy.slug}`, body);
      setResult(data);
    } catch (err) {
      setResult(null);
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Typography variant="h4">Strategy Builder</Typography>

      <Card variant="outlined">
        <CardContent>
          <Grid container spacing={2} component="form" onSubmit={analyze}>
            <Grid item xs={12} md={4}>
              <TextField select fullWidth label="Strategy" value={strategy.slug} onChange={(e) => pickStrategy(e.target.value)}>
                {STRATEGIES.map((s) => <MenuItem key={s.slug} value={s.slug}>{s.label}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={4}><TextField fullWidth label="Underlying" value={form.underlying} onChange={updateForm('underlying')} /></Grid>
            <Grid item xs={6} md={2}><TextField fullWidth type="number" label="Spot" value={form.spot} onChange={updateForm('spot')} /></Grid>
            <Grid item xs={6} md={2}><TextField fullWidth type="number" label="IV (%)" value={form.ivPercent} onChange={updateForm('ivPercent')} /></Grid>
            <Grid item xs={6} md={2}><TextField fullWidth type="number" label="Lot size" value={form.lotSize} onChange={updateForm('lotSize')} /></Grid>
            <Grid item xs={6} md={2}><TextField fullWidth type="number" label="Lots" value={form.lots} onChange={updateForm('lots')} /></Grid>
            <Grid item xs={6} md={2}><TextField fullWidth type="number" label="Days to expiry" value={form.daysToExpiry} onChange={updateForm('daysToExpiry')} /></Grid>
            <Grid item xs={6} md={2}><TextField fullWidth type="number" label="Capital (₹)" value={form.capital} onChange={updateForm('capital')} /></Grid>

            <Grid item xs={12}>
              <Typography variant="subtitle1" gutterBottom>Legs</Typography>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Type</TableCell><TableCell>Side</TableCell>
                    <TableCell>Strike</TableCell><TableCell>Premium</TableCell><TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {legs.map((l, i) => (
                    <TableRow key={i}>
                      <TableCell>
                        <TextField select size="small" value={l.type} onChange={(e) => updateLeg(i, 'type', e.target.value)}>
                          <MenuItem value="CE">CE</MenuItem><MenuItem value="PE">PE</MenuItem>
                        </TextField>
                      </TableCell>
                      <TableCell>
                        <TextField select size="small" value={l.side} onChange={(e) => updateLeg(i, 'side', e.target.value)}>
                          <MenuItem value="BUY">BUY</MenuItem><MenuItem value="SELL">SELL</MenuItem>
                        </TextField>
                      </TableCell>
                      <TableCell>
                        <TextField size="small" type="number" value={l.strike} onChange={(e) => updateLeg(i, 'strike', e.target.value)} />
                      </TableCell>
                      <TableCell>
                        <TextField size="small" type="number" value={l.premium} onChange={(e) => updateLeg(i, 'premium', e.target.value)} />
                      </TableCell>
                      <TableCell>
                        <IconButton aria-label="remove leg" onClick={() => setLegs(legs.filter((_, j) => j !== i))}>
                          <DeleteIcon />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Grid>

            <Grid item xs={12}>
              <Stack direction="row" spacing={2}>
                <Button variant="outlined" onClick={() => setLegs([...legs, leg('CE', 'BUY', form.spot, 0)])}>Add leg</Button>
                <Button type="submit" variant="contained" disabled={loading}>Analyze strategy</Button>
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {error && <Alert severity="error">{error}</Alert>}
      {result && <StrategyResult result={result} />}
    </Stack>
  );
}

function StrategyResult({ result }) {
  const payoff = (result.payoff || []).map((p) => ({ x: Number(p.price), y: Number(p.pnl) }));
  return (
    <Stack spacing={3}>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Net premium" value={formatMoney(result.netPremium)} /></Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="Max profit" value={result.unlimitedProfit ? 'Unlimited' : formatMoney(result.maxProfit)} color="success.main" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="Max loss" value={result.unlimitedLoss ? 'Unlimited' : formatMoney(result.maxLoss)} color="error.main" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}><MetricCard label="Margin required" value={formatMoney(result.marginRequired)} /></Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="ROI" value={result.roiPercent != null ? `${formatNumber(result.roiPercent)}%` : '—'} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="Probability of profit" value={result.probabilityOfProfit != null ? `${formatNumber(result.probabilityOfProfit)}%` : '—'} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="Breakevens" value={(result.breakevens || []).map((b) => formatNumber(b)).join(' / ') || '—'} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard label="Net delta" value={formatNumber(result.greeks?.delta, 4)} />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" gutterBottom>Payoff at expiry</Typography>
          <LineChart points={payoff} xLabel="Underlying price" yLabel="P&L" />
        </CardContent>
      </Card>
    </Stack>
  );
}

