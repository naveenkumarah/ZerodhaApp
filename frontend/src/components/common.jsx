import { Card, CardContent, Typography } from '@mui/material';

export function formatMoney(value) {
  if (value === null || value === undefined) return '—';
  const n = Number(value);
  return `₹${n.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
}

export function formatNumber(value, digits = 2) {
  if (value === null || value === undefined) return '—';
  return Number(value).toLocaleString('en-IN', { maximumFractionDigits: digits });
}

export function MetricCard({ label, value, color }) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="body2" color="text.secondary">{label}</Typography>
        <Typography variant="h5" sx={{ fontWeight: 600, color: color || 'text.primary' }}>
          {value}
        </Typography>
      </CardContent>
    </Card>
  );
}

