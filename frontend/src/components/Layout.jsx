import { useEffect } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import {
  AppBar, Box, Chip, Drawer, IconButton, List, ListItemButton, ListItemText,
  Toolbar, Tooltip, Typography,
} from '@mui/material';
import LogoutIcon from '@mui/icons-material/Logout';
import { logout } from '../store/authSlice';
import { fetchRiskStatus } from '../store/riskSlice';

const DRAWER_WIDTH = 220;

const NAV = [
  { label: 'Dashboard', to: '/' },
  { label: 'Trading', to: '/trading' },
  { label: 'Option Chain', to: '/option-chain' },

  { label: 'Risk Calculator', to: '/risk-calculator' },
  { label: 'Strategy Builder', to: '/strategy' },
  { label: 'Trading Journal', to: '/journal' },
  { label: 'Analytics', to: '/analytics' },
  { label: 'Settings', to: '/settings' },
];

const STATUS_COLOR = { GREEN: 'success', YELLOW: 'warning', RED: 'error' };
const STATUS_TEXT = { GREEN: 'Safe', YELLOW: 'Warning', RED: 'Trading locked' };

export default function Layout() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const risk = useSelector((state) => state.risk);

  useEffect(() => {
    dispatch(fetchRiskStatus());
    const timer = setInterval(() => dispatch(fetchRiskStatus()), 60000);
    return () => clearInterval(timer);
  }, [dispatch]);

  const handleLogout = () => {
    dispatch(logout());
    navigate('/login');
  };

  const chipDetail = [...risk.locks, ...risk.warnings].join(' • ') || 'No active limits';

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>TradeDiscipline Pro</Typography>
          {risk.status && (
            <Tooltip title={chipDetail}>
              <Chip
                label={`Discipline: ${STATUS_TEXT[risk.status]}`}
                color={STATUS_COLOR[risk.status]}
                sx={{ mr: 2, fontWeight: 600 }}
              />
            </Tooltip>
          )}
          <Tooltip title="Log out">
            <IconButton color="inherit" onClick={handleLogout} aria-label="log out">
              <LogoutIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{ width: DRAWER_WIDTH, '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
      >
        <Toolbar />
        <List>
          {NAV.map((item) => (
            <ListItemButton key={item.to} component={NavLink} to={item.to} end>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, p: 3, mt: 8, minWidth: 0 }}>
        <Outlet />
      </Box>
    </Box>
  );
}

