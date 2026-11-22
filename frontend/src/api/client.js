import axios from 'axios';

// All backend routes live under /api; Vite proxies them to Spring Boot in development.
export const api = axios.create({ baseURL: '/api', timeout: 20000 });

let onUnauthorized = () => {};
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLogin = error.config?.url?.includes('/auth/login');
    if (error.response?.status === 401 && !isLogin) {
      onUnauthorized();
    }
    return Promise.reject(error);
  },
);

// Backend errors look like { status, error, details }. Flatten them into one readable string.
export function errorMessage(err) {
  const data = err?.response?.data;
  if (Array.isArray(data?.details)) {
    return `${data.error}: ${data.details.join('; ')}`;
  }
  if (data?.details && typeof data.details === 'object') {
    return `${data.error}: ${Object.values(data.details).join(', ')}`;
  }
  if (data?.error) {
    return data.error;
  }
  if (err?.response?.status === 401) {
    return 'Session expired. Please log in again.';
  }
  return err?.message || 'Request failed';
}

