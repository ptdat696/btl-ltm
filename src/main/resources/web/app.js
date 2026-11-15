const $ = id => document.getElementById(id);
let ws = null, nick = '', room = '', loggedIn = false;

const HELP = "Lệnh: /join <phòng> · /leave · /rooms · /users · /pm <tên> <nd> · /quit";

function send(line) { if (ws && ws.readyState === 1) ws.send(line); }

function toWire(input) {
  if (!input.startsWith('/')) return 'MSG ' + input;
  const p = input.slice(1).split(/\s+/);
  switch ((p[0] || '').toLowerCase()) {
    case 'join':  return p[1] ? 'JOIN ' + p[1] : null;
    case 'leave': return 'LEAVE';
    case 'rooms': return 'ROOMS';
    case 'users': return 'USERS';
    case 'pm':    return p.length >= 3 ? 'PM ' + p[1] + ' ' + input.slice(input.indexOf(p[1], 3) + p[1].length).trim() : null;
    case 'quit':  return 'QUIT';
    default:      return null;
  }
}

function hhmm() { return new Date().toTimeString().slice(0, 5); }

function addLine(cls, text) {
  const d = document.createElement('div');
  d.className = cls; d.textContent = text;
  push(d);
}

function addMsg(who, text, mine, isPm, time) {
  const d = document.createElement('div');
  d.className = 'msg' + (mine ? ' me' : '') + (isPm ? ' pm' : '');
  const w = document.createElement('span'); w.className = 'who'; w.textContent = who;
  const t = document.createElement('span'); t.className = 'time'; t.textContent = time || hhmm();
  w.appendChild(t);
  d.appendChild(w);
  d.appendChild(document.createTextNode(text));
  push(d);
}

function push(el) {
  const log = $('log');
  const atBottom = log.scrollHeight - log.scrollTop - log.clientHeight < 80;
  log.appendChild(el);
  if (atBottom || el.classList.contains('me')) log.scrollTop = log.scrollHeight;
}

function connect(name) {
  nick = name;
  $('loginErr').textContent = '';
  ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws');
  ws.onopen = () => send('NICK ' + nick);
  ws.onmessage = e => handle(e.data);
  ws.onclose = () => {
    if (loggedIn) { addLine('err', 'Mất kết nối tới server. Tải lại trang để vào lại.'); $('msg').disabled = true; }
    else $('loginErr').textContent = $('loginErr').textContent || 'Không kết nối được tới server.';
  };
}

function handle(line) {
  const f = line.split('\t');
  switch (f[0]) {
    case 'NICKOK':
      nick = f[1]; loggedIn = true;
      $('login').style.display = 'none';
      $('app').style.display = 'grid';
      $('me').textContent = 'Bạn: ' + nick;
      send('WATCH');
      $('msg').focus();
      break;
    case 'ERROR':
      if (!loggedIn) { $('loginErr').textContent = f[1]; ws.close(); } else addLine('err', f[1]);
      break;
    case 'INFO':   if (loggedIn) addLine('sys', f[1]); break;
    case 'JOINED':
      room = f[1]; $('roomTitle').textContent = room; $('log').innerHTML = '';
      addLine('sys', "Bạn đã vào phòng '" + room + "'");
      markActive();
      break;
    case 'SYSTEM': addLine('sys', f[2]); break;
    case 'CHAT':   addMsg(f[2], f[3], f[2].toLowerCase() === nick.toLowerCase(), false); break;
    case 'HISTORY': addMsg(f[3], f[4], f[3].toLowerCase() === nick.toLowerCase(), false, f[2]); break;
    case 'PM': {
      const mine = f[1].toLowerCase() === nick.toLowerCase();
      addMsg(mine ? 'Riêng → ' + f[2] : 'Riêng từ ' + f[1], f[3], mine, true);
      break;
    }
    case 'ROOMS': renderRooms(f[1] || ''); break;
    case 'USERS': renderUsers(f[2] || ''); break;
  }
}

function renderRooms(s) {
  const ul = $('rooms'); ul.innerHTML = '';
  s.split(',').filter(Boolean).forEach(r => {
    const i = r.lastIndexOf(':'), name = r.slice(0, i), n = r.slice(i + 1);
    const li = document.createElement('li');
    li.dataset.room = name;
    const a = document.createElement('span'); a.textContent = '# ' + name;
    const c = document.createElement('span'); c.className = 'count'; c.textContent = n;
    li.append(a, c);
    li.onclick = () => send('JOIN ' + name);
    ul.appendChild(li);
  });
  markActive();
}

function markActive() {
  document.querySelectorAll('#rooms li').forEach(li => li.classList.toggle('active', li.dataset.room === room));
}

function renderUsers(s) {
  const ul = $('users'); ul.innerHTML = '';
  s.split(',').filter(Boolean).forEach(u => {
    const li = document.createElement('li');
    const self = u.toLowerCase() === nick.toLowerCase();
    li.textContent = self ? u + ' (bạn)' : u;
    if (self) li.className = 'self';
    else li.onclick = () => { $('msg').value = '/pm ' + u + ' '; $('msg').focus(); };
    ul.appendChild(li);
  });
}

$('loginForm').onsubmit = e => {
  e.preventDefault();
  const n = $('nick').value.trim();
  if (n) connect(n);
};

$('sendForm').onsubmit = e => {
  e.preventDefault();
  const text = $('msg').value.trim();
  $('msg').value = '';
  if (!text) return;
  if (text.toLowerCase() === '/help') { addLine('sys', HELP); return; }
  const wire = toWire(text);
  if (!wire) { addLine('err', HELP); return; }
  send(wire);
  if (wire === 'QUIT') setTimeout(() => location.reload(), 300);
};

$('newRoom').onclick = () => {
  const r = prompt('Tên phòng (chữ, số, _ hoặc -):');
  if (r && r.trim()) send('JOIN ' + r.trim());
};
