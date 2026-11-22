import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { api, errorMessage } from '../api/client';

export const login = createAsyncThunk('auth/login', async ({ email, password }, { rejectWithValue }) => {
  try {
    const { data } = await api.post('/auth/login', { email, password });
    localStorage.setItem('token', data.token);
    return data.token;
  } catch (err) {
    return rejectWithValue(errorMessage(err));
  }
});

export const register = createAsyncThunk('auth/register', async ({ name, email, password }, { dispatch, rejectWithValue }) => {
  try {
    await api.post('/auth/register', { name, email, password });
  } catch (err) {
    return rejectWithValue(errorMessage(err));
  }
  // Log in straight away so the user doesn't have to type credentials twice.
  return dispatch(login({ email, password })).unwrap();
});

const authSlice = createSlice({
  name: 'auth',
  initialState: {
    token: localStorage.getItem('token'),
    status: 'idle',
    error: null,
  },
  reducers: {
    logout(state) {
      localStorage.removeItem('token');
      state.token = null;
      state.status = 'idle';
      state.error = null;
    },
    clearError(state) {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    for (const thunk of [login, register]) {
      builder
        .addCase(thunk.pending, (state) => {
          state.status = 'loading';
          state.error = null;
        })
        .addCase(thunk.fulfilled, (state, action) => {
          state.status = 'succeeded';
          state.token = action.payload;
        })
        .addCase(thunk.rejected, (state, action) => {
          state.status = 'failed';
          state.error = action.payload || action.error.message;
        });
    }
  },
});

export const { logout, clearError } = authSlice.actions;
export default authSlice.reducer;

