import { useEffect, useRef, useState } from 'react';
import { useDispatch } from 'react-redux';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert, Button, Card, CardContent, Divider, Grid, Stack, TextField, Typography,
} from '@mui/material';
import { api, errorMessage } from '../api/client';
import { logout } from '../store/authSlice';
import { fetchRiskStatus } from '../store/riskSlice';
const FIELDS = [
  { key: 'capital', label: 'Capital (₹)' },
  { key: 'riskPercentage', label: 'Risk per trade (%)' },
  { key: 'dailyLossLimit', label: 'Daily loss limit (%)' },
  { key: 'weeklyLossLimit', label: 'Weekly loss limit (%)' },
  { key: 'monthlyLossLimit', label: 'Monthly loss limit (%)' },
];
export default function SettingsPage() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const handledRedirect = useRef(false);
  const [form, setForm] = useState(null);
  const [requestToken, setRequestToken] = useState('');
  const [kiteUserId, setKiteUserId] = useState(null);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => {
    api.get('/risk/profile')
      .then(({ data }) => {
        const next = {};
        FIELDS.forEach(({ key }) => { next[key] = data[key] ?? ''; });
        setForm(next);
      })
      .catch((err) => setError(errorMessage(err)));
  }, []);
  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });
  const saveProfile = async (e) => {
    e.preventDefault();
    setError(null);
    setMessage(null);
    const body = {};
    FIELDS.forEach(({ key }) => { body[key] = Number(form[key]); });
    try {
      const { data } = await api.put('/risk/profile', body);
      const next = {};
      FIELDS.forEach(({ key }) => { next[key] = data[key] ?? ''; });
      setForm(next);
      setMessage('Risk profile saved.');
      dispatch(fetchRiskStatus());
    } catch (err) {
      setError(errorMessage(err));
    }
  };
  const connectKite = async () => {
    setError(null);
    try {
      const { data } = await api.get('/auth/kite/login-url');
      window.open(data.url, '_blank', 'noopener');
    } catch (err) {
      setError(errorMessage(err));
    }
  };
  const exchangeToken = async (token) => {
    setError(null);
    setMessage(null);
    try {
      const { data } = await api.post('/auth/kite/callback', { requestToken: token.trim() });
      setKiteUserId(data.kiteUserId);
      setRequestToken('');
      setMessage('Zerodha account connected.');
    } catch (err) {
      setError(errorMessage(err));
    }
  };
  const completeKite = (e) => {
    e.preventDefault();
    exchangeToken(requestToken);
  };
  // Kite redirects here with ?request_token=...&status=success after login. Exchange it once.
  useEffect(() => {
    const token = searchParams.get('request_token');
    if (!token || handledRedirect.current) return;
    handledRedirect.current = true;
    const status = searchParams.get('status');
    setSearchParams({}, { replace: true });
    if (status && status !== 'success') {
      setError('Zerodha login was not successful. Please try again.');
      return;
    }
    exchangeToken(token);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);
  const handleLogout = () => {
    dispatch(logout());
    navigate('/login');
  };
  return (
    <Stack spacing={3}>
      <Typography variant="h4">Settings</Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {message && <Alert severity="success">{message}</Alert>}
      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" gutterBottom>Risk profile</Typography>
          {!form ? (
            <Typography color="text.secondary">Loading…</Typography>
          ) : (
            <Grid container spacing={2} component="form" onSubmit={saveProfile}>
              {FIELDS.map(({ key, label }) => (
                <Grid item xs={12} sm={6} md={4} key={key}>
                  <TextField
                    fullWidth
                    type="number"
                    label={label}
                    value={form[key]}
                    onChange={update(key)}
                    required
                    inputProps={{ min: 0, step: 'any' }}
                  />
                </Grid>
              ))}
              <Grid item xs={12}>
                <Button type="submit" variant="contained">Save risk profile</Button>
              </Grid>
            </Grid>
          )}
        </CardContent>
      </Card>
      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" gutterBottom>Zerodha Kite connection</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Click Connect to log in to Zerodha. After login you are redirected back here and the
            connection completes automatically. You can also paste a request token manually below.
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="flex-start">
            <Button variant="outlined" onClick={connectKite}>Connect Zerodha</Button>
            <Stack component="form" direction={{ xs: 'column', sm: 'row' }} spacing={2} onSubmit={completeKite}>
              <TextField
                label="Request token"
                value={requestToken}
                onChange={(e) => setRequestToken(e.target.value)}
                required
                sx={{ minWidth: 320 }}
              />
              <Button type="submit" variant="contained">Complete connection</Button>
            </Stack>
          </Stack>
          {kiteUserId && (
            <Typography variant="body2" sx={{ mt: 2 }}>Connected Kite user: <b>{kiteUserId}</b></Typography>
          )}
        </CardContent>
      </Card>
      <Divider />
      <Stack direction="row">
        <Button color="error" variant="outlined" onClick={handleLogout}>Log out</Button>
      </Stack>
    </Stack>
  );
}
