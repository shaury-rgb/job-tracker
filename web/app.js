const queryApi = new URLSearchParams(window.location.search).get('api');
const savedApi = window.localStorage.getItem('job-tracker-api-base');
const API_BASE = (queryApi || savedApi || window.JOB_TRACKER_API_BASE || '').replace(/\/$/, '');
if (queryApi) window.localStorage.setItem('job-tracker-api-base', API_BASE);
const API = `${API_BASE}/api/jobs`;
const state = { jobs: [], view: 'table', editingId: null };
const $ = (id) => document.getElementById(id);
const fields = ['company', 'role', 'location', 'salary', 'applicationUrl', 'status', 'dateApplied', 'notes'];

async function request(url = API, options = {}) {
  const response = await fetch(url, { headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }, ...options });
  if (response.status === 204) return null;
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.error || 'Something went wrong.');
  return body;
}
function showError(message) { $('feedback').textContent = message; $('feedback').hidden = false; }
function clearError() { $('feedback').hidden = true; }
function escapeHtml(value = '') { return String(value).replace(/[&<>'"]/g, (char) => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' }[char])); }
function statusClass(status) { return `status-${status.toLowerCase()}`; }
function filteredJobs() {
  const query = $('search').value.toLowerCase().trim(); const filter = $('status-filter').value;
  return state.jobs.filter(job => (filter === 'All' || job.status === filter) && `${job.company} ${job.role} ${job.location}`.toLowerCase().includes(query)).sort((a,b) => { const direction = $('sort-date').value === 'newest' ? -1 : 1; return direction * a.dateApplied.localeCompare(b.dateApplied); });
}
function render() {
  const jobs = filteredJobs(); $('loading').hidden = true; $('empty').hidden = state.jobs.length !== 0;
  $('table-wrap').hidden = state.jobs.length === 0 || state.view !== 'table'; $('cards-wrap').hidden = state.jobs.length === 0 || state.view !== 'cards';
  $('stat-total').textContent = state.jobs.length; $('stat-progress').textContent = state.jobs.filter(j => j.status === 'Applied' || j.status === 'Interview').length; $('stat-offers').textContent = state.jobs.filter(j => j.status === 'Offer').length; $('stat-response').textContent = state.jobs.length ? `${Math.round(state.jobs.filter(j => j.status === 'Interview' || j.status === 'Offer').length / state.jobs.length * 100)}%` : '0%';
  $('jobs-table').innerHTML = jobs.map(job => `<tr><td><div class="company-cell"><span class="company-logo">${escapeHtml(job.company.slice(0,1).toUpperCase())}</span>${escapeHtml(job.company)}</div></td><td>${escapeHtml(job.role)}</td><td>${escapeHtml(job.location || '—')}</td><td>${formatDate(job.dateApplied)}</td><td><span class="status ${statusClass(job.status)}">${escapeHtml(job.status)}</span></td><td class="row-actions"><button data-action="edit" data-id="${job.id}" title="Edit">✎</button><button data-action="delete" data-id="${job.id}" title="Delete">⌫</button></td></tr>`).join('');
  $('cards-wrap').innerHTML = jobs.map(job => `<article class="job-card"><span class="status ${statusClass(job.status)}">${escapeHtml(job.status)}</span><h3>${escapeHtml(job.company)}</h3><p>${escapeHtml(job.role)} · ${escapeHtml(job.location || 'Remote / unspecified')}</p><div class="card-meta"><small>${formatDate(job.dateApplied)}</small><span class="card-actions"><button data-action="edit" data-id="${job.id}">✎</button><button data-action="delete" data-id="${job.id}">⌫</button></span></div></article>`).join('');
  if (state.jobs.length > 0 && jobs.length === 0) { $('table-wrap').hidden = false; $('jobs-table').innerHTML = `<tr><td colspan="6" style="text-align:center;padding:50px;color:#8492a5">No applications match your filters.</td></tr>`; }
}
function formatDate(value) { if (!value) return '—'; return new Intl.DateTimeFormat('en', { month:'short', day:'numeric', year:'numeric' }).format(new Date(`${value}T00:00:00`)); }
async function loadJobs() { clearError(); $('loading').hidden = false; try { state.jobs = await request(); render(); } catch (error) { $('loading').hidden = true; showError(`${error.message} API: ${API}. Set BACKEND_URL in GitHub Actions, or open the site with ?api=https://your-backend-url.`); } }
function openModal(job = null) { state.editingId = job?.id || null; $('modal-title').textContent = job ? 'Edit application' : 'Add application'; $('save-job').textContent = job ? 'Save changes' : 'Save application'; fields.forEach(field => $(field).value = job?.[field] || (field === 'status' ? 'Applied' : '')); if (!job) $('dateApplied').value = new Date().toISOString().slice(0,10); $('job-dialog').showModal(); $('company').focus(); }
function closeModal() { $('job-dialog').close(); }
async function saveJob(event) { event.preventDefault(); const payload = Object.fromEntries(fields.map(field => [field, $(field).value.trim()])); const button = $('save-job'); button.disabled = true; button.textContent = 'Saving...'; try { if (state.editingId) await request(`${API}/${state.editingId}`, { method:'PUT', body:JSON.stringify(payload) }); else await request(API, { method:'POST', body:JSON.stringify(payload) }); closeModal(); await loadJobs(); } catch (error) { showError(error.message); } finally { button.disabled = false; button.textContent = state.editingId ? 'Save changes' : 'Save application'; } }
async function deleteJob(id) { const job = state.jobs.find(item => item.id === id); if (!job || !confirm(`Delete the ${job.role} application at ${job.company}?`)) return; try { await request(`${API}/${id}`, { method:'DELETE' }); await loadJobs(); } catch (error) { showError(error.message); } }
function handleAction(event) { const button = event.target.closest('[data-action]'); if (!button) return; const job = state.jobs.find(item => item.id === button.dataset.id); if (button.dataset.action === 'edit') openModal(job); else deleteJob(button.dataset.id); }
$('add-job').addEventListener('click', () => openModal()); $('empty-add').addEventListener('click', () => openModal()); $('cancel-modal').addEventListener('click', closeModal); $('close-modal').addEventListener('click', closeModal); $('job-form').addEventListener('submit', saveJob); $('jobs-table').addEventListener('click', handleAction); $('cards-wrap').addEventListener('click', handleAction); ['search','status-filter','sort-date'].forEach(id => $(id).addEventListener('input', render)); document.querySelectorAll('.view-button').forEach(button => button.addEventListener('click', () => { state.view = button.dataset.view; document.querySelectorAll('.view-button').forEach(item => item.classList.toggle('active', item === button)); render(); })); loadJobs();
