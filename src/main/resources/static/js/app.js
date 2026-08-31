const state = {
  me: null,
  view: "discover",
  users: [],
  person: null,
  swaps: [],
  messages: [],
  stats: {},
  adminUsers: [],
  adminSwaps: [],
};

const PALETTE = ["#2f5d4a", "#b85c38", "#3b4e7a", "#7a4e2e", "#4d6b3a", "#8d3d5b"];

function avatarColor(name) {
  let h = 0;
  for (const ch of name || "") h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return PALETTE[h % PALETTE.length];
}

async function api(path, options = {}) {
  const res = await fetch(path, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : {};
  if (!res.ok) throw new Error(data.error || "Request failed");
  return data;
}

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content;
}

function pills(list, cls) {
  if (!list || !list.length) return `<span class="hint">None listed</span>`;
  return `<div class="pills">${list.map((s) => `<span class="pill ${cls || ""}">${escapeHtml(s)}</span>`).join("")}</div>`;
}

function escapeHtml(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[c]));
}

function stars(n) {
  const v = Math.round(n || 0);
  return "★".repeat(v) + "☆".repeat(Math.max(0, 5 - v));
}

function nav() {
  const bar = document.getElementById("nav");
  if (!state.me) {
    bar.innerHTML = `<button data-go="auth" class="${state.view === "auth" ? "active" : ""}">Sign in</button>`;
    return;
  }
  bar.innerHTML = `
    <button data-go="discover" class="${state.view === "discover" ? "active" : ""}">Discover</button>
    <button data-go="swaps" class="${state.view === "swaps" ? "active" : ""}">Swaps</button>
    <button data-go="profile" class="${state.view === "profile" ? "active" : ""}">My desk</button>
    ${state.me.isAdmin ? `<button data-go="admin" class="${state.view === "admin" ? "active" : ""}">Admin</button>` : ""}
    <button id="logout">Log out</button>
  `;
  const logout = bar.querySelector("#logout");
  if (logout) {
    logout.onclick = async () => {
      await api("/api/logout", { method: "POST" });
      state.me = null;
      state.view = "auth";
      render();
    };
  }
  bar.querySelectorAll("[data-go]").forEach((b) => {
    b.onclick = async () => {
      state.view = b.dataset.go;
      if (state.view === "discover") state.users = await api("/api/users");
      if (state.view === "swaps") state.swaps = await api("/api/swaps");
      if (state.view === "admin" && state.me?.isAdmin) {
        state.adminUsers = await api("/api/admin/users");
        state.adminSwaps = await api("/api/admin/swaps");
      }
      render();
    };
  });
}

function renderBanner() {
  const box = document.getElementById("banner");
  if (!state.messages.length) {
    box.classList.add("hidden");
    box.innerHTML = "";
    return;
  }
  box.classList.remove("hidden");
  box.textContent = "Desk note · " + state.messages[0].text;
}

function render() {
  nav();
  renderBanner();
  const app = document.getElementById("app");
  if (!state.me && state.view !== "auth") state.view = "auth";
  const views = { auth: viewAuth, discover: viewDiscover, profile: viewProfile, person: viewPerson, swaps: viewSwaps, admin: viewAdmin };
  app.innerHTML = "";
  app.appendChild((views[state.view] || viewDiscover)());
  app.querySelectorAll("[data-go]").forEach((b) => b.onclick = () => { state.view = b.dataset.go; render(); });
}

