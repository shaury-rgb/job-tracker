(() => {
  const queryApi = new URLSearchParams(window.location.search).get('api');
  const savedApi = window.localStorage.getItem('job-tracker-api-base');
  const API_BASE = (queryApi || savedApi || window.JOB_TRACKER_API_BASE || '').replace(/\/$/, '');
  if (queryApi) {
    window.localStorage.setItem('job-tracker-api-base', API_BASE);
  }
  const API = `${API_BASE}/api/jobs`;
  const AUTH_API = `${API_BASE}/api/auth`;

  const state = {
    token: window.localStorage.getItem('northstar_token') || null,
    user: JSON.parse(window.localStorage.getItem('northstar_user') || 'null'),
    authMode: 'signin',
    jobs: [],
    view: 'table',
    activeTab: 'applications',
    editingId: null,
    viewingId: null
  };

  const $ = (id) => document.getElementById(id);
  const fields = ['company', 'role', 'location', 'salary', 'applicationUrl', 'status', 'dateApplied', 'notes'];

  const SAMPLE_JOBS = [
    {
      company: 'Stripe',
      role: 'Staff Infrastructure Engineer',
      location: 'San Francisco, CA (Hybrid)',
      salary: '$190,000 - $225,000',
      applicationUrl: 'https://stripe.com/jobs',
      status: 'Interview',
      dateApplied: '2026-10-02',
      notes: 'Completed system design interview with Tech Lead. Round 3 scheduled for Thursday. Review distributed cache consensus algorithms.'
    },
    {
      company: 'Figma',
      role: 'Senior Product Designer & Engineer',
      location: 'Remote',
      salary: '$175,000 - $210,000',
      applicationUrl: 'https://figma.com/careers',
      status: 'Offer',
      dateApplied: '2026-09-18',
      notes: 'Offer extended! $185k base + equity grant. Review benefits and respond by end of week.'
    },
    {
      company: 'Apple',
      role: 'Platform Systems Engineer',
      location: 'Cupertino, CA',
      salary: '$180,000 - $215,000',
      applicationUrl: 'https://apple.com/jobs',
      status: 'Applied',
      dateApplied: '2026-10-08',
      notes: 'Referred by Michael from Core OS team. Recruiter acknowledged receipt.'
    },
    {
      company: 'Datadog',
      role: 'Distributed Systems Engineer',
      location: 'Remote',
      salary: '$160,000 - $190,000',
      applicationUrl: 'https://datadoghq.com/careers',
      status: 'Interview',
      dateApplied: '2026-09-28',
      notes: 'Hiring manager screening completed. Take-home assignment submitted.'
    },
    {
      company: 'Airbnb',
      role: 'Full Stack Engineer',
      location: 'Remote',
      salary: '$165,000 - $195,000',
      applicationUrl: 'https://airbnb.com/careers',
      status: 'Rejected',
      dateApplied: '2026-09-12',
      notes: 'High applicant volume; team closed requisition.'
    }
  ];

  async function request(url = API, options = {}) {
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };
    if (state.token) {
      headers['Authorization'] = `Bearer ${state.token}`;
    }

    const response = await fetch(url, {
      ...options,
      headers
    });

    if (response.status === 204) return null;
    if (response.status === 401) {
      handleUnauthorized();
      throw new Error('Authentication required. Please sign in.');
    }
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || 'Server error occurred.');
    }
    return body;
  }

  function handleUnauthorized() {
    state.token = null;
    state.user = null;
    window.localStorage.removeItem('northstar_token');
    window.localStorage.removeItem('northstar_user');
    updateUserUI();
    openAuthModal('signin');
  }

  function showError(message) {
    const fb = $('feedback');
    fb.textContent = message;
    fb.hidden = false;
  }

  function clearError() {
    $('feedback').hidden = true;
  }

  function showModalError(message) {
    const fb = $('modal-feedback');
    fb.textContent = message;
    fb.hidden = false;
  }

  function clearModalError() {
    $('modal-feedback').hidden = true;
  }

  function showAuthError(message) {
    const fb = $('auth-feedback');
    fb.textContent = message;
    fb.hidden = false;
  }

  function clearAuthError() {
    $('auth-feedback').hidden = true;
  }

  function showToast(message, type = 'success') {
    const container = $('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(8px)';
      setTimeout(() => toast.remove(), 300);
    }, 3200);
  }

  function escapeHtml(value = '') {
    return String(value).replace(/[&<>'"]/g, (char) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      "'": '&#39;',
      '"': '&quot;'
    }[char]));
  }

  function statusClass(status) {
    return `status-${(status || '').toLowerCase()}`;
  }

  function formatDate(value) {
    if (!value) return '—';
    try {
      return new Intl.DateTimeFormat('en', {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      }).format(new Date(`${value}T00:00:00`));
    } catch {
      return value;
    }
  }

  function filteredJobs() {
    const query = $('search').value.toLowerCase().trim();
    const filter = $('status-filter').value;
    const sortVal = $('sort-date').value;

    return state.jobs
      .filter((job) => {
        const matchesStatus = filter === 'All' || job.status === filter;
        const searchable = `${job.company || ''} ${job.role || ''} ${job.location || ''} ${job.notes || ''}`.toLowerCase();
        const matchesQuery = query === '' || searchable.includes(query);
        return matchesStatus && matchesQuery;
      })
      .sort((a, b) => {
        if (sortVal === 'oldest') {
          return (a.dateApplied || '').localeCompare(b.dateApplied || '');
        }
        if (sortVal === 'company_asc') {
          return (a.company || '').localeCompare(b.company || '', undefined, { sensitivity: 'base' });
        }
        if (sortVal === 'company_desc') {
          return (b.company || '').localeCompare(a.company || '', undefined, { sensitivity: 'base' });
        }
        // default newest
        return (b.dateApplied || '').localeCompare(a.dateApplied || '');
      });
  }

  function renderStats() {
    const total = state.jobs.length;
    const inProgress = state.jobs.filter((j) => j.status === 'Applied' || j.status === 'Interview').length;
    const offers = state.jobs.filter((j) => j.status === 'Offer').length;
    const positiveResponse = state.jobs.filter((j) => j.status === 'Interview' || j.status === 'Offer').length;
    const responseRate = total > 0 ? `${Math.round((positiveResponse / total) * 100)}%` : '0%';

    $('stat-total').textContent = total;
    $('stat-progress').textContent = inProgress;
    $('stat-offers').textContent = offers;
    $('stat-response').textContent = responseRate;
  }

  function renderApplications() {
    const jobs = filteredJobs();
    const totalCount = state.jobs.length;

    $('loading').hidden = true;
    $('empty').hidden = totalCount !== 0;

    if (totalCount === 0) {
      $('table-wrap').hidden = true;
      $('cards-wrap').hidden = true;
      return;
    }

    if (state.view === 'table') {
      $('table-wrap').hidden = false;
      $('cards-wrap').hidden = true;
      renderTable(jobs);
    } else {
      $('table-wrap').hidden = true;
      $('cards-wrap').hidden = false;
      renderCards(jobs);
    }
  }

  function renderTable(jobs) {
    const tbody = $('jobs-table');
    tbody.innerHTML = '';

    if (jobs.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" class="table-empty-row">
            No applications match your search query or filter.
          </td>
        </tr>
      `;
      return;
    }

    jobs.forEach((job) => {
      const tr = document.createElement('tr');
      const salaryDisplay = job.salary ? escapeHtml(job.salary) : '<span class="cell-muted">—</span>';
      const locationDisplay = job.location ? escapeHtml(job.location) : '<span class="cell-muted">—</span>';
      const companyDisplay = job.applicationUrl
        ? `<a href="${escapeHtml(job.applicationUrl)}" target="_blank" rel="noopener noreferrer" class="company-link" title="Open job listing">${escapeHtml(job.company)} ↗</a>`
        : `<strong>${escapeHtml(job.company)}</strong>`;

      tr.innerHTML = `
        <td>${companyDisplay}</td>
        <td>${escapeHtml(job.role)}</td>
        <td>${locationDisplay}</td>
        <td>${salaryDisplay}</td>
        <td><span class="status ${statusClass(job.status)}">${escapeHtml(job.status)}</span></td>
        <td>${formatDate(job.dateApplied)}</td>
        <td class="action-cell">
          <button type="button" class="action-btn view-btn" data-action="view" data-id="${job.id}" title="View details">👁</button>
          <button type="button" class="action-btn edit-btn" data-action="edit" data-id="${job.id}" title="Edit application">✎</button>
          <button type="button" class="action-btn delete-btn" data-action="delete" data-id="${job.id}" title="Delete application">🗑</button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  }

  function renderCards(jobs) {
    const wrap = $('cards-wrap');
    wrap.innerHTML = '';

    if (jobs.length === 0) {
      wrap.innerHTML = `
        <div class="cards-empty">
          <p>No applications match your active search or filters.</p>
        </div>
      `;
      return;
    }

    jobs.forEach((job) => {
      const card = document.createElement('article');
      card.className = 'job-card';

      const salaryBadge = job.salary ? `<span class="card-pill salary">${escapeHtml(job.salary)}</span>` : '';
      const locationBadge = job.location ? `<span class="card-pill location">📍 ${escapeHtml(job.location)}</span>` : '';
      const notesSnippet = job.notes ? `<p class="card-notes">${escapeHtml(job.notes)}</p>` : '';
      const linkBtn = job.applicationUrl
        ? `<a href="${escapeHtml(job.applicationUrl)}" target="_blank" rel="noopener noreferrer" class="card-external-btn" title="View job listing">🔗</a>`
        : '';

      card.innerHTML = `
        <div class="card-head">
          <div>
            <span class="status ${statusClass(job.status)}">${escapeHtml(job.status)}</span>
            <h3 class="card-role">${escapeHtml(job.role)}</h3>
            <p class="card-company">${escapeHtml(job.company)}</p>
          </div>
          <div class="card-head-actions">
            ${linkBtn}
          </div>
        </div>

        <div class="card-pills">
          ${locationBadge}
          ${salaryBadge}
          <span class="card-pill date">📅 ${formatDate(job.dateApplied)}</span>
        </div>

        ${notesSnippet}

        <div class="card-foot">
          <button type="button" class="secondary-button card-action-btn" data-action="view" data-id="${job.id}">Details</button>
          <button type="button" class="secondary-button card-action-btn" data-action="edit" data-id="${job.id}">Edit</button>
          <button type="button" class="icon-button card-del-btn" data-action="delete" data-id="${job.id}" title="Delete">🗑</button>
        </div>
      `;
      wrap.appendChild(card);
    });
  }

  function renderInsights() {
    const total = state.jobs.length;
    const applied = state.jobs.filter((j) => j.status === 'Applied').length;
    const interview = state.jobs.filter((j) => j.status === 'Interview').length;
    const offer = state.jobs.filter((j) => j.status === 'Offer').length;
    const rejected = state.jobs.filter((j) => j.status === 'Rejected').length;

    // Conversion Funnel
    $('funnel-applied-count').textContent = total;
    const interviewRate = total > 0 ? Math.round(((interview + offer) / total) * 100) : 0;
    $('funnel-interview-rate').textContent = `${interviewRate}% moved to interview`;
    $('funnel-interview-count').textContent = interview + offer;
    $('funnel-interview-bar').style.width = `${interviewRate}%`;

    const offerRate = interview + offer > 0 ? Math.round((offer / (interview + offer)) * 100) : 0;
    $('funnel-offer-rate').textContent = `${offerRate}% interview-to-offer rate`;
    $('funnel-offer-count').textContent = offer;
    $('funnel-offer-bar').style.width = `${total > 0 ? Math.round((offer / total) * 100) : 0}%`;

    // Status Distribution
    const distBar = $('distribution-bar');
    const distLegend = $('distribution-legend');
    distBar.innerHTML = '';
    distLegend.innerHTML = '';

    const statuses = [
      { key: 'Applied', count: applied, color: '#2864dc' },
      { key: 'Interview', count: interview, color: '#d38b17' },
      { key: 'Offer', count: offer, color: '#159a70' },
      { key: 'Rejected', count: rejected, color: '#e66458' }
    ];

    statuses.forEach((s) => {
      const pct = total > 0 ? Math.round((s.count / total) * 100) : 0;
      if (pct > 0) {
        const seg = document.createElement('div');
        seg.className = 'distribution-segment';
        seg.style.width = `${pct}%`;
        seg.style.backgroundColor = s.color;
        seg.title = `${s.key}: ${s.count} (${pct}%)`;
        distBar.appendChild(seg);
      }

      const leg = document.createElement('div');
      leg.className = 'legend-item';
      leg.innerHTML = `
        <span class="legend-color-dot" style="background:${s.color}"></span>
        <span class="legend-text">${s.key}: <strong>${s.count}</strong> (${pct}%)</span>
      `;
      distLegend.appendChild(leg);
    });

    // Workplace breakdown
    const remoteRoles = state.jobs.filter((j) => (j.location || '').toLowerCase().includes('remote')).length;
    const onsiteRoles = total - remoteRoles;
    $('remote-count').textContent = remoteRoles;
    $('onsite-count').textContent = onsiteRoles;

    const withSalary = state.jobs.filter((j) => j.salary && j.salary.trim() !== '').length;
    const salaryRate = total > 0 ? Math.round((withSalary / total) * 100) : 0;
    $('salary-disclosure-text').textContent = `${withSalary} of ${total} applications (${salaryRate}%) have salary or compensation disclosed.`;

    // Dynamic Strategic Recommendation
    const recBox = $('pipeline-recommendation');
    if (total === 0) {
      recBox.innerHTML = `
        <p class="rec-title">🌱 Fresh Workspace</p>
        <p class="rec-desc">You haven't tracked any applications yet. Add your current opportunities or load realistic demo data to see pipeline analytics.</p>
      `;
    } else if (offer > 0) {
      recBox.innerHTML = `
        <p class="rec-title">🎉 Offer Stage Momentum</p>
        <p class="rec-desc">You have <strong>${offer} active offer(s)</strong>! Compare total compensation, review benefits, and consider leveraging active interview stages for negotiations.</p>
      `;
    } else if (interview > 0) {
      recBox.innerHTML = `
        <p class="rec-title">⚡ High Interview Activity</p>
        <p class="rec-desc">You have <strong>${interview} interview process(es) underway</strong>. Keep notes updated after each conversation and follow up with recruiters within 24 hours.</p>
      `;
    } else if (applied >= 5) {
      recBox.innerHTML = `
        <p class="rec-title">🔍 Pipeline Warming Up</p>
        <p class="rec-desc">You have applied to <strong>${applied} roles</strong>. Give companies 7 to 10 business days, or reach out to internal team members on LinkedIn for warm referrals.</p>
      `;
    } else {
      recBox.innerHTML = `
        <p class="rec-title">🎯 Increase Outbound Velocity</p>
        <p class="rec-desc">Target tracking 3 to 5 tailored applications weekly to maintain healthy top-of-funnel momentum and improve response likelihood.</p>
      `;
    }
  }

  function render() {
    renderStats();
    if (state.activeTab === 'applications') {
      renderApplications();
    } else {
      renderInsights();
    }
  }

  function switchTab(tab) {
    state.activeTab = tab;
    const isApps = tab === 'applications';

    $('nav-applications').classList.toggle('active', isApps);
    $('nav-insights').classList.toggle('active', !isApps);

    $('applications-panel').hidden = !isApps;
    $('insights-panel').hidden = isApps;

    $('page-heading').textContent = isApps ? 'Applications' : 'Pipeline Insights';
    $('add-job').hidden = !isApps;

    render();
  }

  async function loadJobs() {
    if (!state.token) {
      state.jobs = [];
      render();
      return;
    }
    clearError();
    $('loading').hidden = false;
    try {
      const data = await request(API);
      state.jobs = Array.isArray(data) ? data : [];
      render();
    } catch (error) {
      $('loading').hidden = true;
      showError(`${error.message} Connected API: ${API}. Check server status.`);
    }
  }

  // ==========================================
  // User Authentication Functions
  // ==========================================

  function updateUserUI() {
    if (state.user) {
      const names = (state.user.name || '').trim().split(/\s+/);
      const initials = names.length > 1
        ? (names[0][0] + names[names.length - 1][0]).toUpperCase()
        : (names[0]?.[0] || 'U').toUpperCase();

      $('user-avatar').textContent = initials;
      $('user-name-display').textContent = state.user.name || 'User';
      $('user-email-display').textContent = state.user.email || '';
      $('logout-btn').style.display = 'grid';
      $('open-auth-btn').style.display = 'none';
    } else {
      $('user-avatar').textContent = '?';
      $('user-name-display').textContent = 'Guest';
      $('user-email-display').textContent = 'Please sign in';
      $('logout-btn').style.display = 'none';
      $('open-auth-btn').style.display = 'inline-flex';
    }
  }

  function openAuthModal(mode = 'signin') {
    clearAuthError();
    state.authMode = mode;
    const isRegister = mode === 'register';

    $('auth-tab-signin').classList.toggle('active', !isRegister);
    $('auth-tab-register').classList.toggle('active', isRegister);

    $('auth-name-row').style.display = isRegister ? 'flex' : 'none';
    $('auth-password-hint').style.display = isRegister ? 'block' : 'none';

    $('auth-modal-title').textContent = isRegister ? 'Create your account' : 'Sign in to your account';
    $('auth-submit-btn').textContent = isRegister ? 'Create My Account' : 'Sign In to Northstar';

    $('auth-name').required = isRegister;
    $('auth-dialog').showModal();
    if (isRegister) {
      $('auth-name').focus();
    } else {
      $('auth-email').focus();
    }
  }

  function closeAuthModal() {
    clearAuthError();
    $('auth-dialog').close();
  }

  async function handleAuthSubmit(event) {
    event.preventDefault();
    clearAuthError();

    const submitBtn = $('auth-submit-btn');
    submitBtn.disabled = true;
    const originalText = submitBtn.textContent;
    submitBtn.textContent = 'Processing...';

    const isRegister = state.authMode === 'register';
    const payload = {
      email: $('auth-email').value.trim(),
      password: $('auth-password').value
    };
    if (isRegister) {
      payload.name = $('auth-name').value.trim();
    }

    try {
      const endpoint = isRegister ? `${AUTH_API}/register` : `${AUTH_API}/login`;
      const res = await request(endpoint, {
        method: 'POST',
        body: JSON.stringify(payload)
      });

      state.token = res.token;
      state.user = res.user;
      window.localStorage.setItem('northstar_token', res.token);
      window.localStorage.setItem('northstar_user', JSON.stringify(res.user));

      updateUserUI();
      closeAuthModal();
      showToast(isRegister ? `Welcome to Northstar, ${res.user.name}!` : `Welcome back, ${res.user.name}!`);
      await loadJobs();
    } catch (err) {
      showAuthError(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = originalText;
    }
  }

  async function handleDemoLogin() {
    $('auth-email').value = 'demo@northstar.app';
    $('auth-password').value = 'password123';
    state.authMode = 'signin';
    $('auth-tab-signin').classList.add('active');
    $('auth-tab-register').classList.remove('active');
    $('auth-name-row').style.display = 'none';

    $('auth-form').requestSubmit();
  }

  async function handleLogout() {
    if (state.token) {
      try {
        await request(`${AUTH_API}/logout`, { method: 'POST' });
      } catch (ignored) {}
    }
    state.token = null;
    state.user = null;
    state.jobs = [];
    window.localStorage.removeItem('northstar_token');
    window.localStorage.removeItem('northstar_user');
    updateUserUI();
    render();
    showToast('Signed out successfully.');
    openAuthModal('signin');
  }

  async function initAuth() {
    updateUserUI();
    if (state.token) {
      try {
        const user = await request(`${AUTH_API}/me`);
        state.user = user;
        window.localStorage.setItem('northstar_user', JSON.stringify(user));
        updateUserUI();
        await loadJobs();
      } catch (err) {
        handleUnauthorized();
      }
    } else {
      openAuthModal('signin');
    }
  }

  // ==========================================
  // Job Dialog and CRUD Functions
  // ==========================================

  function openModal(job = null) {
    if (!state.token) {
      openAuthModal('signin');
      return;
    }
    clearModalError();
    state.editingId = job?.id || null;
    $('modal-title').textContent = job ? 'Edit application' : 'Add application';
    $('save-job').textContent = job ? 'Save changes' : 'Save application';

    fields.forEach((field) => {
      $(field).value = job?.[field] || (field === 'status' ? 'Applied' : '');
    });

    if (!job) {
      $('dateApplied').value = new Date().toISOString().slice(0, 10);
    }
    $('job-dialog').showModal();
    $('company').focus();
  }

  function closeModal() {
    clearModalError();
    $('job-dialog').close();
  }

  function openDetails(job) {
    if (!job) return;
    state.viewingId = job.id;

    $('details-company-name').textContent = job.company;
    $('details-role-title').textContent = job.role;

    const badge = $('details-status-badge');
    badge.className = `status ${statusClass(job.status)}`;
    badge.textContent = job.status;

    $('details-date').textContent = formatDate(job.dateApplied);
    $('details-location').textContent = job.location || 'Remote / unspecified';
    $('details-salary').textContent = job.salary || 'Not specified';

    const urlRow = $('details-url-row');
    const urlLink = $('details-url-link');
    if (job.applicationUrl) {
      urlRow.hidden = false;
      urlLink.href = job.applicationUrl;
    } else {
      urlRow.hidden = true;
    }

    $('details-notes').textContent = job.notes || 'No notes added for this application.';
    $('details-dialog').showModal();
  }

  function closeDetails() {
    $('details-dialog').close();
  }

  async function saveJob(event) {
    event.preventDefault();
    clearModalError();

    const payload = Object.fromEntries(fields.map((field) => [field, $(field).value.trim()]));
    const button = $('save-job');
    button.disabled = true;
    button.textContent = 'Saving...';

    try {
      if (state.editingId) {
        await request(`${API}/${state.editingId}`, {
          method: 'PUT',
          body: JSON.stringify(payload)
        });
        showToast(`Updated ${payload.company} application`);
      } else {
        await request(API, {
          method: 'POST',
          body: JSON.stringify(payload)
        });
        showToast(`Added application at ${payload.company}`);
      }
      closeModal();
      await loadJobs();
    } catch (error) {
      showModalError(error.message);
    } finally {
      button.disabled = false;
      button.textContent = state.editingId ? 'Save changes' : 'Save application';
    }
  }

  async function deleteJob(id) {
    const job = state.jobs.find((item) => item.id === id);
    if (!job) return;
    if (!confirm(`Are you sure you want to delete the ${job.role} application at ${job.company}?`)) {
      return;
    }

    try {
      await request(`${API}/${id}`, { method: 'DELETE' });
      showToast(`Deleted ${job.company} application`);
      if (state.viewingId === id) {
        closeDetails();
      }
      await loadJobs();
    } catch (error) {
      showError(error.message);
    }
  }

  function handleAction(event) {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const id = button.dataset.id;
    const job = state.jobs.find((item) => item.id === id);
    if (!job) return;

    if (button.dataset.action === 'edit') {
      openModal(job);
    } else if (button.dataset.action === 'view') {
      openDetails(job);
    } else if (button.dataset.action === 'delete') {
      deleteJob(id);
    }
  }

  function exportData() {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(state.jobs, null, 2));
    const dlAnchor = document.createElement('a');
    const today = new Date().toISOString().slice(0, 10);
    dlAnchor.setAttribute('href', dataStr);
    dlAnchor.setAttribute('download', `job-tracker-backup-${today}.json`);
    dlAnchor.click();
    showToast('Exported applications backup');
  }

  async function importData(event) {
    const file = event.target.files[0];
    if (!file) return;

    try {
      const text = await file.text();
      const items = JSON.parse(text);
      if (!Array.isArray(items)) {
        throw new Error('Import file must contain a JSON array of applications.');
      }

      let count = 0;
      for (const item of items) {
        if (!item.company || !item.role) continue;
        const payload = {
          company: item.company || '',
          role: item.role || '',
          location: item.location || '',
          salary: item.salary || '',
          applicationUrl: item.applicationUrl || '',
          status: item.status || 'Applied',
          dateApplied: item.dateApplied || new Date().toISOString().slice(0, 10),
          notes: item.notes || ''
        };
        await request(API, {
          method: 'POST',
          body: JSON.stringify(payload)
        });
        count++;
      }

      showToast(`Successfully imported ${count} applications`);
      await loadJobs();
    } catch (err) {
      showError(`Import failed: ${err.message}`);
    } finally {
      event.target.value = '';
    }
  }

  async function loadSampleData() {
    if (!state.token) {
      openAuthModal('signin');
      return;
    }
    if (state.jobs.length > 0 && !confirm('Load demo applications into your workspace?')) {
      return;
    }
    try {
      for (const sample of SAMPLE_JOBS) {
        await request(API, {
          method: 'POST',
          body: JSON.stringify(sample)
        });
      }
      showToast('Loaded demo applications');
      await loadJobs();
    } catch (err) {
      showError(`Failed to load demo data: ${err.message}`);
    }
  }

  // ==========================================
  // Event Listeners
  // ==========================================

  // Authentication UI Listeners
  $('auth-tab-signin').addEventListener('click', () => openAuthModal('signin'));
  $('auth-tab-register').addEventListener('click', () => openAuthModal('register'));
  $('close-auth-modal').addEventListener('click', closeAuthModal);
  $('auth-form').addEventListener('submit', handleAuthSubmit);
  $('auth-demo-btn').addEventListener('click', handleDemoLogin);
  $('open-auth-btn').addEventListener('click', () => openAuthModal('signin'));
  $('logout-btn').addEventListener('click', handleLogout);

  // Job Dialog Listeners
  $('add-job').addEventListener('click', () => openModal());
  $('empty-add').addEventListener('click', () => openModal());
  $('empty-sample').addEventListener('click', loadSampleData);
  $('seed-sample-btn').addEventListener('click', loadSampleData);
  $('cancel-modal').addEventListener('click', closeModal);
  $('close-modal').addEventListener('click', closeModal);
  $('job-form').addEventListener('submit', saveJob);

  // Details Dialog Listeners
  $('close-details').addEventListener('click', closeDetails);
  $('details-edit-btn').addEventListener('click', () => {
    const job = state.jobs.find((j) => j.id === state.viewingId);
    closeDetails();
    if (job) openModal(job);
  });
  $('details-delete-btn').addEventListener('click', () => {
    if (state.viewingId) deleteJob(state.viewingId);
  });

  // Table & Card Delegated Actions
  $('jobs-table').addEventListener('click', handleAction);
  $('cards-wrap').addEventListener('click', handleAction);

  // Filters & Search
  $('clear-filters-btn').addEventListener('click', () => {
    $('search').value = '';
    $('status-filter').value = 'All';
    render();
  });

  ['search', 'status-filter', 'sort-date'].forEach((id) => $(id).addEventListener('input', render));

  // Display Mode Buttons (Table vs Cards)
  document.querySelectorAll('.view-button').forEach((button) => {
    button.addEventListener('click', () => {
      state.view = button.dataset.view;
      document.querySelectorAll('.view-button').forEach((item) => item.classList.toggle('active', item === button));
      renderApplications();
    });
  });

  // Navigation Tabs (Applications vs Insights)
  $('nav-applications').addEventListener('click', (e) => {
    e.preventDefault();
    switchTab('applications');
  });
  $('nav-insights').addEventListener('click', (e) => {
    e.preventDefault();
    switchTab('insights');
  });

  // Data Mobility (Import / Export)
  $('export-json').addEventListener('click', exportData);
  $('import-trigger').addEventListener('click', () => $('import-file').click());
  $('import-file').addEventListener('change', importData);

  // Initial tab sync based on URL hash
  if (window.location.hash === '#insights') {
    switchTab('insights');
  } else {
    switchTab('applications');
  }

  // Initialize Authentication & Workspace
  initAuth();
})();
