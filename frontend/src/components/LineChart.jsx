import { Box, Typography } from '@mui/material';

// Minimal SVG line chart. points: [{ x, y }]. Used for payoff graphs and equity curves.
export default function LineChart({ points, width = 640, height = 300, xLabel = '', yLabel = '' }) {
  if (!points || points.length < 2) {
    return <Typography color="text.secondary">Not enough data to chart.</Typography>;
  }

  const pad = 40;
  const xs = points.map((p) => p.x);
  const ys = points.map((p) => p.y);
  const xMin = Math.min(...xs);
  const xMax = Math.max(...xs);
  const yMin = Math.min(0, ...ys);
  const yMax = Math.max(0, ...ys);
  const xSpan = xMax - xMin || 1;
  const ySpan = yMax - yMin || 1;

  const sx = (x) => pad + ((x - xMin) / xSpan) * (width - pad * 2);
  const sy = (y) => height - pad - ((y - yMin) / ySpan) * (height - pad * 2);

  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${sx(p.x).toFixed(1)},${sy(p.y).toFixed(1)}`).join(' ');
  const zeroY = sy(0);
  const lastY = points[points.length - 1].y;

  return (
    <Box>
      <svg viewBox={`0 0 ${width} ${height}`} width="100%" style={{ maxWidth: width }} role="img" aria-label={yLabel || 'chart'}>
        <rect x="0" y="0" width={width} height={height} fill="#fff" />
        <line x1={pad} x2={width - pad} y1={zeroY} y2={zeroY} stroke="#9e9e9e" strokeDasharray="4 4" />
        <line x1={pad} x2={pad} y1={pad} y2={height - pad} stroke="#bdbdbd" />
        <text x={pad - 6} y={pad} textAnchor="end" fontSize="11" fill="#616161">{formatAxis(yMax)}</text>
        <text x={pad - 6} y={height - pad} textAnchor="end" fontSize="11" fill="#616161">{formatAxis(yMin)}</text>
        <text x={width - pad} y={height - 8} textAnchor="end" fontSize="11" fill="#616161">{xLabel}</text>
        <path d={path} fill="none" stroke={lastY >= 0 ? '#2e7d32' : '#c62828'} strokeWidth="2" />
      </svg>
    </Box>
  );
}

function formatAxis(v) {
  return Number(v).toLocaleString('en-IN', { maximumFractionDigits: 0 });
}

