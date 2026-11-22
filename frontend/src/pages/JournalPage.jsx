import { useEffect, useState } from 'react';
import {
  Alert, Button, Card, CardContent, Chip, Grid, List, ListItem, ListItemText, MenuItem, Stack,
  Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { api, errorMessage } from '../api/client';

const EMOTIONS = ['FEAR', 'GREED', 'IMPULSIVE', 'DISCIPLINED'];
const INITIAL = {
  tradeId: '',
  emotionBefore: '',
  emotionAfter: '',
  notes: '',
  mistakes: '',
  lessons: '',
  entryScreenshotUrl: '',
  exitScreenshotUrl: '',
};

export default function JournalPage() {
  const [form, setForm] = useState(INITIAL);
  const [history, setHistory] = useState([]);
  const [analysis, setAnalysis] = useState(null);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  const load = () => {
    Promise.all([api.get('/journal/history'), api.get('/journal/analysis')])
      .then(([h, a]) => {
        setHistory(h.data);
        setAnalysis(a.data);
      })
      .catch((err) => setError(errorMessage(err)));
  };

  useEffect(load, []);

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setMessage(null);
    const body = {
      ...form,
      tradeId: Number(form.tradeId),
      emotionBefore: form.emotionBefore || null,
      emotionAfter: form.emotionAfter || null,
    };
    try {
      await api.post('/journal/add', body);
      setMessage('Journal entry saved.');
      setForm(INITIAL);
      load();
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <Stack spacing={3}>
      <Typography variant="h4">Trading Journal</Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {message && <Alert severity="success">{message}</Alert>}

      <Card variant="outlined">
        <CardContent>
          <Grid container spacing={2} component="form" onSubmit={submit}>
            <Grid item xs={12} md={4}>
              <TextField fullWidth type="number" label="Trade ID" required value={form.tradeId} onChange={update('tradeId')} />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField select fullWidth label="Emotion before trade" value={form.emotionBefore} onChange={update('emotionBefore')}>
                <MenuItem value="">—</MenuItem>
                {EMOTIONS.map((x) => <MenuItem key={x} value={x}>{x}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField select fullWidth label="Emotion after trade" value={form.emotionAfter} onChange={update('emotionAfter')}>
                <MenuItem value="">—</MenuItem>
                {EMOTIONS.map((x) => <MenuItem key={x} value={x}>{x}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12}><TextField fullWidth multiline minRows={2} label="Trade notes" value={form.notes} onChange={update('notes')} /></Grid>
            <Grid item xs={12} md={6}><TextField fullWidth multiline minRows={2} label="Mistakes" value={form.mistakes} onChange={update('mistakes')} /></Grid>
            <Grid item xs={12} md={6}><TextField fullWidth multiline minRows={2} label="Lessons learned" value={form.lessons} onChange={update('lessons')} /></Grid>
            <Grid item xs={12} md={6}><TextField fullWidth label="Entry screenshot URL" value={form.entryScreenshotUrl} onChange={update('entryScreenshotUrl')} /></Grid>
            <Grid item xs={12} md={6}><TextField fullWidth label="Exit screenshot URL" value={form.exitScreenshotUrl} onChange={update('exitScreenshotUrl')} /></Grid>
            <Grid item xs={12}>
              <Button type="submit" variant="contained">Save entry</Button>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {analysis && (
        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6">Behaviour analysis — score {analysis.disciplineScore} / 100</Typography>
            <Stack direction="row" spacing={1} sx={{ my: 1, flexWrap: 'wrap' }}>
              <Chip label={`Emotional trades: ${analysis.emotionalTrades}`} />
              <Chip label={`Overtrading days: ${analysis.overtradingDays}`} />
              <Chip label={`Revenge trades: ${analysis.revengeTrades}`} />
            </Stack>
            <List dense>
              {analysis.suggestions.map((s) => <ListItem key={s} disableGutters><ListItemText primary={s} /></ListItem>)}
            </List>
          </CardContent>
        </Card>
      )}

      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" gutterBottom>History</Typography>
          {history.length === 0 ? (
            <Typography color="text.secondary">No journal entries yet.</Typography>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Trade</TableCell><TableCell>Before</TableCell><TableCell>After</TableCell>
                  <TableCell>Notes</TableCell><TableCell>Lessons</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {history.map((h) => (
                  <TableRow key={h.id}>
                    <TableCell>{h.tradeId}</TableCell>
                    <TableCell>{h.emotionBefore || '—'}</TableCell>
                    <TableCell>{h.emotionAfter || '—'}</TableCell>
                    <TableCell>{h.notes || '—'}</TableCell>
                    <TableCell>{h.lessons || '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}

