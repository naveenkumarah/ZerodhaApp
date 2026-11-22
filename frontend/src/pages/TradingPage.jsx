import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert, Button, Card, CardContent, Grid, Link, Paper, Stack, Tab, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Tabs, Typography,
} from '@mui/material';
import { api, errorMessage } from '../api/client';
import { MetricCard, formatMoney } from '../components/common';
import LineChart from '../components/LineChart';

const TABS = ['Margins', 'Positions', 'Orders'];

export default function TradingPage() {
  const [tab, setTab] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = (index) => {
    setLoading(true);
    setError(null);
    const endpoints = ['/trading/margins', '/trading/positions', '/trading/orders'];
    api.get(endpoints[index])
      .then(({ data: body }) => setData(body))
      .catch((err) => {
        setData(null);
        setError(errorMessage(err));
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load(tab);
  }, [tab]);

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h4">Trading</Typography>
        <Button variant="outlined" onClick={() => load(tab)} disabled={loading}>Refresh</Button>
      </Stack>

      <Tabs value={tab} onChange={(_, v) => setTab(v)}>
        {TABS.map((t) => <Tab key={t} label={t} />)}
      </Tabs>

      {error && (
        <Alert severity="warning">
          {error}. Connect your Zerodha account in <Link component={RouterLink} to="/settings">Settings</Link> first.
        </Alert>
      )}

      {!error && tab === 0 && <MarginsView data={data} />}
      {!error && tab === 1 && <PositionsView data={data} />}
      {!error && tab === 2 && <OrdersView data={data} />}
    </Stack>
  );
}

function MarginsView({ data }) {
  if (!data) return <Typography color="text.secondary">Loading…</Typography>;
  return (
    <Grid container spacing={2}>
      <Grid item xs={12} md={4}>
        <MetricCard label="Available margin" value={formatMoney(data.availableMargin)} />
      </Grid>
      <Grid item xs={12} md={4}>
        <MetricCard label="Used margin" value={formatMoney(data.usedMargin)} />
      </Grid>
    </Grid>
  );
}

// Longer prefixes must come first so GOLDM matches before GOLD, BANKNIFTY before NIFTY.
const UNDERLYINGS = [
  'MIDCPNIFTY', 'BANKNIFTY', 'FINNIFTY', 'NIFTY', 'SENSEX', 'BANKEX',
  'GOLDPETAL', 'GOLDGUINEA', 'GOLDM', 'GOLD',
  'SILVERMIC', 'SILVERM', 'SILVER',
  'CRUDEOILM', 'CRUDEOIL', 'NATURALGAS', 'COPPER', 'ZINC', 'ALUMINIUM', 'LEAD', 'NICKEL',
];

// "NIFTY26NOV22000CE" -> "NIFTY", "GOLD26DECFUT" -> "GOLD", "RELIANCE" -> "RELIANCE".
// A known underlying must be followed by a digit, so NIFTYBEES stays its own symbol.
function underlyingOf(tradingsymbol) {
  const sym = (tradingsymbol || '').toUpperCase();
  for (const u of UNDERLYINGS) {
    if (sym.startsWith(u) && /\d/.test(sym.charAt(u.length))) return u;
  }
  return sym;
}

// Short positions (negative qty) in red, long positions (positive qty) in blue.
function qtyCell(quantity) {
  const n = Number(quantity);
  const color = n < 0 ? '#d32f2f' : n > 0 ? '#1565c0' : undefined;
  return <span style={{ color, fontWeight: 600 }}>{quantity}</span>;
}

// "NIFTY26NOV22000CE" -> { strike: 22000, type: 'CE' }; futures/equity -> null.
function parseOption(tradingsymbol) {
  const m = /(\d+(?:\.\d+)?)(CE|PE)$/.exec((tradingsymbol || '').toUpperCase());
  return m ? { strike: Number(m[1]), type: m[2] } : null;
}

