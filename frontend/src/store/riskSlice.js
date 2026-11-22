import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { api, errorMessage } from '../api/client';

// Discipline state (GREEN / YELLOW / RED) shown in the app header and on the dashboard.
export const fetchRiskStatus = createAsyncThunk('risk/fetchStatus', async (_, { rejectWithValue }) => {
  try {
    const { data } = await api.get('/risk/status');
    return data;
  } catch (err) {
    return rejectWithValue(errorMessage(err));
  }
});

const riskSlice = createSlice({
  name: 'risk',
  initialState: { status: null, locks: [], warnings: [], error: null, loading: false },
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchRiskStatus.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchRiskStatus.fulfilled, (state, action) => {
        state.loading = false;
        state.error = null;
        state.status = action.payload.status;
        state.locks = action.payload.locks;
        state.warnings = action.payload.warnings;
      })
      .addCase(fetchRiskStatus.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload || action.error.message;
      });
  },
});

export default riskSlice.reducer;