function viewAuth() {
  const wrap = document.createElement("section");
  wrap.className = "hero";
  wrap.innerHTML = `
    <div>
      <p class="kicker">Issue 01 · Skill barter</p>
      <h1>Bring a skill.<br>Leave with another.</h1>
      <p class="lede">List what you can teach — Photoshop, Java, sourdough, a clean spreadsheet — and ask for something back. Accept, reject, or withdraw a swap before it lands. Afterward, leave a mark in the ledger.</p>
      <div class="stats">
        <div class="stat"><b>${state.stats.users ?? "—"}</b><span>Members</span></div>
        <div class="stat"><b>${state.stats.skills ?? "—"}</b><span>Skills on offer</span></div>
        <div class="stat"><b>${state.stats.swaps ?? "—"}</b><span>Swap requests</span></div>
        <div class="stat"><b>${state.stats.accepted ?? "—"}</b><span>Accepted</span></div>
      </div>
    </div>
    <div class="panel">
      <h2>Take a seat</h2>
      <div class="actions">
        <button class="btn primary" id="tab-login">Sign in</button>
        <button class="btn ghost" id="tab-register">Register</button>
      </div>
      <form id="login-form">
        <label>Email</label><input name="email" type="email" required value="aisha@skillswap.local" />
        <label>Password</label><input name="password" type="password" required value="aisha123" />
        <div class="actions"><button class="btn primary" type="submit">Enter</button></div>
        <p class="error" id="auth-error"></p>
      </form>
      <form id="register-form" class="hidden">
        <div class="row">
          <div><label>Name</label><input name="name" required /></div>
          <div><label>Email</label><input name="email" type="email" required /></div>
        </div>
        <div class="row">
          <div><label>Password</label><input name="password" type="password" required minlength="4" /></div>
          <div><label>Location</label><input name="location" placeholder="City (optional)" /></div>
        </div>
        <label>Availability</label><input name="availability" placeholder="Weekends, evenings…" />
        <label>Skills you offer (comma separated)</label><input name="skillsOffered" placeholder="Java, Guitar" />
        <label>Skills you want</label><input name="skillsWanted" placeholder="Photography" />
        <label><input type="checkbox" name="isPublic" checked style="width:auto" /> Public profile</label>
        <div class="actions"><button class="btn copper" type="submit">Create desk</button></div>
        <p class="error" id="reg-error"></p>
      </form>
      <div class="demo-grid">
        <div class="demo"><span>Aisha · Photoshop</span><button class="btn" data-demo="aisha@skillswap.local" data-pw="aisha123">Try</button></div>
        <div class="demo"><span>Ravi · Java</span><button class="btn" data-demo="ravi@skillswap.local" data-pw="ravi123">Try</button></div>
        <div class="demo"><span>Admin desk</span><button class="btn" data-demo="admin@skillswap.local" data-pw="admin123">Try</button></div>
      </div>
    </div>`;
  wrap.querySelector("#tab-login").onclick = () => {
    wrap.querySelector("#login-form").classList.remove("hidden");
    wrap.querySelector("#register-form").classList.add("hidden");
  };
  wrap.querySelector("#tab-register").onclick = () => {
    wrap.querySelector("#login-form").classList.add("hidden");
    wrap.querySelector("#register-form").classList.remove("hidden");
  };
  wrap.querySelector("#login-form").onsubmit = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      state.me = await api("/api/login", { method: "POST", body: JSON.stringify(Object.fromEntries(fd)) });
      state.view = "discover";
      await boot(false);
    } catch (err) {
      wrap.querySelector("#auth-error").textContent = err.message;
    }
  };
  wrap.querySelector("#register-form").onsubmit = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const body = Object.fromEntries(fd);
    body.isPublic = !!fd.get("isPublic");
    try {
      state.me = await api("/api/register", { method: "POST", body: JSON.stringify(body) });
      state.view = "discover";
      await boot(false);
    } catch (err) {
      wrap.querySelector("#reg-error").textContent = err.message;
    }
  };
  wrap.querySelectorAll("[data-demo]").forEach((btn) => {
    btn.onclick = async () => {
      try {
        state.me = await api("/api/login", {
          method: "POST",
          body: JSON.stringify({ email: btn.dataset.demo, password: btn.dataset.pw }),
        });
        state.view = btn.dataset.demo.startsWith("admin") ? "admin" : "discover";
        await boot(false);
      } catch (err) {
        wrap.querySelector("#auth-error").textContent = err.message;
      }
    };
  });
  return wrap;
}

