import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import riskReducer from './riskSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    risk: riskReducer,
  },
});

