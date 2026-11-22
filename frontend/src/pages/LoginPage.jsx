import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Alert, Box, Button, Card, CardContent, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { login, register, clearError } from '../store/authSlice';

export default function LoginPage() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { token, status, error } = useSelector((state) => state.auth);
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ name: '', email: '', password: '' });

  useEffect(() => () => dispatch(clearError()), [dispatch]);

  if (token) {
    return <Navigate to="/" replace />;
  }

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const submit = (e) => {
    e.preventDefault();
    const action = mode === 'login'
      ? login({ email: form.email, password: form.password })
      : register(form);
    dispatch(action).unwrap()
      .then(() => navigate('/', { replace: true }))
      .catch(() => {}); // error is already in the auth state and shown in the form
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2 }}>
      <Card sx={{ width: 380 }} elevation={3}>
        <CardContent>
          <Typography variant="h5" align="center" gutterBottom>TradeDiscipline Pro</Typography>
          <Tabs value={mode} onChange={(_, v) => setMode(v)} variant="fullWidth" sx={{ mb: 2 }}>
            <Tab value="login" label="Log in" />
            <Tab value="register" label="Register" />
          </Tabs>
          <Stack component="form" spacing={2} onSubmit={submit}>
            {mode === 'register' && (
              <TextField label="Name" value={form.name} onChange={update('name')} required />
            )}
            <TextField label="Email" type="email" value={form.email} onChange={update('email')} required />
            <TextField
              label="Password"
              type="password"
              value={form.password}
              onChange={update('password')}
              helperText={mode === 'register' ? 'At least 8 characters' : ' '}
              inputProps={{ minLength: mode === 'register' ? 8 : 1 }}
              required
            />
            {error && <Alert severity="error">{error}</Alert>}
            <Button type="submit" variant="contained" size="large" disabled={status === 'loading'}>
              {mode === 'login' ? 'Log in' : 'Create account'}
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}