function userCard(u) {
  return `
    <article class="card" data-id="${u.id}">
      <div class="who">
        <div class="avatar" style="background:${avatarColor(u.name)}">${escapeHtml(u.initials)}</div>
        <div>
          <strong>${escapeHtml(u.name)}</strong>
          <p class="meta">${escapeHtml(u.location || "Somewhere")} · ${escapeHtml(u.availability || "Flexible")}</p>
          <div class="stars" title="${u.rating} from ${u.ratingCount} reviews">${stars(u.rating)} <span class="meta">${u.ratingCount ? u.rating : "new"}</span></div>
        </div>
      </div>
      <div><p class="meta">Offers</p>${pills(u.skillsOffered)}</div>
      <div><p class="meta">Wants</p>${pills(u.skillsWanted, "want")}</div>
      <button class="btn" data-open="${u.id}">Open desk</button>
    </article>`;
}

function viewDiscover() {
  const wrap = document.createElement("section");
  wrap.innerHTML = `
    <p class="kicker">The floor</p>
    <h2>Who is around the desk</h2>
    <p class="lede">Search a skill — Photoshop, Excel, Guitar — or browse the public ledgers. Private desks stay out of sight.</p>
    <div class="toolbar">
      <input id="q" placeholder="Search names or skills" />
      <input id="skill" placeholder="Filter by skill" />
      <button class="btn primary" id="search">Search</button>
    </div>
    <div class="grid" id="cards">${state.users.map(userCard).join("") || `<div class="empty">No public desks match that.</div>`}</div>
  `;
  const run = async () => {
    const q = wrap.querySelector("#q").value;
    const skill = wrap.querySelector("#skill").value;
    state.users = await api(`/api/users?q=${encodeURIComponent(q)}&skill=${encodeURIComponent(skill)}`);
    wrap.querySelector("#cards").innerHTML = state.users.map(userCard).join("") || `<div class="empty">No public desks match that.</div>`;
    bindCards(wrap);
  };
  wrap.querySelector("#search").onclick = run;
  wrap.querySelector("#skill").addEventListener("keydown", (e) => { if (e.key === "Enter") run(); });
  wrap.querySelector("#q").addEventListener("keydown", (e) => { if (e.key === "Enter") run(); });
  bindCards(wrap);
  return wrap;
}

function bindCards(root) {
  root.querySelectorAll("[data-open]").forEach((b) => {
    b.onclick = async () => {
      state.person = await api("/api/users/" + b.dataset.open);
      state.view = "person";
      render();
    };
  });
}

