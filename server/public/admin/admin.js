'use strict';
// Panel del personal: canje para cajeros y administración de contenido para admin. Todo se valida en el servidor.
(() => {
  const API = '../staff-api.php';
  const view = document.getElementById('view');
  const tabs = document.getElementById('tabs');
  const who = document.getElementById('who');
  let csrf = '';
  let me = null;
  let parks = [];
  const STATUS = { available: 'Disponible', upcoming: 'Aún no disponible', redeemed: 'Canjeado', expired: 'Vencido', void: 'Anulado' };

  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const toast = (text, error = false) => {
    const t = document.getElementById('toast');
    t.textContent = text; t.className = 'show' + (error ? ' error' : '');
    clearTimeout(toast.timer); toast.timer = setTimeout(() => { t.className = ''; }, 3500);
  };
  async function api(action, body = null, query = {}) {
    const url = API + '?' + new URLSearchParams({ action, ...query });
    const res = await fetch(url, body === null ? { credentials: 'same-origin' } : {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf }, body: JSON.stringify(body)
    });
    const json = await res.json().catch(() => ({ ok: false, error: 'Respuesta inválida del servidor.' }));
    if (res.status === 401 && action !== 'login') { me = null; render(); }
    if (!json.ok) throw new Error(json.error || 'Error');
    return json.data;
  }
  const field = (label, name, value = '', attrs = '') => `<label>${esc(label)}<input name="${name}" value="${esc(value)}" ${attrs}></label>`;
  const area = (label, name, value = '', attrs = '') => `<label>${esc(label)}<textarea name="${name}" ${attrs}>${esc(value)}</textarea></label>`;
  const check = (label, name, checked) => `<label class="check"><input type="checkbox" name="${name}" ${checked ? 'checked' : ''}>${esc(label)}</label>`;
  const parkSelect = (value, emptyLabel) => `<label>Parque<select name="park_id"><option value="">${esc(emptyLabel)}</option>${parks.filter(p => p.active).map(p => `<option value="${p.id}" ${Number(value) === p.id ? 'selected' : ''}>${esc(p.name)}</option>`).join('')}</select></label>`;
  const formValues = form => {
    const data = {};
    for (const el of form.elements) {
      if (!el.name) continue;
      data[el.name] = el.type === 'checkbox' ? el.checked : el.value;
    }
    return data;
  };
  const date = v => v ? new Date(v.replace(' ', 'T')).toLocaleDateString('es-GT', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';

  const SECTIONS = [
    { id: 'canje', label: 'Canjear', role: 'cajero', render: renderRedeem },
    { id: 'ofertas', label: 'Ofertas', role: 'admin', render: () => renderCrud('offers', 'offer-save', 'Ofertas', offerForm, offerRow, ['Oferta', 'Vigencia', 'Cupón', 'Estado']) },
    { id: 'eventos', label: 'Eventos', role: 'admin', render: () => renderCrud('events', 'event-save', 'Eventos', eventForm, eventRow, ['Evento', 'Fecha', 'Parque', 'Estado']) },
    { id: 'parques', label: 'Parques', role: 'admin', render: () => renderCrud('parks', 'park-save', 'Parques', parkForm, parkRow, ['Parque', 'Dirección', 'Horario', 'Estado']) },
    { id: 'regalo', label: 'Regalo de cumpleaños', role: 'admin', render: renderSettings },
    { id: 'personal', label: 'Personal', role: 'admin', render: () => renderCrud('staff', 'staff-save', 'Personal', staffForm, staffRow, ['Nombre', 'Correo', 'Rol', 'Parque', 'Estado']) }
  ];
  const allowed = () => SECTIONS.filter(s => s.role === 'cajero' || me.role === 'admin');

  async function render() {
    stopScanner();
    if (!me) { tabs.hidden = true; who.innerHTML = ''; return renderLogin(); }
    const list = allowed();
    const current = list.find(s => '#' + s.id === location.hash) || list[0];
    tabs.hidden = false;
    tabs.innerHTML = list.map(s => `<a href="#${s.id}" class="${s === current ? 'active' : ''}">${esc(s.label)}</a>`).join('');
    who.innerHTML = `${esc(me.name)} · ${esc(me.park_name || 'Sin parque')} <button type="button" id="logout">Salir</button>`;
    document.getElementById('logout').onclick = async () => { csrf = (await api('logout', {})).csrf; me = null; render(); };
    try { parks = await api('parks'); await current.render(); } catch (e) { view.innerHTML = `<p class="card">${esc(e.message)}</p>`; }
  }

  function renderLogin() {
    view.innerHTML = `<form class="card" id="login" style="max-width:420px;margin:40px auto"><h1>Entrar</h1>${field('Correo', 'email', '', 'type="email" required autocomplete="username"')}${field('Contraseña', 'password', '', 'type="password" required autocomplete="current-password"')}<button>Entrar</button></form>`;
    document.getElementById('login').onsubmit = async ev => {
      ev.preventDefault();
      try { const r = await api('login', formValues(ev.target)); csrf = r.csrf; me = r.staff; render(); } catch (e) { toast(e.message, true); }
    };
  }

  function couponCard(c) {
    const owner = c.kind === 'birthday' ? `Cumpleaños de <strong>${esc(c.member_name)}</strong> (${c.birthday_year})` : `Oferta: <strong>${esc(c.offer_title)}</strong>`;
    const canRedeem = c.status === 'available';
    return `<div class="card"><div class="code">${esc(c.code)}</div><p><span class="badge ${c.status}">${STATUS[c.status]}</span></p>
      <p>${owner}<br>Cuenta: ${esc(c.account_name || '—')} · ${esc(c.account_email)}</p>
      <p><strong>Regalo:</strong> ${esc(c.label)}</p>${c.terms ? `<p class="muted">${esc(c.terms)}</p>` : ''}
      <p>Vigencia: ${date(c.valid_from)} al ${date(c.valid_until)}</p>
      ${c.redeemed_at ? `<p>Canjeado el ${date(c.redeemed_at)} en ${esc(c.redeemed_park)}</p>` : ''}
      <div class="actions">${canRedeem ? '<button id="confirm">Confirmar canje</button>' : ''}
      ${me.role === 'admin' && !c.redeemed_at && c.status !== 'void' ? '<button class="danger" id="void">Anular</button>' : ''}</div></div>`;
  }
  let scanStream = null;
  function stopScanner() {
    if (scanStream) { scanStream.getTracks().forEach(t => t.stop()); scanStream = null; }
    const box = document.getElementById('scanner-box');
    if (box) box.hidden = true;
    const btn = document.getElementById('scan-btn');
    if (btn) btn.textContent = 'Escanear QR';
  }
  function renderRedeem() {
    stopScanner();
    view.innerHTML = `<form class="card" id="find"><h1>Canjear cupón</h1><p class="muted">Escribe el código de 6 caracteres o escanea el código QR que el cliente muestra en la app.</p>
      ${field('Código', 'code', '', 'required maxlength="6" autocomplete="off" autocapitalize="characters" style="text-transform:uppercase"')}
      <div class="actions"><button>Consultar</button><button type="button" class="secondary" id="scan-btn">Escanear QR</button></div>
      <div id="scanner-box" class="scanner" hidden><video id="scanner-video" playsinline muted></video><div class="scanner-guide"></div></div></form><div id="result"></div>`;
    const result = document.getElementById('result');
    const form = document.getElementById('find');
    const show = c => {
      result.innerHTML = couponCard(c);
      const confirm = document.getElementById('confirm');
      if (confirm) confirm.onclick = async () => {
        confirm.disabled = true;
        try { show(await api('redeem', { code: c.code })); toast('Cupón canjeado.'); } catch (e) { toast(e.message, true); confirm.disabled = false; }
      };
      const voidBtn = document.getElementById('void');
      if (voidBtn) voidBtn.onclick = async () => {
        const reason = window.prompt('Motivo de la anulación');
        if (!reason) return;
        try { show(await api('void', { code: c.code, reason })); toast('Cupón anulado.'); } catch (e) { toast(e.message, true); }
      };
    };
    const lookupCode = async raw => {
      const clean = String(raw || '').trim().toUpperCase();
      form.code.value = clean;
      result.innerHTML = '';
      try { show(await api('lookup', null, { code: clean })); } catch (e) { toast(e.message, true); }
    };
    form.onsubmit = async ev => {
      ev.preventDefault();
      stopScanner();
      await lookupCode(form.code.value);
    };
    document.getElementById('scan-btn').onclick = async () => {
      if (scanStream) { stopScanner(); return; }
      if (!('BarcodeDetector' in window) || !navigator.mediaDevices?.getUserMedia) {
        toast('Este navegador no incluye lector QR directo; escribe el código de 6 caracteres.', true);
        return;
      }
      try {
        const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
        scanStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
        const video = document.getElementById('scanner-video');
        const box = document.getElementById('scanner-box');
        video.srcObject = scanStream;
        box.hidden = false;
        document.getElementById('scan-btn').textContent = 'Detener cámara';
        await video.play();
        const tick = async () => {
          if (!scanStream) return;
          try {
            const codes = await detector.detect(video);
            const match = codes.map(b => String(b.rawValue || '').trim().toUpperCase()).find(v => /^[A-Z0-9]{6}$/.test(v));
            if (match) { stopScanner(); await lookupCode(match); return; }
          } catch (_) { }
          if (scanStream) requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick);
      } catch (_) {
        stopScanner();
        toast('No se pudo abrir la cámara. Revisa el permiso del navegador.', true);
      }
    };
  }

  async function renderCrud(listAction, saveAction, title, form, row, headers) {
    const items = listAction === 'parks' ? parks : await api(listAction);
    let q = '';
    const draw = (item = {}) => {
      const norm = v => String(v ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
      const filtered = q ? items.filter(i => norm(JSON.stringify(i)).includes(norm(q))) : items;
      view.innerHTML = `<div class="card"><div class="toolbar"><h1>${esc(title)} (${filtered.length})</h1>${items.length > 5 ? `<input type="search" id="crud-search" placeholder="Filtrar…" value="${esc(q)}">` : ''}</div><div class="table-wrap"><table><thead><tr>${headers.map(h => `<th>${esc(h)}</th>`).join('')}<th></th></tr></thead><tbody>
        ${filtered.map(i => `<tr>${row(i).map(c => `<td>${c}</td>`).join('')}<td><button class="secondary" data-edit="${i.id}">Editar</button></td></tr>`).join('') || `<tr><td colspan="${headers.length + 1}" class="muted">No hay registros que coincidan.</td></tr>`}
        </tbody></table></div></div><form class="card" id="editor"><h2>${item.id ? 'Editar' : 'Nuevo'}</h2><input type="hidden" name="id" value="${esc(item.id || '')}">${form(item)}
        <div class="actions"><button>Guardar</button>${item.id ? '<button type="button" class="secondary" id="new">Nuevo</button>' : ''}</div></form>`;
      const search = document.getElementById('crud-search');
      if (search) {
        search.focus(); search.setSelectionRange(q.length, q.length);
        search.oninput = ev => { q = ev.target.value; draw(item); };
      }
      view.querySelectorAll('[data-edit]').forEach(b => { b.onclick = () => { draw(items.find(i => String(i.id) === b.dataset.edit)); document.getElementById('editor').scrollIntoView(); }; });
      const fresh = document.getElementById('new'); if (fresh) fresh.onclick = () => draw();
      document.getElementById('editor').onsubmit = async ev => {
        ev.preventDefault();
        try { await api(saveAction, formValues(ev.target)); toast('Guardado.'); render(); } catch (e) { toast(e.message, true); }
      };
    };
    draw();
  }
  const state = active => `<span class="badge ${active ? 'available' : 'void'}">${active ? 'Activo' : 'Inactivo'}</span>`;
  const today = () => new Date().toISOString().slice(0, 10);

  const offerForm = o => `${field('Título', 'title', o.title, 'required maxlength="140"')}${area('Descripción', 'description', o.description, 'required maxlength="4000"')}
    <div class="row">${field('Desde', 'starts_on', o.starts_on || today(), 'type="date" required')}${field('Hasta', 'ends_on', o.ends_on || '', 'type="date" required')}${parkSelect(o.park_id, 'Todos los parques')}</div>
    ${check('Incluye cupón (uno por cuenta, se canjea una vez)', 'coupon_enabled', o.coupon_enabled)}
    ${field('Regalo del cupón', 'coupon_label', o.coupon_label, 'maxlength="250"')}${area('Condiciones del cupón', 'coupon_terms', o.coupon_terms, 'maxlength="2000"')}
    ${check('Visible en la app', 'active', o.id ? o.active : true)}`;
  const offerRow = o => [`<strong>${esc(o.title)}</strong><br><span class="muted">${esc(o.park_name || 'Todos los parques')}</span>`, `${date(o.starts_on)} – ${date(o.ends_on)}`, o.coupon_enabled ? esc(o.coupon_label) : '—', state(o.active)];

  const eventForm = e => `${field('Título', 'title', e.title, 'required maxlength="140"')}${area('Descripción', 'description', e.description, 'required maxlength="4000"')}
    <div class="row">${field('Inicio', 'starts_at', (e.starts_at || '').slice(0, 16).replace(' ', 'T'), 'type="datetime-local" required')}${field('Fin (opcional)', 'ends_at', (e.ends_at || '').slice(0, 16).replace(' ', 'T'), 'type="datetime-local"')}${parkSelect(e.park_id, 'Todos los parques')}</div>
    ${check('Visible en la app', 'active', e.id ? e.active : true)}`;
  const eventRow = e => [`<strong>${esc(e.title)}</strong>`, date(e.starts_at), esc(e.park_name || 'Todos'), state(e.active)];

  const parkForm = p => `${field('Nombre', 'name', p.name, 'required maxlength="120"')}${field('Dirección', 'address', p.address, 'maxlength="250"')}
    <div class="row">${field('Horario', 'hours', p.hours, 'maxlength="250" placeholder="Lun a dom 10:00 a 21:00"')}${field('Teléfono', 'phone', p.phone, 'maxlength="30"')}</div>
    <div class="row">${field('Latitud', 'latitude', p.latitude ?? '', 'inputmode="decimal"')}${field('Longitud', 'longitude', p.longitude ?? '', 'inputmode="decimal"')}</div>
    ${check('Visible en la app', 'active', p.id ? p.active : true)}`;
  const parkRow = p => [`<strong>${esc(p.name)}</strong>`, esc(p.address || '—'), esc(p.hours || '—'), state(p.active)];

  const staffForm = s => `<div class="row">${field('Nombre', 'name', s.name, 'required maxlength="140"')}${field('Correo', 'email', s.email, 'type="email" required')}</div>
    <div class="row"><label>Rol<select name="role">${['admin', 'cajero'].map(r => `<option value="${r}" ${s.role === r ? 'selected' : ''}>${r === 'admin' ? 'Administrador' : 'Cajero'}</option>`).join('')}</select></label>${parkSelect(s.park_id, 'Sin parque')}</div>
    ${field(s.id ? 'Nueva contraseña (vacía conserva la actual)' : 'Contraseña', 'password', '', `type="password" minlength="10" autocomplete="new-password" ${s.id ? '' : 'required'}`)}
    ${check('Cuenta activa', 'active', s.id ? s.active : true)}`;
  const staffRow = s => [esc(s.name), esc(s.email), s.role === 'admin' ? 'Administrador' : 'Cajero', esc(s.park_name || '—'), state(s.active)];

  async function renderSettings() {
    const s = await api('settings');
    view.innerHTML = `<form class="card" id="settings"><h1>Regalo de cumpleaños</h1>
      <p class="muted">Cada festejado recibe un cupón por año con este regalo. Mientras el regalo esté vacío no se emiten cupones. Cambiarlo no modifica los cupones ya emitidos.</p>
      ${field('Regalo', 'birthday_label', s.birthday_label, 'maxlength="250"')}${area('Condiciones', 'birthday_terms', s.birthday_terms, 'maxlength="2000"')}
      <div class="row">${field('Días antes del cumpleaños', 'birthday_days_before', s.birthday_days_before, 'type="number" min="0" max="60" required')}${field('Días después', 'birthday_days_after', s.birthday_days_after, 'type="number" min="0" max="60" required')}</div>
      <button>Guardar</button></form>`;
    document.getElementById('settings').onsubmit = async ev => {
      ev.preventDefault();
      try { await api('settings-save', formValues(ev.target)); toast('Regalo guardado.'); } catch (e) { toast(e.message, true); }
    };
  }

  window.addEventListener('hashchange', render);
  api('me').then(r => { csrf = r.csrf; me = r.staff; render(); }).catch(e => { view.innerHTML = `<p class="card">${esc(e.message)}</p>`; });
})();
