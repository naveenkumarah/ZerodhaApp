import { useEffect, useState } from 'react';
import {
  Alert, Button, Card, CardContent, Checkbox, FormControlLabel, Grid, IconButton, MenuItem, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { api, errorMessage } from '../api/client';
import { MetricCard, formatMoney, formatNumber } from '../components/common';
import LineChart from '../components/LineChart';
// Legs are defined as strike offsets from the ATM strike (in strike steps), so every underlying works.
const leg = (type, side, offset) => ({ type, side, offset, strike: '', premium: '', stopLoss: '', target: '' });
const STRATEGIES = [
  {
    slug: 'iron-condor', label: 'Iron Condor',
    legs: [leg('CE', 'SELL', 2), leg('CE', 'BUY', 4), leg('PE', 'SELL', -2), leg('PE', 'BUY', -4)],
  },
  {
    slug: 'hedged-strangle', label: 'Hedged Strangle',
    legs: [leg('CE', 'SELL', 2), leg('CE', 'BUY', 5), leg('PE', 'SELL', -2), leg('PE', 'BUY', -5)],
  },
  {
    slug: 'iron-fly', label: 'Iron Fly',
    legs: [leg('CE', 'SELL', 0), leg('PE', 'SELL', 0), leg('CE', 'BUY', 2), leg('PE', 'BUY', -2)],
  },
  { slug: 'bull-put', label: 'Bull Put Spread', legs: [leg('PE', 'SELL', -2), leg('PE', 'BUY', -4)] },
  { slug: 'bear-call', label: 'Bear Call Spread', legs: [leg('CE', 'SELL', 2), leg('CE', 'BUY', 4)] },
];
const UNDERLYINGS = [
  'NIFTY', 'BANKNIFTY', 'FINNIFTY', 'MIDCPNIFTY',
  'RELIANCE', 'TCS', 'HDFCBANK', 'ICICIBANK', 'INFY', 'SBIN',
];
const CHECKLIST_ITEMS = [
  { key: 'trendConfirmed', label: 'Trend confirmed' },
  { key: 'supportResistanceMarked', label: 'Support/resistance marked' },
  { key: 'riskRewardAboveTwo', label: 'Risk:reward at least 1:2' },
  { key: 'stopLossDefined', label: 'Stop loss defined' },
  { key: 'positionSizeValid', label: 'Position size validated' },
];
const EMPTY_CHECKLIST = Object.fromEntries(CHECKLIST_ITEMS.map((i) => [i.key, false]));
const INITIAL = {
  underlying: 'NIFTY', expiry: '', spot: '', lotSize: '', lots: '1',
  daysToExpiry: '', ivPercent: '15', capital: '500000', product: 'NRML',
};
function daysUntil(expiry) {
  return Math.max(0, Math.ceil((new Date(expiry) - new Date()) / 86400000));
}
// Places each leg at ATM + offset and fills premium from the live LTP of that contract.
function legsFromChain(templateLegs, chain) {
  const atmIndex = chain ? chain.rows.findIndex((r) => r.atm) : -1;
  return templateLegs.map((l) => {
    const row = atmIndex >= 0 ? chain.rows[atmIndex + l.offset] : undefined;
    const quote = row ? row[l.type === 'CE' ? 'ce' : 'pe'] : null;
    return {
      ...l,
      strike: row ? String(row.strike) : '',
      premium: quote ? String(quote.ltp) : '',
    };
  });
}
export default function StrategyBuilderPage() {
  const [strategy, setStrategy] = useState(STRATEGIES[0]);
  const [form, setForm] = useState(INITIAL);
  const [chain, setChain] = useState(null);
  const [chainLoading, setChainLoading] = useState(false);
  const [chainError, setChainError] = useState(null);
  const [legs, setLegs] = useState(STRATEGIES[0].legs);
  const [checklist, setChecklist] = useState(EMPTY_CHECKLIST);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [executionLog, setExecutionLog] = useState([]);
  const loadChain = (symbol, expiry) => {
    setChainLoading(true);
    setChainError(null);
    setResult(null);
    setExecutionLog([]);
    const params = { underlying: symbol };
    if (expiry) params.expiry = expiry;
    api.get('/trading/option-chain', { params })
      .then(({ data }) => {
        setChain(data);
        setForm((f) => ({
          ...f,
          underlying: data.underlying,
          expiry: data.expiry,
          spot: String(data.spot),
          lotSize: String(data.lotSize),
          daysToExpiry: String(daysUntil(data.expiry)),
        }));
        setLegs(legsFromChain(legs, data));
      })
      .catch((err) => {
        setChain(null);
        setChainError(errorMessage(err));
      })
      .finally(() => setChainLoading(false));
  };
  useEffect(() => {
    loadChain('NIFTY', '');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const pickStrategy = (slug) => {
    const next = STRATEGIES.find((s) => s.slug === slug);
    setStrategy(next);
    setLegs(legsFromChain(next.legs, chain));
    setResult(null);
    setExecutionLog([]);
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
  const execute = async () => {
    setError(null);
    setExecutionLog([]);
    const missing = CHECKLIST_ITEMS.filter((i) => !checklist[i.key]).map((i) => i.label);
    if (missing.length > 0) {
      setError(`Checklist incomplete: ${missing.join(', ')}`);
      return;
    }
    // Resolve each leg to its tradeable contract in the loaded chain.
    const orders = legs.map((l) => {
      const row = chain.rows.find((r) => Number(r.strike) === Number(l.strike));
      const quote = row ? row[l.type === 'CE' ? 'ce' : 'pe'] : null;
      return { leg: l, tradingsymbol: quote ? quote.tradingsymbol : null };
    });
    const unresolved = orders.filter((o) => !o.tradingsymbol).map((o) => `${o.leg.type} ${o.leg.strike || '(no strike)'}`);
    if (unresolved.length > 0) {
      setError(`No contract in the loaded chain for: ${unresolved.join(', ')}`);
      return;
    }
    const quantity = Number(form.lots) * Number(form.lotSize);
    const confirmed = window.confirm(
      `Place ${orders.length} order(s) for ${strategy.label} on ${form.underlying} ${form.expiry}?\n` +
      `Quantity per leg: ${quantity} (${form.product}). Buy legs are placed first.`,
    );
    if (!confirmed) return;
    // Buy (hedge) legs first, so short legs are never placed unhedged.
    const rank = (side) => (side === 'BUY' ? 0 : 1);
    const ordered = [...orders].sort((a, b) => rank(a.leg.side) - rank(b.leg.side));
    setExecuting(true);
    const log = [];
    for (const { leg: l, tradingsymbol } of ordered) {
      const body = {
        tradingsymbol,
        exchange: 'NFO',
        transactionType: l.side,
        quantity,
        orderType: 'MARKET',
        product: form.product,
        entryPrice: Number(l.premium),
        stopLoss: l.stopLoss ? Number(l.stopLoss) : null,
        target: l.target ? Number(l.target) : null,
        strategy: strategy.label,
        estimatedMargin: null,
        checklist: { ...checklist },
      };
      try {
        const { data } = await api.post('/trading/order', body);
        log.push({ ok: true, text: `${l.side} ${tradingsymbol}: order ${data.orderId} (risk ${data.riskStatus})` });
        setExecutionLog([...log]);
      } catch (err) {
        log.push({ ok: false, text: `${l.side} ${tradingsymbol}: ${errorMessage(err)}` });
        log.push({ ok: false, text: 'Execution stopped. Remaining legs were not placed.' });
        setExecutionLog([...log]);
        break;
      }
    }
    setExecuting(false);
  };
  const quantity = Number(form.lots) * Number(form.lotSize);
  return (
    <Stack spacing={3}>
      <Typography variant="h4">Strategy Builder</Typography>
      <Card variant="outlined">
        <CardContent>
          <Grid container spacing={2} component="form" onSubmit={analyze}>
            <Grid item xs={12} md={3}>
              <TextField select fullWidth label="Underlying" value={form.underlying}
                onChange={(e) => { setForm({ ...form, underlying: e.target.value }); loadChain(e.target.value, ''); }}
                disabled={chainLoading}>
                {UNDERLYINGS.map((u) => <MenuItem key={u} value={u}>{u}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField select fullWidth label="Expiry" value={form.expiry}
                onChange={(e) => loadChain(form.underlying, e.target.value)}
                disabled={!chain || chainLoading}>
                {(chain?.expiries || []).map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField select fullWidth label="Strategy" value={strategy.slug} onChange={(e) => pickStrategy(e.target.value)}>
                {STRATEGIES.map((s) => <MenuItem key={s.slug} value={s.slug}>{s.label}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField select fullWidth label="Product" value={form.product} onChange={updateForm('product')}>
                <MenuItem value="NRML">NRML (carry forward)</MenuItem>
                <MenuItem value="MIS">MIS (intraday)</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={6} md={3}><TextField fullWidth type="number" label="Spot" value={form.spot} onChange={updateForm('spot')} /></Grid>
            <Grid item xs={6} md={3}><TextField fullWidth type="number" label="Lot size" value={form.lotSize} onChange={updateForm('lotSize')} /></Grid>
            <Grid item xs={6} md={3}><TextField fullWidth type="number" label="Lots" value={form.lots} onChange={updateForm('lots')} /></Grid>
            <Grid item xs={6} md={3}><TextField fullWidth type="number" label="IV (%)" value={form.ivPercent} onChange={updateForm('ivPercent')} /></Grid>
            <Grid item xs={6} md={3}><TextField fullWidth type="number" label="Days to expiry" value={form.daysToExpiry} onChange={updateForm('daysToExpiry')} /></Grid>
            <Grid item xs={6} md={3}><TextField fullWidth type="number" label="Capital (₹)" value={form.capital} onChange={updateForm('capital')} /></Grid>
            <Grid item xs={12} md={6}>
              <Typography variant="body2" color="text.secondary" sx={{ pt: 1 }}>
                ATM strike {chain ? formatNumber(chain.atmStrike, 0) : '—'} · Quantity per leg {quantity || '—'}
              </Typography>
            </Grid>
            {chainLoading && <Grid item xs={12}><Typography color="text.secondary">Loading option chain…</Typography></Grid>}
            {chainError && (
              <Grid item xs={12}>
                <Alert severity="warning">{chainError}. Connect your Zerodha account in Settings first.</Alert>
              </Grid>
            )}
            <Grid item xs={12}>
              <Typography variant="subtitle1" gutterBottom>Legs</Typography>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Type</TableCell><TableCell>Side</TableCell>
                    <TableCell>Strike</TableCell><TableCell>Premium (LTP)</TableCell>
                    <TableCell>Stop loss (premium)</TableCell><TableCell>Target (premium)</TableCell><TableCell />
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
                        <TextField size="small" type="number" value={l.stopLoss} onChange={(e) => updateLeg(i, 'stopLoss', e.target.value)} />
                      </TableCell>
                      <TableCell>
                        <TextField size="small" type="number" value={l.target} onChange={(e) => updateLeg(i, 'target', e.target.value)} />
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
                <Button variant="outlined" onClick={() => setLegs([...legs, leg('CE', 'BUY', 0)])}>Add leg</Button>
                <Button type="submit" variant="contained" disabled={loading || !chain}>Analyze strategy</Button>
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" gutterBottom>Execute trade</Typography>
          <Grid container spacing={1}>
            {CHECKLIST_ITEMS.map((item) => (
              <Grid item xs={12} sm={6} md={4} key={item.key}>
                <FormControlLabel
                  label={item.label}
                  control={
                    <Checkbox checked={checklist[item.key]}
                      onChange={(e) => setChecklist({ ...checklist, [item.key]: e.target.checked })} />
                  }
                />
              </Grid>
            ))}
          </Grid>
          <Stack direction="row" spacing={2} sx={{ mt: 2 }} alignItems="center">
            <Button variant="contained" color="error" onClick={execute}
              disabled={executing || !chain || legs.length === 0}>
              {executing ? 'Placing orders…' : 'Execute strategy'}
            </Button>
            <Typography variant="body2" color="text.secondary">
              Every leg passes the discipline gate. Stop loss and target are required per leg.
            </Typography>
          </Stack>
          {executionLog.length > 0 && (
            <Stack spacing={1} sx={{ mt: 2 }}>
              {executionLog.map((entry, i) => (
                <Alert key={i} severity={entry.ok ? 'success' : 'error'}>{entry.text}</Alert>
              ))}
            </Stack>
          )}
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