function viewPerson() {
  const u = state.person;
  const me = state.me;
  const wrap = document.createElement("section");
  if (!u) {
    wrap.innerHTML = `<p>No one here.</p>`;
    return wrap;
  }
  const mySkills = me?.skillsOffered || [];
  const theirSkills = u.skillsOffered || [];
  wrap.innerHTML = `
    <p class="kicker"><button class="btn ghost" data-go="discover">← Floor</button></p>
    <div class="split">
      <div>
        <div class="who">
          <div class="avatar" style="background:${avatarColor(u.name)}">${escapeHtml(u.initials)}</div>
          <div>
            <h2>${escapeHtml(u.name)}</h2>
            <p class="meta">${escapeHtml(u.location || "Somewhere")} · ${escapeHtml(u.availability || "Flexible")} · ${u.isPublic ? "Public" : "Private"}</p>
            <div class="stars">${stars(u.rating)} ${u.ratingCount ? u.rating + " · " + u.ratingCount + " notes" : "No ratings yet"}</div>
          </div>
        </div>
        <p class="lede">${escapeHtml(u.bio || "No bio yet.")}</p>
        <h3>Offers</h3>${pills(u.skillsOffered)}
        <h3>Wants</h3>${pills(u.skillsWanted, "want")}
        <h3>Feedback</h3>
        <ul>${(u.feedback || []).map((f) => `<li>${escapeHtml(f)}</li>`).join("") || "<li class='hint'>Quiet so far.</li>"}</ul>
      </div>
      <div class="panel">
        <h2>Propose a swap</h2>
        <p class="hint">You teach something they want; they teach something you want. They can accept or refuse. You may withdraw while it is still pending.</p>
        <label>You offer</label>
        <select id="offer">${mySkills.map((s) => `<option>${escapeHtml(s)}</option>`).join("")}</select>
        <label>You want from ${escapeHtml(u.name)}</label>
        <select id="want">${theirSkills.map((s) => `<option>${escapeHtml(s)}</option>`).join("")}</select>
        <label>Note</label>
        <textarea id="note" placeholder="When, how long, what you hope to cover…"></textarea>
        <div class="actions"><button class="btn copper" id="send" ${me && me.id !== u.id ? "" : "disabled"}>Send request</button></div>
        <p class="error" id="swap-error"></p>
      </div>
    </div>
  `;
  wrap.querySelector("#send").onclick = async () => {
    try {
      await api("/api/swaps", {
        method: "POST",
        body: JSON.stringify({
          toUserId: u.id,
          skillOffered: wrap.querySelector("#offer").value,
          skillWanted: wrap.querySelector("#want").value,
          message: wrap.querySelector("#note").value,
        }),
      });
      state.view = "swaps";
      state.swaps = await api("/api/swaps");
      render();
    } catch (err) {
      wrap.querySelector("#swap-error").textContent = err.message;
    }
  };
  wrap.querySelector("[data-go]").onclick = () => { state.view = "discover"; render(); };
  return wrap;
}

function viewProfile() {
  const u = state.me;
  const wrap = document.createElement("section");
  wrap.innerHTML = `
    <p class="kicker">Your desk</p>
    <h2>How you show up</h2>
    <form class="panel" id="profile">
      <div class="row">
        <div><label>Name</label><input name="name" value="${escapeHtml(u.name)}" /></div>
        <div><label>Location</label><input name="location" value="${escapeHtml(u.location || "")}" /></div>
      </div>
      <label>Photo URL (optional)</label><input name="profilePhoto" value="${escapeHtml(u.profilePhoto || "")}" />
      <label>Availability</label><input name="availability" value="${escapeHtml(u.availability || "")}" />
      <label>Bio</label><textarea name="bio">${escapeHtml(u.bio || "")}</textarea>
      <label>Skills offered</label><input name="skillsOffered" value="${escapeHtml((u.skillsOffered || []).join(", "))}" />
      <label>Skills wanted</label><input name="skillsWanted" value="${escapeHtml((u.skillsWanted || []).join(", "))}" />
      <label><input type="checkbox" name="isPublic" ${u.isPublic ? "checked" : ""} style="width:auto" /> Public profile</label>
      <div class="actions"><button class="btn primary" type="submit">Save</button></div>
      <p class="error" id="prof-error"></p>
      <p class="hint" id="prof-ok"></p>
    </form>
  `;
  wrap.querySelector("#profile").onsubmit = async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      state.me = await api("/api/me", {
        method: "PUT",
        body: JSON.stringify({
          name: fd.get("name"),
          location: fd.get("location"),
          profilePhoto: fd.get("profilePhoto"),
          availability: fd.get("availability"),
          bio: fd.get("bio"),
          skillsOffered: fd.get("skillsOffered"),
          skillsWanted: fd.get("skillsWanted"),
          isPublic: !!fd.get("isPublic"),
        }),
      });
      wrap.querySelector("#prof-ok").textContent = "Saved.";
    } catch (err) {
      wrap.querySelector("#prof-error").textContent = err.message;
    }
  };
  return wrap;
}

