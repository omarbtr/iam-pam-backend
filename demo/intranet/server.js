const express = require('express');
const session = require('express-session');
const path = require('path');

const app = express();

app.use(express.urlencoded({ extended: true }));
app.use(express.json());
app.use(session({
  secret: 'esprit-corp-intranet-secret-2025',
  resave: false,
  saveUninitialized: false,
  cookie: { maxAge: 3_600_000 }
}));

// Avatar colors cycling for dynamic users
const AVATAR_COLORS = ['#3b82f6', '#8b5cf6', '#00c4a0', '#f59e0b', '#ef4444', '#0ea5e9'];

// Shared credential accepted by PAM auto-login
const SHARED_USER = 'admin';
const SHARED_PASS = 'EspritCorp2025!';

// ── Static files (index.html, serveurs.html, etc.) ────────────────────────────
app.use(express.static(path.join(__dirname)));

// ── GET /login ────────────────────────────────────────────────────────────────
app.get('/login', (req, res) => {
  if (req.session.loggedIn) return res.redirect('/profile');
  res.sendFile(path.join(__dirname, 'login.html'));
});

// ── POST /login ───────────────────────────────────────────────────────────────
app.post('/login', (req, res) => {
  const { username, password } = req.body;
  if (username === SHARED_USER && password === SHARED_PASS) {
    req.session.loggedIn = true;
    res.redirect('/profile');
  } else {
    res.redirect('/login?error=1');
  }
});

// ── GET /profile ──────────────────────────────────────────────────────────────
// The PAM proxy injects X-Pam-User on every proxied request.
// We use it to display the real user's data even though everyone logs in
// with the same shared credentials.
app.get('/profile', (req, res) => {
  if (!req.session.loggedIn) return res.redirect('/login');

  const pamUser = req.headers['x-pam-user'] || null;
  const user = generateGenericUser(pamUser);

  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.send(renderProfilePage(user, pamUser));
});

// ── GET /logout ───────────────────────────────────────────────────────────────
app.get('/logout', (req, res) => {
  req.session.destroy(() => res.redirect('/login'));
});

// ── Helpers ───────────────────────────────────────────────────────────────────

function generateGenericUser(pamUser) {
  const email = pamUser || 'inconnu@espritcorp.com';
  const localPart = email.split('@')[0];
  const parts = localPart.split(/[._\-+]/).filter(Boolean);
  const initials = parts.map(p => p[0]?.toUpperCase() || '').join('').substring(0, 2) || 'U';
  const fullName = parts.map(p => p.charAt(0).toUpperCase() + p.slice(1)).join(' ');
  // Deterministic color from email so the same user always gets the same color
  const colorIdx = [...email].reduce((acc, c) => acc + c.charCodeAt(0), 0) % AVATAR_COLORS.length;
  return {
    fullName,
    initials,
    email,
    role: 'Utilisateur',
    roleClass: 'role-user',
    department: 'ESPRIT Corp',
    lastLogin: new Date().toISOString().replace('T', ' ').substring(0, 19),
    memberSince: '2024-01-01',
    phone: '—',
    location: '—',
    avatarColor: AVATAR_COLORS[colorIdx]
  };
}

