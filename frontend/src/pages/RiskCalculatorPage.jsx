import { useState } from 'react';
import {
  Alert, Button, Card, CardContent, Grid, MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { api, errorMessage } from '../api/client';
import { MetricCard, formatMoney, formatNumber } from '../components/common';

const METHODS = [
  { value: 'PERCENTAGE_RISK', label: 'Percentage risk (capital × risk % ÷ stop distance)' },
  { value: 'FIXED_RISK', label: 'Fixed rupee risk' },
  { value: 'ATR', label: 'ATR based' },
  { value: 'VOLATILITY', label: 'Volatility based' },
  { value: 'KELLY', label: 'Kelly criterion (half-Kelly, capped at risk %)' },
];

const INITIAL = {
  method: 'PERCENTAGE_RISK',
  capital: '1000000',
  riskPercent: '1',
  fixedRiskAmount: '',
  entryPrice: '250',
  stopLoss: '240',
  atr: '',
  atrMultiplier: '',
  volatilityPercent: '',
  winRate: '',
  avgWin: '',
  avgLoss: '',
  lotSize: '',
  maxExposurePercent: '',
};

const NUMERIC_FIELDS = Object.keys(INITIAL).filter((k) => k !== 'method');

// Blank inputs are omitted so the backend treats them as absent, not zero.
function toPayload(form) {
  const body = { method: form.method };
  for (const key of NUMERIC_FIELDS) {
    if (form[key] !== '') body[key] = Number(form[key]);
  }
  return body;
}

export default function RiskCalculatorPage() {
  const [form, setForm] = useState(INITIAL);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.post('/risk/calculate-position-size', toPayload(form));
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
      <Typography variant="h4">Risk Calculator</Typography>
      <Card variant="outlined">
        <CardContent>
          <Grid container spacing={2} component="form" onSubmit={submit}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Sizing method" value={form.method} onChange={update('method')}>
                {METHODS.map((m) => <MenuItem key={m.value} value={m.value}>{m.label}</MenuItem>)}
              </TextField>
            </Grid>
            <Field form={form} update={update} name="capital" label="Capital (₹)" />
            <Field form={form} update={update} name="riskPercent" label="Risk per trade (%)" />
            <Field form={form} update={update} name="entryPrice" label="Entry price" />
            <Field form={form} update={update} name="stopLoss" label="Stop loss" />
            <Field form={form} update={update} name="fixedRiskAmount" label="Fixed risk amount (₹)" />
            <Field form={form} update={update} name="atr" label="ATR" />
            <Field form={form} update={update} name="atrMultiplier" label="ATR multiplier" />
            <Field form={form} update={update} name="volatilityPercent" label="Volatility (%)" />
            <Field form={form} update={update} name="winRate" label="Win rate (0–1)" />
            <Field form={form} update={update} name="avgWin" label="Average win (₹)" />
            <Field form={form} update={update} name="avgLoss" label="Average loss (₹)" />
            <Field form={form} update={update} name="lotSize" label="Lot size" />
            <Field form={form} update={update} name="maxExposurePercent" label="Max exposure (% of capital)" />
            <Grid item xs={12}>
              <Button type="submit" variant="contained" disabled={loading}>Calculate position size</Button>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {error && <Alert severity="error">{error}</Alert>}

      {result && (
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={3}><MetricCard label="Quantity" value={formatNumber(result.quantity, 0)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><MetricCard label="Risk per share" value={formatMoney(result.riskPerShare)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><MetricCard label="Total risk" value={formatMoney(result.riskAmount)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><MetricCard label="Position value" value={formatMoney(result.positionValue)} /></Grid>
          <Grid item xs={12}>
            <Typography variant="body2" color="text.secondary">
              Exposure: {formatNumber(result.exposurePercent)}% of capital
            </Typography>
          </Grid>
        </Grid>
      )}
    </Stack>
  );
}

function Field({ form, update, name, label }) {
  return (
    <Grid item xs={12} sm={6} md={4}>
      <TextField fullWidth label={label} type="number" value={form[name]} onChange={update(name)} />
    </Grid>
  );
}