function viewSwaps() {
  const me = state.me;
  const incoming = state.swaps.filter((s) => s.toId === me.id);
  const outgoing = state.swaps.filter((s) => s.fromId === me.id);
  const wrap = document.createElement("section");
  wrap.innerHTML = `
    <p class="kicker">The ledger</p>
    <h2>Requests in motion</h2>
    <div class="split">
      <div>
        <h3>Incoming</h3>
        <div class="list">${incoming.map((s) => swapCard(s, "in")).join("") || `<div class="empty">Nothing waiting.</div>`}</div>
      </div>
      <div>
        <h3>Outgoing</h3>
        <div class="list">${outgoing.map((s) => swapCard(s, "out")).join("") || `<div class="empty">You have not sent a request.</div>`}</div>
      </div>
    </div>
  `;
  wrap.querySelectorAll("[data-act]").forEach((b) => {
    b.onclick = async () => {
      const id = b.dataset.id;
      try {
        if (b.dataset.act === "accept") await api(`/api/swaps/${id}/accept`, { method: "POST" });
        if (b.dataset.act === "reject") await api(`/api/swaps/${id}/reject`, { method: "POST" });
        if (b.dataset.act === "delete") await api(`/api/swaps/${id}`, { method: "DELETE" });
        if (b.dataset.act === "feedback") {
          const rating = Number(wrap.querySelector(`[data-rate="${id}"]`).value);
          const comment = wrap.querySelector(`[data-comment="${id}"]`).value;
          await api(`/api/swaps/${id}/feedback`, { method: "POST", body: JSON.stringify({ rating, comment }) });
        }
        state.swaps = await api("/api/swaps");
        render();
      } catch (err) {
        alert(err.message);
      }
    };
  });
  return wrap;
}

function swapCard(s, dir) {
  const other = dir === "in" ? s.fromName : s.toName;
  const canRespond = dir === "in" && s.status === "pending";
  const canDelete = dir === "out" && s.status === "pending";
  const canRate = (s.status === "accepted" || s.status === "completed");
  const already = dir === "in" ? s.toRating : s.fromRating;
  return `
    <article class="swap ${s.status}">
      <div class="status">${escapeHtml(s.status)} · ${escapeHtml(s.createdAt)}</div>
      <strong>${escapeHtml(other)}</strong>
      <p>${escapeHtml(s.fromName)} teaches <em>${escapeHtml(s.skillOffered)}</em> · ${escapeHtml(s.toName)} teaches <em>${escapeHtml(s.skillWanted)}</em></p>
      ${s.message ? `<p class="hint">“${escapeHtml(s.message)}”</p>` : ""}
      <div class="actions">
        ${canRespond ? `<button class="btn primary" data-act="accept" data-id="${s.id}">Accept</button>
                        <button class="btn danger" data-act="reject" data-id="${s.id}">Reject</button>` : ""}
        ${canDelete ? `<button class="btn" data-act="delete" data-id="${s.id}">Withdraw</button>` : ""}
      </div>
      ${canRate && !already ? `
        <label>Rating</label>
        <select data-rate="${s.id}">
          <option>5</option><option>4</option><option>3</option><option>2</option><option>1</option>
        </select>
        <input data-comment="${s.id}" placeholder="How did it go?" />
        <button class="btn copper" data-act="feedback" data-id="${s.id}">Leave note</button>` : ""}
      ${already ? `<p class="hint">You rated this ${already}/5.</p>` : ""}
    </article>`;
}

