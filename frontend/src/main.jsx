import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
import App from './App';
import { store } from './store';
import { logout } from './store/authSlice';
import { setUnauthorizedHandler } from './api/client';

// Any 401 from the backend (expired or invalid JWT) clears the session and returns to login.
setUnauthorizedHandler(() => {
  store.dispatch(logout());
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
});

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#1565c0' },
    background: { default: '#f5f7fb' },
  },
});

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </ThemeProvider>
    </Provider>
  </React.StrictMode>,
);

