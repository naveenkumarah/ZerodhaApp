import { useEffect, useState } from 'react';
import {
  Alert, Autocomplete, Button, Card, CardContent, Chip, Grid, MenuItem, Paper, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { api, errorMessage } from '../api/client';
import { MetricCard, formatMoney, formatNumber } from '../components/common';
// Common underlyings. Any other stock F&O symbol can be typed in.
const UNDERLYINGS = [
  'NIFTY', 'BANKNIFTY', 'FINNIFTY', 'MIDCPNIFTY',
  'RELIANCE', 'TCS', 'HDFCBANK', 'ICICIBANK', 'INFY', 'SBIN',
];
export default function OptionChainPage() {
  const [input, setInput] = useState('NIFTY');
  const [underlying, setUnderlying] = useState('NIFTY');
  const [expiry, setExpiry] = useState('');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const load = (symbol, exp) => {
    setLoading(true);
    setError(null);
    const params = { underlying: symbol };
    if (exp) params.expiry = exp;
    api.get('/trading/option-chain', { params })
      .then(({ data: body }) => {
        setData(body);
        setExpiry(body.expiry);
      })
      .catch((err) => {
        setData(null);
        setError(errorMessage(err));
      })
      .finally(() => setLoading(false));
  };
  // Load the nearest expiry whenever a different underlying is chosen.
  useEffect(() => {
    load(underlying, '');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [underlying]);
  const submit = () => {
    const symbol = input.trim().toUpperCase();
    if (!symbol) return;
    if (symbol === underlying) {
      load(symbol, '');
    } else {
      setUnderlying(symbol);
    }
  };
  const onExpiryChange = (e) => {
    setExpiry(e.target.value);
    load(underlying, e.target.value);
  };
  return (
    <Stack spacing={3}>
      <Typography variant="h4">Option Chain</Typography>
      <Card variant="outlined">
        <CardContent>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={4}>
              <Autocomplete
                freeSolo
                options={UNDERLYINGS}
                inputValue={input}
                onInputChange={(_, v) => setInput(v)}
                onChange={(_, v) => { if (v) { setInput(v); setUnderlying(v.toUpperCase()); } }}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Index or stock"
                    helperText="e.g. NIFTY, BANKNIFTY, RELIANCE"
                    onKeyDown={(e) => { if (e.key === 'Enter') submit(); }}
                  />
                )}
              />
            </Grid>
            <Grid item xs={6} md={2}>
              <Button variant="contained" fullWidth onClick={submit} disabled={loading}>Load</Button>
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField
                select
                fullWidth
                label="Expiry"
                value={expiry}
                onChange={onExpiryChange}
                disabled={!data || loading}
              >
                {(data?.expiries || []).map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
              </TextField>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
      {error && <Alert severity="warning">{error}. Connect your Zerodha account in Settings first.</Alert>}
      {loading && <Typography color="text.secondary">Loading…</Typography>}
      {data && !loading && (
        <>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6} md={3}><MetricCard label="Underlying" value={data.underlying} /></Grid>
            <Grid item xs={12} sm={6} md={3}><MetricCard label="Spot" value={formatMoney(data.spot)} /></Grid>
            <Grid item xs={12} sm={6} md={3}><MetricCard label="ATM strike" value={formatNumber(data.atmStrike, 0)} /></Grid>
            <Grid item xs={12} sm={6} md={3}><MetricCard label="Lot size" value={data.lotSize} /></Grid>
          </Grid>
          <Card variant="outlined">
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                <Typography variant="h6">Expiry {data.expiry}</Typography>
                <Chip size="small" label="ATM highlighted" />
              </Stack>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell align="right">CE OI</TableCell>
                      <TableCell align="right">CE Volume</TableCell>
                      <TableCell align="right" sx={{ color: 'success.main', fontWeight: 600 }}>CE LTP</TableCell>
                      <TableCell align="center" sx={{ fontWeight: 700 }}>Strike</TableCell>
                      <TableCell align="left" sx={{ color: 'error.main', fontWeight: 600 }}>PE LTP</TableCell>
                      <TableCell align="left">PE Volume</TableCell>
                      <TableCell align="left">PE OI</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(data.rows || []).map((row) => (
                      <TableRow key={String(row.strike)} sx={row.atm ? { bgcolor: 'warning.light' } : undefined}>
                        <TableCell align="right">{row.ce ? formatNumber(row.ce.oi, 0) : '—'}</TableCell>
                        <TableCell align="right">{row.ce ? formatNumber(row.ce.volume, 0) : '—'}</TableCell>
                        <TableCell align="right">{row.ce ? formatNumber(row.ce.ltp) : '—'}</TableCell>
                        <TableCell align="center" sx={{ fontWeight: 700 }}>{formatNumber(row.strike, 0)}</TableCell>
                        <TableCell align="left">{row.pe ? formatNumber(row.pe.ltp) : '—'}</TableCell>
                        <TableCell align="left">{row.pe ? formatNumber(row.pe.volume, 0) : '—'}</TableCell>
                        <TableCell align="left">{row.pe ? formatNumber(row.pe.oi, 0) : '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                Strikes within 10 of ATM. LTP, OI and volume from Zerodha quotes.
              </Typography>
            </CardContent>
          </Card>
        </>
      )}
    </Stack>
  );
}