function viewAdmin() {
  const wrap = document.createElement("section");
  wrap.innerHTML = `
    <p class="kicker">Back office</p>
    <h2>Keep the desk honest</h2>
    <div class="panel">
      <h3>Broadcast</h3>
      <div class="toolbar">
        <input id="msg" placeholder="Feature update, downtime, a kind reminder…" style="max-width:none;flex:1" />
        <button class="btn copper" id="cast">Send</button>
      </div>
      <div class="actions">
        <a class="btn" href="/api/admin/reports/users.csv">Users CSV</a>
        <a class="btn" href="/api/admin/reports/swaps.csv">Swaps CSV</a>
        <a class="btn" href="/api/admin/reports/feedback.csv">Feedback CSV</a>
      </div>
    </div>
    <h3>Members</h3>
    <table>
      <thead><tr><th>Name</th><th>Email</th><th>Skills</th><th>Status</th><th></th></tr></thead>
      <tbody>
        ${state.adminUsers.map((u) => `
          <tr>
            <td>${escapeHtml(u.name)}</td>
            <td>${escapeHtml(u.email || "")}</td>
            <td>${escapeHtml((u.skillsOffered || []).join(", "))}</td>
            <td>${u.isBanned ? "Banned" : (u.isPublic ? "Public" : "Private")}${u.isAdmin ? " · Admin" : ""}</td>
            <td>
              ${u.isAdmin ? "" : (u.isBanned
                ? `<button class="btn" data-unban="${u.id}">Unban</button>`
                : `<button class="btn danger" data-ban="${u.id}">Ban</button>`)}
              ${(u.skillsOffered || []).map((s) => `<button class="btn" data-rm="${u.id}" data-skill="${escapeHtml(s)}">Strip ${escapeHtml(s)}</button>`).join("")}
            </td>
          </tr>`).join("")}
      </tbody>
    </table>
    <h3>All swaps</h3>
    <table>
      <thead><tr><th>From</th><th>To</th><th>Trade</th><th>Status</th><th>When</th></tr></thead>
      <tbody>
        ${state.adminSwaps.map((s) => `
          <tr>
            <td>${escapeHtml(s.fromName)}</td>
            <td>${escapeHtml(s.toName)}</td>
            <td>${escapeHtml(s.skillOffered)} → ${escapeHtml(s.skillWanted)}</td>
            <td>${escapeHtml(s.status)}</td>
            <td>${escapeHtml(s.createdAt)}</td>
          </tr>`).join("")}
      </tbody>
    </table>
  `;
  wrap.querySelector("#cast").onclick = async () => {
    try {
      await api("/api/admin/broadcast", { method: "POST", body: JSON.stringify({ message: wrap.querySelector("#msg").value }) });
      state.messages = await api("/api/messages");
      render();
    } catch (err) { alert(err.message); }
  };
  wrap.querySelectorAll("[data-ban]").forEach((b) => b.onclick = async () => {
    await api(`/api/admin/users/${b.dataset.ban}/ban`, { method: "POST" });
    state.adminUsers = await api("/api/admin/users");
    render();
  });
  wrap.querySelectorAll("[data-unban]").forEach((b) => b.onclick = async () => {
    await api(`/api/admin/users/${b.dataset.unban}/unban`, { method: "POST" });
    state.adminUsers = await api("/api/admin/users");
    render();
  });
  wrap.querySelectorAll("[data-rm]").forEach((b) => b.onclick = async () => {
    await api(`/api/admin/users/${b.dataset.rm}/remove-skill`, { method: "POST", body: JSON.stringify({ skill: b.dataset.skill }) });
    state.adminUsers = await api("/api/admin/users");
    render();
  });
  return wrap;
}

async function boot(keepView = true) {
  try { state.me = await api("/api/me"); } catch { state.me = null; }
  try { state.messages = await api("/api/messages"); } catch { state.messages = []; }
  try { state.stats = await api("/api/stats"); } catch { state.stats = {}; }
  if (state.me) {
    state.users = await api("/api/users");
    state.swaps = await api("/api/swaps");
    if (state.me.isAdmin) {
      state.adminUsers = await api("/api/admin/users");
      state.adminSwaps = await api("/api/admin/swaps");
    }
    if (!keepView) { /* view already set */ }
    else if (state.view === "auth") state.view = "discover";
  } else {
    state.view = "auth";
  }
  render();
}

document.querySelector(".wordmark").addEventListener("click", (e) => {
  e.preventDefault();
  state.view = state.me ? "discover" : "auth";
  render();
});

boot();