function renderProfilePage(user, pamUser) {
  const now = new Date().toISOString().replace('T', ' ').substring(0, 19);

  return `<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Mon Profil — ESPRIT Corp Intranet</title>
<link rel="stylesheet" href="/styles.css">
<style>
  .profile-hero {
    background: linear-gradient(135deg, #1e3a5f 0%, #0f172a 100%);
    border-radius: 12px;
    padding: 32px;
    display: flex;
    align-items: center;
    gap: 24px;
    color: #f8fafc;
    margin-bottom: 24px;
  }
  .profile-avatar {
    width: 80px; height: 80px;
    background: ${user.avatarColor};
    border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    font-size: 28px; font-weight: 700; color: #fff;
    flex-shrink: 0;
    box-shadow: 0 4px 16px rgba(0,0,0,.35);
  }
  .profile-name { font-size: 24px; font-weight: 700; margin-bottom: 4px; }
  .profile-email { font-size: 13px; color: #94a3b8; margin-bottom: 10px; }
  .role-badge {
    display: inline-flex; align-items: center; gap: 6px;
    padding: 4px 12px; border-radius: 99px;
    font-size: 12px; font-weight: 600;
  }
  .role-admin { background: rgba(239,68,68,.2); color: #fca5a5; border: 1px solid rgba(239,68,68,.3); }
  .role-dev   { background: rgba(139,92,246,.2); color: #c4b5fd; border: 1px solid rgba(139,92,246,.3); }
  .role-user  { background: rgba(100,116,139,.2); color: #cbd5e1; border: 1px solid rgba(100,116,139,.3); }
  .profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 24px; }
  .info-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 20px; }
  .info-card-title { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: #94a3b8; margin-bottom: 16px; }
  .info-row { display: flex; justify-content: space-between; align-items: center; padding: 9px 0; border-bottom: 1px solid #f1f5f9; font-size: 13px; }
  .info-row:last-child { border-bottom: none; }
  .info-label { color: #64748b; font-weight: 500; }
  .info-value { color: #1e293b; font-weight: 600; text-align: right; }
  .pam-badge {
    background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 8px;
    padding: 12px 16px; margin-bottom: 24px;
    display: flex; align-items: flex-start; gap: 10px;
    font-size: 12px; color: #1d4ed8; line-height: 1.5;
  }
  .pam-badge-icon { font-size: 20px; flex-shrink: 0; margin-top: 1px; }
  .action-row { display: flex; gap: 12px; flex-wrap: wrap; }
</style>
</head>
<body>

<div class="topbar">
  <div class="topbar-logo">ESPRIT<span>Corp</span></div>
  <div class="topbar-badge">Réseau Interne</div>
  <nav class="topbar-nav">
    <a href="/">Accueil</a>
    <a href="/serveurs.html">Infrastructure</a>
    <a href="/tickets.html">Tickets</a>
    <a href="/securite.html">Sécurité</a>
    <a href="/profile" class="active">Mon Profil</a>
    <a href="/logout" style="color:#f87171">Déconnexion</a>
    <div class="avatar">${user.initials}</div>
  </nav>
</div>

<div class="layout">
  <aside class="sidebar">
    <div class="sidebar-section">Navigation</div>
    <a href="/"              class="sidebar-item">📊 Tableau de bord</a>
    <a href="/serveurs.html" class="sidebar-item">🖥️ Serveurs</a>
    <a href="/securite.html" class="sidebar-item">🔐 Sécurité</a>

    <div class="sidebar-section">Compte</div>
    <a href="/tickets.html"  class="sidebar-item">🎫 Tickets</a>
    <a href="/profile"       class="sidebar-item active">👤 Mon Profil</a>
    <a href="/logout"        class="sidebar-item">🚪 Déconnexion</a>
  </aside>

  <main class="main">
    <div class="page-title">Mon Profil</div>
    <div class="page-subtitle">Informations de compte — Session active via IAM-PAM</div>

    ${pamUser ? `
    <div class="pam-badge">
      <span class="pam-badge-icon">🔐</span>
      <div>
        <strong>Accès PAM sécurisé</strong> — Session établie pour <strong>${pamUser}</strong>
        via le portail IAM-PAM.&nbsp; Connecté le : <strong>${now}</strong>
      </div>
    </div>` : ''}

    <div class="profile-hero">
      <div class="profile-avatar">${user.initials}</div>
      <div>
        <div class="profile-name">${user.fullName}</div>
        <div class="profile-email">${user.email}</div>
        <span class="role-badge ${user.roleClass}">${user.role}</span>
      </div>
    </div>

    <div class="profile-grid">
      <div class="info-card">
        <div class="info-card-title">Informations personnelles</div>
        <div class="info-row">
          <span class="info-label">Nom complet</span>
          <span class="info-value">${user.fullName}</span>
        </div>
        <div class="info-row">
          <span class="info-label">Email</span>
          <span class="info-value">${user.email}</span>
        </div>
        <div class="info-row">
          <span class="info-label">Téléphone</span>
          <span class="info-value">${user.phone}</span>
        </div>
        <div class="info-row">
          <span class="info-label">Localisation</span>
          <span class="info-value">${user.location}</span>
        </div>
      </div>

      <div class="info-card">
        <div class="info-card-title">Informations professionnelles</div>
        <div class="info-row">
          <span class="info-label">Rôle</span>
          <span class="info-value">${user.role}</span>
        </div>
        <div class="info-row">
          <span class="info-label">Département</span>
          <span class="info-value">${user.department}</span>
        </div>
        <div class="info-row">
          <span class="info-label">Membre depuis</span>
          <span class="info-value">${user.memberSince}</span>
        </div>
        <div class="info-row">
          <span class="info-label">Dernière connexion</span>
          <span class="info-value">${user.lastLogin}</span>
        </div>
      </div>
    </div>

    <div class="action-row">
      <button class="btn btn-primary" onclick="alert('Modification de profil disponible en accès direct.')">✏️ Modifier le profil</button>
      <button class="btn btn-outline" onclick="alert('Changement de mot de passe disponible en accès direct.')">🔑 Changer le mot de passe</button>
      <a href="/logout" class="btn btn-danger">🚪 Se déconnecter</a>
    </div>
  </main>
</div>

</body>
</html>`;
}

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`ESPRIT Corp Intranet running on http://localhost:${PORT}`);
  console.log(`Shared credentials: ${SHARED_USER} / ${SHARED_PASS}`);
});