// P&L at expiry across all legs of a group, sampled over a price range around the strikes.
// Options use intrinsic value minus the average premium; futures/equity are linear.
function payoffCurve(positions) {
  const legs = positions
    .map((p) => ({ qty: Number(p.quantity), avg: Number(p.average_price || 0), opt: parseOption(p.tradingsymbol) }))
    .filter((l) => l.qty !== 0);
  const anchors = legs.filter((l) => l.opt).map((l) => l.opt.strike);
  if (anchors.length === 0) return [];

  const lo = Math.min(...anchors) * 0.8;
  const hi = Math.max(...anchors) * 1.2;
  const steps = 60;
  const points = [];
  for (let i = 0; i <= steps; i += 1) {
    const x = lo + ((hi - lo) * i) / steps;
    let pnl = 0;
    for (const l of legs) {
      if (l.opt) {
        const intrinsic = l.opt.type === 'CE' ? Math.max(x - l.opt.strike, 0) : Math.max(l.opt.strike - x, 0);
        pnl += l.qty * (intrinsic - l.avg);
      } else {
        pnl += l.qty * (x - l.avg);
      }
    }
    points.push({ x: Math.round(x), y: Math.round(pnl) });
  }
  return points;
}

function PositionsView({ data }) {
  if (!data) return <Typography color="text.secondary">Loading…</Typography>;
  const rows = data.net || [];
  if (rows.length === 0) {
    return (
      <Card variant="outlined">
        <CardContent>
          <Typography color="text.secondary">No open positions</Typography>
        </CardContent>
      </Card>
    );
  }

  // Group by underlying (NIFTY, BANKNIFTY, GOLD, ...). Each group's P&L is the sum of its legs.
  const groups = new Map();
  for (const p of rows) {
    const key = underlyingOf(p.tradingsymbol);
    if (!groups.has(key)) groups.set(key, { symbol: key, pnl: 0, positions: [] });
    const group = groups.get(key);
    group.pnl += Number(p.pnl || 0);
    group.positions.push(p);
  }
  const totalPnl = rows.reduce((sum, p) => sum + Number(p.pnl || 0), 0);
  const pnlColor = (v) => (v < 0 ? 'error.main' : 'success.main');

  return (
    <Stack spacing={2}>
      <MetricCard label="Total P&L" value={formatMoney(totalPnl)} color={pnlColor(totalPnl)} />
      {[...groups.values()].map((g) => (
        <Card variant="outlined" key={g.symbol}>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <Typography variant="h6">{g.symbol}</Typography>
              <Typography variant="h6" sx={{ color: pnlColor(g.pnl), fontWeight: 600 }}>
                {formatMoney(g.pnl)}
              </Typography>
            </Stack>
            <SimpleTable
              empty="No open positions"
              columns={['Exchange', 'Instrument', 'Product', 'Qty', 'Avg price', 'Last price', 'P&L']}
              rows={g.positions.map((p) => [
                p.exchange, p.tradingsymbol, p.product, qtyCell(p.quantity),
                p.average_price, p.last_price, formatMoney(p.pnl),
              ])}
            />
            {payoffCurve(g.positions).length > 0 && (
              <>
                <Typography variant="subtitle2" sx={{ mt: 2 }}>Payoff at expiry</Typography>
                <LineChart points={payoffCurve(g.positions)} xLabel="Underlying price" yLabel="P&L" />
              </>
            )}
          </CardContent>
        </Card>
      ))}
    </Stack>
  );
}

function OrdersView({ data }) {
  if (!data) return <Typography color="text.secondary">Loading…</Typography>;
  const rows = Array.isArray(data) ? data : [];
  return (
    <Card variant="outlined">
      <CardContent>
        <SimpleTable
          empty="No orders today"
          columns={['Order ID', 'Symbol', 'Side', 'Qty', 'Type', 'Status']}
          rows={rows.map((o) => [
            o.order_id, o.tradingsymbol, o.transaction_type, o.quantity, o.order_type, o.status,
          ])}
        />
      </CardContent>
    </Card>
  );
}

function SimpleTable({ columns, rows, empty }) {
  if (rows.length === 0) {
    return <Typography color="text.secondary">{empty}</Typography>;
  }
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            {columns.map((c) => <TableCell key={c}>{c}</TableCell>)}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((cells, i) => (
            <TableRow key={i}>
              {cells.map((cell, j) => <TableCell key={j}>{cell ?? '—'}</TableCell>)}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

