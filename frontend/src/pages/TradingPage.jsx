import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert, Button, Card, CardContent, Grid, Link, Paper, Stack, Tab, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Tabs, Typography,
} from '@mui/material';
import { api, errorMessage } from '../api/client';
import { MetricCard, formatMoney } from '../components/common';

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

function PositionsView({ data }) {
  if (!data) return <Typography color="text.secondary">Loading…</Typography>;
  const rows = data.net || [];
  return (
    <Card variant="outlined">
      <CardContent>
        <SimpleTable
          empty="No open positions"
          columns={['Symbol', 'Exchange', 'Product', 'Qty', 'Avg price', 'Last price', 'P&L']}
          rows={rows.map((p) => [
            p.tradingsymbol, p.exchange, p.product, p.quantity,
            p.average_price, p.last_price, formatMoney(p.pnl),
          ])}
        />
      </CardContent>
    </Card>
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

