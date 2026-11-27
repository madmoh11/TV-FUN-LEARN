/* The flight simulator on the TV, whichever engine draws the world: the pilot's inputs, the flying, the chase camera's motion,
   the instruments and the sound. An engine (cz.js for strong TVs, light.js for old graphics chips) draws what tick() describes. */
(function () {
  'use strict';
  var F = window.FlyCore, D = Math.PI / 180;
  var keys = {}, kthr = null, kgear = null, kflap = null;
  /* Outside the TV app (a desktop browser, a test) there is no bridge: the keyboard flies and nothing is reported. */
  var L = window.Lamma || { conf: function () { var q = new URLSearchParams(location.search); return JSON.stringify({ g: q.get('g') || '', ion: q.get('ion') || '', lang: q.get('lang') || 'ar', q: +(q.get('q') || 1), spot: +(q.get('spot') || 1), eng: +(q.get('eng') || 0) }); },
    poll: function () { return '0,0,-1,0,0,0,0,1,0,-1,-1,0,1'; }, tell: function () {} };
  var conf = {}; try { conf = JSON.parse(L.conf()); } catch (e) {}
  var AR = conf.lang !== 'en', Q = F.clamp(conf.q == null ? 1 : conf.q | 0, 0, 2);
  var T = AR ? { kmh: 'كم/س', m: 'م', load: 'جاري تحميل عمّان…', stall: 'السرعة قليلة! زِد قوة المحرك', hard: 'هبوط قاسي!', ground: 'على الأرض', spots: 'إلى أين نطير؟', thr: 'المحرك',
      nogl: 'هذا التلفزيون لا يدعم الرسم ثلاثي الأبعاد، فالمحاكي لا يعمل عليه.', google: 'عمّان ثلاثية الأبعاد', sat: 'صور الأقمار', light: 'صور الأقمار على تضاريس حقيقية', basic: 'خريطة بسيطة (لا يوجد إنترنت)', nokey: 'بدون مفتاح خرائط: أرض مصوّرة بلا مبانٍ', badkey: 'مفتاح الخرائط لم يعمل: أرض مصوّرة بلا مبانٍ', N: 'ش', E: 'ق', S: 'ج', W: 'غ', auto: 'توازن تلقائي', weak: 'التلفزيون ضعيف على المباني المجسّمة: رجعنا لصور الأقمار', gear: 'العجلات', flaps: 'القلّابات', brake: 'فرامل', rwy: 'المدرج', km: 'كم', soft: 'هبوط ناعم 👏', good: 'هبوط جيد 👍', firm: 'هبوط قاسٍ شوي', belly: 'هبوط بدون عجلات!', onrw: 'على المدرج ✅', offrw: 'خارج المدرج', nogear: 'نزّل العجلات!', close: '↩ كمّل الطيران', gearUp: 'ارفع العجلات', gearDn: 'نزّل العجلات', exit: 'اخرج من الطيران', keys: 'الأسهم: التوجيه   •   OK: القائمة   •   الصوت + −: المحرك   •   رجوع: خروج' }
    : { kmh: 'km/h', m: 'm', load: 'Loading Amman…', stall: 'Too slow! Add power', hard: 'Hard landing!', ground: 'On the ground', spots: 'Where to?', thr: 'Engine',
      nogl: 'This TV cannot draw 3D, so the simulator does not run on it.', google: 'Amman in 3D', sat: 'Satellite pictures', light: 'Satellite pictures on real terrain', basic: 'Simple map (no internet)', nokey: 'No map key: pictured ground without buildings', badkey: 'The map key did not work: pictured ground without buildings', N: 'N', E: 'E', S: 'S', W: 'W', auto: 'Auto-level', weak: 'This TV is too weak for the 3D buildings: back to satellite pictures', gear: 'Wheels', flaps: 'Flaps', brake: 'Brakes', rwy: 'Runway', km: 'km', soft: 'A soft landing 👏', good: 'A good landing 👍', firm: 'A firm landing', belly: 'Landed with the wheels up!', onrw: 'on the runway ✅', offrw: 'off the runway', nogear: 'Wheels down!', close: '↩ Keep flying', gearUp: 'Wheels up', gearDn: 'Wheels down', exit: 'Leave the flight', keys: 'Arrows: steer   •   OK: the list   •   Volume + −: engine   •   Back: leave' };
  /* The places of the quick-jump list. elev is the height of the land there above the sea. */
  var SPOTS = [
    { ar: 'مطار الملكة علياء: الإقلاع', en: 'Queen Alia airport: take-off', lat: 31.7225, lon: 35.9932, elev: 719, hdg: 80, land: true },
    { ar: 'أبراج العبدلي والبوليفارد', en: 'Abdali towers and the Boulevard', lat: 31.9634, lon: 35.9065, elev: 940, hdg: 35 },
    { ar: 'جسر عبدون', en: 'Abdoun Bridge', lat: 31.9482, lon: 35.8825, elev: 880, hdg: 60 },
    { ar: 'جبل القلعة ووسط البلد', en: 'The Citadel and downtown', lat: 31.9544, lon: 35.9349, elev: 850, hdg: 100 },
    { ar: 'البحر الميت', en: 'The Dead Sea', lat: 31.5590, lon: 35.4732, elev: -430, hdg: 250 },
    { ar: 'مطار الملكة علياء: الهبوط', en: 'Queen Alia airport: landing', lat: 31.7225, lon: 35.9932, elev: 719, hdg: 80, final: true }];
  /* Queen Alia's northern runway, 08L/26R: both ends, from the OurAirports data (public domain). 3,660 m long and 61 m wide. */
  var RW = { lat1: 31.7267, lon1: 35.9696, lat2: 31.7322, lon2: 36.0075, elev: 719, width: 61 };
  var RWH = F.approach(F.start(RW.lat1, RW.lon1, RW.elev, 81, 0), RW).hdg;
  /** A point d metres along the runway from its western end (negative: before it). */
  function onLine(d) { return [RW.lat1 + d * Math.cos(RWH) / 6371000 / D, RW.lon1 + d * Math.sin(RWH) / (6371000 * Math.cos(RW.lat1 * D)) / D]; }
  var $ = function (id) { return document.getElementById(id); };
  function say(t) { $('msg').textContent = t; $('msg').hidden = !t; }
  function tell(extra) { try { L.tell(extra); } catch (e) {} }
  var B = null;                 // the engine drawing the world
  var lastErr = '', cpu = 0, f0 = 0, fps = -1, fpsN = 0, fpsAt = performance.now();
  addEventListener('error', function (e) { lastErr = ('' + (e.message || e)).slice(0, 90); });
  addEventListener('unhandledrejection', function (e) { lastErr = ('' + (e.reason && e.reason.message || e.reason)).slice(0, 90); });
  var shake = 0, touchSeen = 0, touchMsg = '', touchUntil = 0, sndOn = true, ap = null;
  var s = null, spot = 1, groundH = null, groundAt = 0, camH = 0, camP = 0, camR = 0, camRange = 110, menu = false, menuAt = 1, lvlSeq = '0', goSeq = '0', ready = false, last = 0, hudAt = 0, tellAt = 0, started = performance.now();
  var view = { s: null, camH: 0, camE: 0, camR: 0, range: 110, fov: 1, guides: false, now: 0, dt: 0 };
  function go(i) {
    spot = F.clamp(i | 0, 0, SPOTS.length - 1); var p = SPOTS[spot], base = B && B.flat ? 0 : p.elev;
    RW.elev = base;
    if (p.land) { var a = onLine(90); s = F.start(a[0], a[1], base + F.GEAR, RWH / D, 0); s.onGround = true; }                      // lined up at the start of the runway
    else if (p.final) { var b = onLine(-9000); s = F.start(b[0], b[1], base + F.GEAR + 9300 * Math.tan(3 * D), RWH / D, 80);          // nine kilometres out, on the glide path, ready to land
      s.gear = s.gearWant = 1; s.flaps = s.flapPos = 2; s.thr = 0.4; s.pitch = -3 * D; }
    else { // arrive from a little way off, already flying toward the landmark
      var back = 2600, h = p.hdg * D; s = F.start(p.lat - back * Math.cos(h) / 6371000 / D, p.lon - back * Math.sin(h) / (6371000 * Math.cos(p.lat * D)) / D, base + 420, p.hdg, 135); }
    groundH = base; groundAt = 0; camH = s.hdg; camP = 0; camR = 0; kthr = null; touchSeen = 0; touchUntil = 0; shake = 0; if (B && B.moved) B.moved(p);
  }
  function sampleGround(now) {
    if (now - groundAt < (s.alt - (groundH || 0) > 700 ? 1500 : 350)) return; groundAt = now; var h;
    try { h = B.ground(s.lat, s.lon); } catch (e) {}
    if (h !== undefined && h !== null && isFinite(h) && h > -600 && h < 3000) groundH = h;
  }

  /* ---- what the pilot asks for: the phone first, the TV remote or a keyboard when no phone is flying ---- */
  /* A key from a keyboard, or from the TV remote by way of the app. With the list open the arrows move in it; otherwise they fly the plane. */
  var kbrake = false, klevel = false, phone = false;
  function items() { var a = [{ t: T.close, f: function () {} }, { t: '⚖️ ' + T.auto, f: function () { klevel = true; } },
      { t: '🛞 ' + (s.gearWant > 0.5 ? T.gearUp : T.gearDn), f: function () { kgear = !(s.gearWant > 0.5); } }, { t: '🪽 ' + T.flaps + ' ' + (s.flaps + 1) % 3, f: function () { kflap = (s.flaps + 1) % 3; } },
      { t: '🛑 ' + T.brake + (kbrake ? ' ✓' : ''), f: function () { kbrake = !kbrake; } }];
    SPOTS.forEach(function (p, i) { a.push({ t: '📍 ' + (AR ? p.ar : p.en), f: function () { go(i); kgear = kflap = null; kbrake = false; } }); });
    a.push({ t: '✕ ' + T.exit, f: function () { try { L.quit && L.quit(); } catch (e) {} } }); return a; }
  function key(k, down) {
    if (!down) { delete keys[k]; return; }
    if (!keys[k] && s) { if (k === 'g') kgear = !(s.gearWant > 0.5); if (k === 'f') kflap = (s.flaps + 1) % 3; }
    keys[k] = 1; audio();
    if (menu) { var n = items().length; if (k === 'ArrowDown') menuAt = (menuAt + 1) % n; else if (k === 'ArrowUp') menuAt = (menuAt + n - 1) % n; else if (k === 'Enter') { var it = items()[menuAt]; menu = false; if (it) it.f(); } showMenu(); }
    else if (k === 'Enter') { menu = true; menuAt = 1; showMenu(); }
  }
  window.flyKey = function (k, down) { key(k, !!down); };
  addEventListener('keydown', function (e) { key(e.key, true); });
  addEventListener('keyup', function (e) { key(e.key, false); });
  function showMenu() { var el = $('menu'); el.hidden = !menu; if (!menu) return; el.dir = AR ? 'rtl' : 'ltr'; var a = items(); if (menuAt >= a.length) menuAt = 0;
    el.innerHTML = '<h2>' + T.spots + '</h2>' + a.map(function (it, i) { return '<div class="' + (i === menuAt ? 'on' : '') + '">' + it.t + '</div>'; }).join(''); }
  function input(dt) {
    var p = ('' + L.poll()).split(','), inp = { x: 0, y: 0, thr: null, level: false, gear: null, flaps: null, brake: false };
    if (p[8] === '1') { inp.x = p[0] / 100; inp.y = p[1] / 100; }
    else if (!menu) { inp.x = (keys.ArrowRight ? 1 : 0) - (keys.ArrowLeft ? 1 : 0); inp.y = (keys.ArrowUp ? 1 : 0) - (keys.ArrowDown ? 1 : 0); }
    if (+p[2] >= 0) inp.thr = p[2] / 100;
    if (keys.MediaFastForward || keys.PageUp || keys.w) kthr = Math.min(1, (kthr == null ? s.thr : kthr) + dt * 0.5);
    if (keys.MediaRewind || keys.PageDown || keys.s) kthr = Math.max(0, (kthr == null ? s.thr : kthr) - dt * 0.5);
    if (inp.thr == null && kthr != null) inp.thr = kthr;
    if (+p[9] >= 0) inp.gear = p[9] === '1'; else if (kgear != null) inp.gear = kgear;
    if (+p[10] >= 0) inp.flaps = +p[10]; else if (kflap != null) inp.flaps = kflap;
    inp.brake = p[11] === '1' || !!keys.b || kbrake; sndOn = p[12] !== '0'; phone = p[8] === '1';
    if (klevel) { klevel = false; inp.level = true; }
    if (p[3] !== lvlSeq) { lvlSeq = p[3]; inp.level = true; }
    if (p[4] !== goSeq) { goSeq = p[4]; go(+p[5]); menu = false; showMenu(); kgear = kflap = null; inp.gear = inp.flaps = null; }
    var m = p[6] === '1'; if (p[8] === '1' && m !== menu) { menu = m; menuAt = 5 + spot; showMenu(); }
    return inp;
  }

  /* ---- one frame: fly, and say where the plane and the camera are. The engine calls this, then draws. ---- */
  function tick() {
    var now = performance.now(), dt = Math.min(0.1, (now - (last || now)) / 1000); last = now; f0 = now;
    if (!s || !B) return null;
    var inp = input(dt);
    if (!ready && (B.ready() || now - started > 15000)) { ready = true; say(''); } else if (!ready && now - started > 2500) say('');
    if (ready) { sampleGround(now); F.step(s, inp, dt, groundH); }
    ap = F.approach(s, RW); if (ap.on && s.onGround && groundH != null) RW.elev = groundH;
    if (s.touch !== touchSeen) { touchSeen = s.touch;                 // the wheels have just met the ground: say how it went, shake the camera, make the sound
      touchMsg = (s.belly ? T.belly : s.touchVs > -2.5 ? T.soft : s.touchVs > -6 ? T.good : s.touchVs > -9 ? T.firm : T.hard) + (ap.show || ap.on ? '  •  ' + (ap.on ? T.onrw : T.offrw) : '');
      touchUntil = now + 5500; shake = s.belly ? 1.6 : F.clamp(-s.touchVs / 6, 0.35, 1.5); thud(s.belly, shake); }
    if (s.stall) shake = Math.max(shake, 0.22); if (s.onGround && s.v > 8) shake = Math.max(shake, s.belly ? 0.5 : 0.05 + s.v / 2500);
    shake *= Math.exp(-dt * 3.2); sound();
    // the camera hangs behind and above on a spring: it is late into every turn and falls back as the plane speeds up
    camH += F.turn(camH, s.hdg) * Math.min(1, dt * 2.3); camP += (s.show - camP) * Math.min(1, dt * 1.6); camR += (s.roll - camR) * Math.min(1, dt * 1.4);
    camRange += (92 + s.v * 0.16 - camRange) * Math.min(1, dt * 1.2);
    view.s = s; view.camH = camH + (Math.random() - 0.5) * shake * 0.012; view.camE = 11 * D - camP * 0.35 - (Math.random() - 0.5) * shake * 0.02; view.camR = camR * 0.22;
    view.range = camRange; view.fov = (58 + F.clamp(s.v / F.VMAX, 0, 1) * 10) * D; view.guides = !s.onGround; view.now = now; view.dt = dt; view.fps = fps; view.started = started;
    fpsN++; if (now - fpsAt > 1000) { fps = Math.round(fpsN * 1000 / (now - fpsAt)); fpsN = 0; fpsAt = now; }
    if (now - hudAt > (B.slowHud ? 160 : 80)) { hudAt = now; hud(); }
    if (now - tellAt > 500) { tellAt = now; tell('on|' + Math.round(s.v * 3.6) + '|' + Math.round(s.alt) + '|' + Math.round(s.hdg / D) + '|' + B.mode + '|' + spot + '|' + Math.round(s.thr * 100) + '|' + (s.onGround ? 1 : 0) + '|' + (ready ? 1 : 0) + '|' + (s.gearWant > 0.5 ? 1 : 0) + '|' + s.flaps + '|' + s.touch + '|' + (ap && ap.show ? Math.round(ap.dist) : '')); }
    return view;
  }

  /* ---- the instruments, drawn over the picture ---- */
  function box(g, x, y, w, h, r) { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath(); }
  function hud() {
    var now = performance.now(), cv = $('hud'), g = cv.getContext('2d'), W = cv.width, H = cv.height, u = H / 540, f = function (px, bold) { g.font = (bold ? 'bold ' : '') + Math.round(px * u) + 'px L, sans-serif'; };
    g.clearRect(0, 0, W, H); g.textBaseline = 'middle';
    var glass = 'rgba(8,16,36,.62)', line = 'rgba(255,255,255,.9)', gold = '#f2c14e';
    // heading tape
    var hd = s.hdg / D, tw = 380 * u, tx = W / 2 - tw / 2, ty = 16 * u, th = 46 * u;
    g.fillStyle = glass; box(g, tx, ty, tw, th, 10 * u); g.fill(); g.save(); box(g, tx, ty, tw, th, 10 * u); g.clip();
    g.strokeStyle = line; g.fillStyle = '#fff'; g.lineWidth = 2 * u; g.textAlign = 'center'; f(15, true);
    for (var a = Math.floor((hd - 50) / 10) * 10; a <= hd + 50; a += 10) { var x = W / 2 + (a - hd) * (tw / 90), n = ((a % 360) + 360) % 360;
      g.beginPath(); g.moveTo(x, ty + th); g.lineTo(x, ty + th - (n % 30 === 0 ? 14 : 8) * u); g.stroke();
      if (n % 30 === 0) g.fillText(n === 0 ? T.N : n === 90 ? T.E : n === 180 ? T.S : n === 270 ? T.W : '' + n / 10, x, ty + 16 * u); }
    g.restore();
    g.fillStyle = gold; g.beginPath(); g.moveTo(W / 2, ty + th + 2 * u); g.lineTo(W / 2 - 9 * u, ty + th + 14 * u); g.lineTo(W / 2 + 9 * u, ty + th + 14 * u); g.fill();
    g.fillStyle = glass; box(g, W / 2 - 46 * u, ty + th + 16 * u, 92 * u, 34 * u, 8 * u); g.fill(); g.fillStyle = '#fff'; f(22, true); g.fillText(('00' + Math.round(((hd % 360) + 360) % 360) % 360).slice(-3) + '°', W / 2, ty + th + 34 * u);
    // speed, left; height above the sea, right
    function gauge(x, big, small, warn) { g.fillStyle = glass; box(g, x, H * 0.36, 150 * u, 92 * u, 14 * u); g.fill(); g.strokeStyle = warn ? '#ff5a4f' : 'rgba(255,255,255,.35)'; g.lineWidth = (warn ? 4 : 2) * u; box(g, x, H * 0.36, 150 * u, 92 * u, 14 * u); g.stroke();
      g.textAlign = 'center'; g.fillStyle = warn ? '#ff8a80' : '#fff'; f(42, true); g.fillText(big, x + 75 * u, H * 0.36 + 38 * u); g.fillStyle = gold; f(17, true); g.fillText(small, x + 75 * u, H * 0.36 + 74 * u); }
    gauge(26 * u, '' + Math.round(s.v * 3.6), T.kmh, s.stall);
    gauge(W - 176 * u, '' + Math.round(s.alt), T.m, false);
    // climbing or sinking, beside the height
    var vsx = W - 196 * u, vy = H * 0.36 + 46 * u, vl = F.clamp(s.vs / 25, -1, 1) * 40 * u; g.strokeStyle = 'rgba(255,255,255,.4)'; g.lineWidth = 3 * u; g.beginPath(); g.moveTo(vsx, vy - 42 * u); g.lineTo(vsx, vy + 42 * u); g.stroke();
    g.strokeStyle = s.vs < -12 ? '#ff5a4f' : '#6fe39a'; g.lineWidth = 7 * u; g.beginPath(); g.moveTo(vsx, vy); g.lineTo(vsx, vy - vl); g.stroke();
    // the artificial horizon
    var cx = 104 * u, cy = H - 108 * u, r = 70 * u; g.save(); g.beginPath(); g.arc(cx, cy, r, 0, 7); g.clip();
    g.translate(cx, cy); g.rotate(-s.roll); var off = F.clamp(s.show / (30 * D), -1, 1) * r;
    g.fillStyle = '#3d8fe0'; g.fillRect(-r * 2, -r * 2 + off, r * 4, r * 2); g.fillStyle = '#8a5a2b'; g.fillRect(-r * 2, off, r * 4, r * 2);
    g.strokeStyle = '#fff'; g.lineWidth = 2.5 * u; g.beginPath(); g.moveTo(-r * 2, off); g.lineTo(r * 2, off); g.stroke();
    g.lineWidth = 1.5 * u; for (var k = -2; k <= 2; k++) if (k) { var yy = off - k * r / 3; g.beginPath(); g.moveTo(-r * 0.25, yy); g.lineTo(r * 0.25, yy); g.stroke(); }
    g.restore(); g.strokeStyle = gold; g.lineWidth = 4 * u; g.beginPath(); g.moveTo(cx - r * 0.62, cy); g.lineTo(cx - r * 0.2, cy); g.lineTo(cx, cy + r * 0.14); g.lineTo(cx + r * 0.2, cy); g.lineTo(cx + r * 0.62, cy); g.stroke();
    g.strokeStyle = 'rgba(255,255,255,.8)'; g.lineWidth = 3 * u; g.beginPath(); g.arc(cx, cy, r, 0, 7); g.stroke();
    // the throttle
    var bx = W - 60 * u, by = H - 178 * u, bh = 140 * u; g.fillStyle = glass; box(g, bx - 8 * u, by - 26 * u, 44 * u, bh + 40 * u, 10 * u); g.fill();
    g.fillStyle = 'rgba(255,255,255,.18)'; g.fillRect(bx, by, 28 * u, bh); g.fillStyle = s.thr > 0.9 ? '#ff8a3d' : '#6fe39a'; g.fillRect(bx, by + bh * (1 - s.thr), 28 * u, bh * s.thr);
    g.fillStyle = '#fff'; f(13, true); g.textAlign = 'center'; g.fillText(Math.round(s.thr * 100) + '%', bx + 14 * u, by - 12 * u);
    // wheels, flaps and brakes, at the bottom
    var px = W / 2 - 150 * u, py = H - 74 * u; g.fillStyle = glass; box(g, px, py, 300 * u, 52 * u, 12 * u); g.fill(); f(14, true); g.textAlign = 'center'; g.fillStyle = '#fff';
    g.fillText(T.gear, px + 52 * u, py + 14 * u); g.fillText(T.flaps, px + 152 * u, py + 14 * u);
    for (var i = 0; i < 3; i++) { g.beginPath(); g.arc(px + (32 + i * 20) * u, py + 36 * u, 7 * u, 0, 7); var moving = s.gear > 0.02 && s.gear < 0.98;
      if (moving) { g.fillStyle = '#ffb02e'; g.fill(); } else if (s.gear >= 0.98) { g.fillStyle = '#39d353'; g.fill(); } else { g.strokeStyle = 'rgba(255,255,255,.6)'; g.lineWidth = 2 * u; g.stroke(); }
      g.fillStyle = i < Math.round(s.flapPos) ? gold : 'rgba(255,255,255,.22)'; if (i < 2) g.fillRect(px + (126 + i * 28) * u, py + 29 * u, 22 * u, 13 * u); }
    g.fillStyle = s.brake ? '#ff5a4f' : 'rgba(255,255,255,.25)'; f(15, true); g.fillText(T.brake, px + 250 * u, py + 27 * u);
    // coming in to land: four lights (two white and two red is right), the runway's side, and how far it is
    if (ap && ap.show && !s.onGround && ap.dist > -300) { var ax = W / 2 - 150 * u, ay = 122 * u; g.fillStyle = glass; box(g, ax, ay, 300 * u, 62 * u, 12 * u); g.fill();
      for (var j = 0; j < 4; j++) { g.beginPath(); g.arc(ax + (26 + j * 24) * u, ay + 20 * u, 9 * u, 0, 7); g.fillStyle = j < 4 - ap.lights ? '#ff4336' : '#ffffff'; g.fill(); }
      g.fillStyle = '#fff'; f(16, true); g.textAlign = 'center'; g.fillText(T.rwy + ' ' + (Math.max(0, ap.dist) / 1000).toFixed(1) + ' ' + T.km, ax + 212 * u, ay + 20 * u);
      var lx0 = ax + 24 * u, lw = 252 * u, ly = ay + 46 * u; g.strokeStyle = 'rgba(255,255,255,.5)'; g.lineWidth = 2 * u; g.beginPath(); g.moveTo(lx0, ly); g.lineTo(lx0 + lw, ly); g.moveTo(lx0 + lw / 2, ly - 8 * u); g.lineTo(lx0 + lw / 2, ly + 8 * u); g.stroke();
      var dx = lx0 + lw / 2 - F.clamp(ap.cross / 500, -1, 1) * lw / 2; g.fillStyle = Math.abs(ap.cross) < 40 ? '#39d353' : gold; g.beginPath(); g.moveTo(dx, ly - 9 * u); g.lineTo(dx + 9 * u, ly); g.lineTo(dx, ly + 9 * u); g.lineTo(dx - 9 * u, ly); g.fill(); }
    // where we are, what the picture is made of, and anything the pilot must know now
    var p = SPOTS[spot]; g.textAlign = AR ? 'right' : 'left'; var lx = AR ? W - 26 * u : 26 * u; g.fillStyle = glass; var nm = AR ? p.ar : p.en; f(19, true); var nw = g.measureText(nm).width + 28 * u;
    box(g, AR ? lx - nw : lx, 16 * u, nw, 38 * u, 10 * u); g.fill(); g.fillStyle = '#fff'; g.fillText(nm, AR ? lx - 14 * u : lx + 14 * u, 36 * u);
    f(13, false); g.fillStyle = 'rgba(255,255,255,.85)'; g.fillText(B.note || (B.mode === 'google' ? T.google : B.mode === 'sat' ? T.sat : B.mode === 'light' ? T.light : B.mode === 'basic' ? T.basic : ''), AR ? lx - 6 * u : lx + 6 * u, 68 * u);
    var warn = now < touchUntil ? touchMsg : s.bump > 0 ? T.hard : (ap && ap.show && !s.onGround && ap.dist < 2500 && ap.dist > -400 && s.gearWant < 0.5 && s.alt - RW.elev < 400) ? T.nogear : s.stall ? T.stall : s.level > 0 ? T.auto : !ready ? T.load : (s.onGround && s.v < 3 ? T.ground : '');
    // for the first minute, one small line says what this TV is drawing with: it is what to photograph when the picture is wrong
    if (now - started < 45000 || !ready || (fps >= 0 && fps < 8)) {
      var dbg = B.name + ' ' + fps + 'fps ' + cpu + 'ms ' + B.info() + (lastErr ? ' ERR ' + lastErr : '');
      g.font = Math.round(12 * u) + 'px monospace'; g.textAlign = 'left'; var rows = dbg.match(/.{1,74}/g) || [];
      for (var ri = 0; ri < rows.length; ri++) { var ry = (300 + ri * 17) * u; g.fillStyle = 'rgba(0,0,0,.6)'; g.fillRect(190 * u, ry - 8 * u, 580 * u, 17 * u); g.fillStyle = '#d7e3ff'; g.fillText(rows[ri], 196 * u, ry); } }
    if (!phone && ready && now - started < 22000) { f(15, true); g.textAlign = 'center'; var kw = g.measureText(T.keys).width + 36 * u; g.fillStyle = glass; box(g, W / 2 - kw / 2, H - 116 * u, kw, 32 * u, 10 * u); g.fill(); g.fillStyle = '#fff'; g.fillText(T.keys, W / 2, H - 99 * u); }
    if (warn) { f(24, true); g.textAlign = 'center'; var ww = g.measureText(warn).width + 44 * u; g.fillStyle = (now >= touchUntil && s.bump > 0) || s.stall || warn === T.nogear || warn.indexOf(T.belly) === 0 ? 'rgba(190,30,40,.88)' : glass; box(g, W / 2 - ww / 2, 194 * u, ww, 46 * u, 12 * u); g.fill(); g.fillStyle = '#fff'; g.fillText(warn, W / 2, 218 * u); }
  }

  /* ---- sound, made here from noise and a few tones: the engines, the wind, the stall horn, the wheels ---- */
  var AC = null, au = null;
  function audio() {
    if (AC) { if (AC.state === 'suspended') AC.resume(); return; }
    try { AC = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { AC = null; return; }
    var buf = AC.createBuffer(1, AC.sampleRate * 2, AC.sampleRate), d = buf.getChannelData(0); for (var i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    function noise() { var n = AC.createBufferSource(); n.buffer = buf; n.loop = true; n.start(); return n; }
    function chain(src, type, freq, q) { var f = AC.createBiquadFilter(); f.type = type; f.frequency.value = freq; if (q) f.Q.value = q; var g = AC.createGain(); g.gain.value = 0; src.connect(f); f.connect(g); g.connect(au.master); return { f: f, g: g }; }
    function tone(type, freq) { var o = AC.createOscillator(); o.type = type; o.frequency.value = freq; o.start(); return o; }
    au = { buf: buf, master: AC.createGain() }; au.master.gain.value = 0; au.master.connect(AC.destination);
    au.roar = chain(noise(), 'bandpass', 300, 0.6); au.rumble = chain(noise(), 'lowpass', 110); au.wind = chain(noise(), 'highpass', 1100);
    au.o1 = tone('sawtooth', 300); au.o2 = tone('sine', 450); au.whine = chain(au.o1, 'lowpass', 2600); au.o2.connect(au.whine.f);
    au.horn = chain(tone('square', 520), 'lowpass', 1800); au.motor = chain(tone('sawtooth', 84), 'lowpass', 520);
    if (AC.state === 'suspended') AC.resume();
  }
  function sound() {
    if (!au) { audio(); if (!au) return; }
    var t = AC.currentTime, k = 0.09, set = function (p, v) { p.setTargetAtTime(v, t, k); }, sp = F.clamp(s.v / F.VMAX, 0, 1.1);
    set(au.master.gain, sndOn && ready ? 0.85 : 0);
    set(au.roar.f.frequency, 170 + s.thr * 560); set(au.roar.g.gain, 0.05 + s.thr * 0.24); set(au.rumble.g.gain, 0.12 + s.thr * 0.3 + (s.onGround ? Math.min(0.3, s.v / 250) : 0));
    set(au.o1.frequency, 190 + s.thr * 1250); set(au.o2.frequency, 290 + s.thr * 1900); set(au.whine.g.gain, 0.003 + s.thr * 0.018);
    set(au.wind.g.gain, sp * sp * 0.28 + (s.gear * 0.05 + s.flapPos * 0.03) * sp);
    au.horn.g.gain.setTargetAtTime(s.stall && performance.now() % 440 < 220 ? 0.05 : 0, t, 0.01);
    set(au.motor.g.gain, s.gear > 0.02 && s.gear < 0.98 ? 0.07 : 0);
  }
  /** The wheels meeting the ground: a thump, and the tyres' chirp; or the belly scraping. */
  function thud(belly, power) {
    if (!au || !sndOn) return; var t = AC.currentTime;
    function burst(type, freq, q, gain, secs) { var n = AC.createBufferSource(); n.buffer = au.buf; var f = AC.createBiquadFilter(); f.type = type; f.frequency.value = freq; f.Q.value = q; var g = AC.createGain();
      g.gain.setValueAtTime(gain, t); g.gain.exponentialRampToValueAtTime(0.001, t + secs); n.connect(f); f.connect(g); g.connect(au.master); n.start(t, Math.random()); n.stop(t + secs + 0.05); }
    burst('lowpass', 170, 0.7, Math.min(1, 0.45 + power * 0.4), 0.5);
    if (belly) burst('bandpass', 900, 0.8, 0.5, 2.2); else burst('bandpass', 2300, 5, 0.16, 0.22);
  }

  function sizeHud() { var h = $('hud'); h.width = Math.round(innerWidth); h.height = Math.round(innerHeight); }
  sizeHud(); addEventListener('resize', sizeHud);
  window.FlySim = { F: F, D: D, T: T, conf: conf, Q: Q, AR: AR, SPOTS: SPOTS, RW: RW, onLine: onLine, say: say, tell: tell, tick: tick, L: L,
    /** An engine is ready to draw: the flight begins at the chosen place. */
    start: function (engine) { B = engine; started = performance.now(); ready = false; go(conf.spot == null ? 1 : conf.spot); },
    /** The engine has found out whether its land has real heights: without them the land is at sea level everywhere. */
    rebase: function () { if (B && B.flat && s) { s.alt -= SPOTS[spot].elev; groundH = 0; RW.elev = 0; } },
    drawn: function () { cpu = Math.round(cpu * 0.7 + (performance.now() - f0) * 0.3); },
    error: function (t) { lastErr = ('' + t).slice(0, 90); }, spot: function () { return spot; } };
  say(T.load);
})();
