/* The TV side of the Amman flight simulator: the 3D world (CesiumJS), the plane, the chase camera and the instruments.
   The phone only sends the stick and the throttle; everything that flies happens here. */
(function () {
  'use strict';
  var C = window.Cesium, F = window.FlyCore, D = Math.PI / 180;
  var keys = {}, kthr = null;
  /* Outside the TV app (a desktop browser, a test) there is no bridge: the keyboard flies and nothing is reported. */
  var L = window.Lamma || { conf: function () { var q = new URLSearchParams(location.search); return JSON.stringify({ g: q.get('g') || '', ion: q.get('ion') || '', lang: q.get('lang') || 'ar', q: +(q.get('q') || 1), spot: +(q.get('spot') || 1) }); },
    poll: function () { return '0,0,-1,0,0,0,0,1,0'; }, tell: function () {} };
  var conf = {}; try { conf = JSON.parse(L.conf()); } catch (e) {}
  var AR = conf.lang !== 'en', Q = F.clamp(conf.q == null ? 1 : conf.q | 0, 0, 2);
  var T = AR ? { kmh: 'كم/س', m: 'م', load: 'جاري تحميل عمّان…', stall: 'السرعة قليلة! زِد قوة المحرك', hard: 'هبوط قاسي!', ground: 'على الأرض', spots: 'إلى أين نطير؟', thr: 'المحرك',
      nogl: 'هذا التلفزيون لا يدعم الرسم ثلاثي الأبعاد، فالمحاكي لا يعمل عليه.', google: 'عمّان ثلاثية الأبعاد', sat: 'صور الأقمار', basic: 'خريطة بسيطة (لا يوجد إنترنت)', nokey: 'بدون مفتاح خرائط: أرض مصوّرة بلا مبانٍ', badkey: 'مفتاح الخرائط لم يعمل: أرض مصوّرة بلا مبانٍ', N: 'ش', E: 'ق', S: 'ج', W: 'غ', auto: 'توازن تلقائي' }
    : { kmh: 'km/h', m: 'm', load: 'Loading Amman…', stall: 'Too slow! Add power', hard: 'Hard landing!', ground: 'On the ground', spots: 'Where to?', thr: 'Engine',
      nogl: 'This TV cannot draw 3D, so the simulator does not run on it.', google: 'Amman in 3D', sat: 'Satellite pictures', basic: 'Simple map (no internet)', nokey: 'No map key: pictured ground without buildings', badkey: 'The map key did not work: pictured ground without buildings', N: 'N', E: 'E', S: 'S', W: 'W', auto: 'Auto-level' };
  /* The places of the quick-jump list. elev is the height of the land there above the sea. */
  var SPOTS = [
    { ar: 'مطار الملكة علياء الدولي', en: 'Queen Alia International Airport', lat: 31.7225, lon: 35.9932, elev: 730, hdg: 80, land: true },
    { ar: 'أبراج العبدلي والبوليفارد', en: 'Abdali towers and the Boulevard', lat: 31.9634, lon: 35.9065, elev: 940, hdg: 35 },
    { ar: 'جسر عبدون', en: 'Abdoun Bridge', lat: 31.9482, lon: 35.8825, elev: 880, hdg: 60 },
    { ar: 'جبل القلعة ووسط البلد', en: 'The Citadel and downtown', lat: 31.9544, lon: 35.9349, elev: 850, hdg: 100 },
    { ar: 'البحر الميت', en: 'The Dead Sea', lat: 31.5590, lon: 35.4732, elev: -430, hdg: 250 }];
  var $ = function (id) { return document.getElementById(id); };
  function say(t) { $('msg').textContent = t; $('msg').hidden = !t; }
  function tell(extra) { try { L.tell(extra); } catch (e) {} }

  if (!C || !F) { say(T.nogl); tell('err|nolib'); return; }
  var viewer;
  try {
    viewer = new C.Viewer('c', { animation: false, baseLayerPicker: false, fullscreenButton: false, geocoder: false, homeButton: false, infoBox: false, sceneModePicker: false, selectionIndicator: false,
      timeline: false, navigationHelpButton: false, navigationInstructionsInitiallyVisible: false, scene3DOnly: true, shadows: false, baseLayer: false, creditContainer: 'credits',
      msaaSamples: 1, requestRenderMode: false, contextOptions: { webgl: { antialias: false, powerPreference: 'high-performance', alpha: false } } });
  } catch (e) { say(T.nogl); tell('err|nogl'); return; }
  var scene = viewer.scene, cam = viewer.camera, globe = scene.globe;
  window.flyViewer = viewer;
  // the TV's graphics chip is small: no shadows, no extra smoothing, and a picture sized to the chosen quality
  viewer.shadows = false; scene.postProcessStages.fxaa.enabled = false; scene.highDynamicRange = false;
  function resize() { var h = $('c').clientHeight || 540; viewer.resolutionScale = F.clamp([432, 540, 720][Q] / h, 0.4, 2); var hud = $('hud'); hud.width = Math.round(innerWidth); hud.height = Math.round(innerHeight); }
  resize(); addEventListener('resize', resize);
  scene.screenSpaceCameraController.enableInputs = false;
  // a clear afternoon over Amman, with the sun lighting the land and haze melting the far hills into the sky
  viewer.clock.currentTime = C.JulianDate.fromIso8601('2025-05-15T12:20:00Z'); viewer.clock.shouldAnimate = false;
  globe.enableLighting = true; globe.dynamicAtmosphereLighting = true; globe.showGroundAtmosphere = true; globe.baseColor = C.Color.fromCssColorString('#6b6a5c');
  globe.maximumScreenSpaceError = [4, 3, 2][Q]; globe.tileCacheSize = 80;
  scene.fog.enabled = true; scene.fog.density = 0.00028; scene.fog.minimumBrightness = 0.3;
  if (scene.skyAtmosphere) scene.skyAtmosphere.show = true;
  if (scene.sun) scene.sun.show = true; if (scene.moon) scene.moon.show = false; if (scene.skyBox) scene.skyBox.show = true;
  C.RequestScheduler.requestsByServer['tile.googleapis.com:443'] = 18;

  /* ---- the world: Google's photographed 3D city if there is a key, then Cesium ion's copy of it, then satellite pictures on real terrain, then a map that ships with the app ---- */
  var tileset = null, mode = '', note = '', flat = false;
  function within(p, ms) { return Promise.race([p, new Promise(function (_, no) { setTimeout(function () { no(new Error('timeout')); }, ms); })]); }
  var tileOpts = { showCreditsOnScreen: true, maximumScreenSpaceError: [32, 24, 16][Q], cacheBytes: 110e6, maximumCacheOverflowBytes: 60e6, dynamicScreenSpaceError: true, preloadFlightDestinations: false, skipLevelOfDetail: false };
  function useTiles(ts, name) { tileset = scene.primitives.add(ts); globe.show = false; mode = name; }
  async function ground() {
    // the pictured ground: a map that ships with the app underneath, satellite pictures over it when there is internet
    try { viewer.imageryLayers.addImageryProvider(await C.TileMapServiceImageryProvider.fromUrl(C.buildModuleUrl('Assets/Textures/NaturalEarthII'))); } catch (e) {}
    mode = 'basic';
    try { var img = await within(C.ArcGisMapServerImageryProvider.fromUrl('https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer', { enablePickFeatures: false }), 15000);
      viewer.imageryLayers.addImageryProvider(img); mode = 'sat'; } catch (e) {}
    flat = true;
    try { if (conf.ion) { viewer.terrainProvider = await within(C.createWorldTerrainAsync(), 15000); flat = false; } } catch (e) {}
    if (flat && mode === 'sat') try { viewer.terrainProvider = await within(C.ArcGISTiledElevationTerrainProvider.fromUrl('https://elevation3d.arcgis.com/arcgis/rest/services/WorldElevation3D/Terrain3D/ImageServer'), 15000); flat = false; } catch (e) {}
    globe.depthTestAgainstTerrain = true;
  }
  async function world() {
    if (conf.ion) C.Ion.defaultAccessToken = conf.ion;
    if (conf.g) try { useTiles(await within(C.Cesium3DTileset.fromUrl('https://tile.googleapis.com/v1/3dtiles/root.json?key=' + encodeURIComponent(conf.g), tileOpts), 20000), 'google'); return; } catch (e) { note = T.badkey; }
    if (conf.ion) try { useTiles(await within(C.Cesium3DTileset.fromIonAssetId(2275207, tileOpts), 20000), 'google'); note = ''; return; } catch (e) { note = T.badkey; }
    if (!conf.g && !conf.ion) note = T.nokey;
    await ground();
  }

  /* ---- the plane ---- */
  var FF = C.Transforms.localFrameToFixedFrameGenerator('north', 'west');      // heading 0 is north and grows clockwise, like a compass
  var plane = null, hpr = new C.HeadingPitchRoll(), pos = new C.Cartesian3(), carto = new C.Cartographic(), mm = new C.Matrix4(), enu = new C.Matrix4();
  var s = null, spot = 1, groundH = null, groundAt = 0, camH = 0, camP = 0, camR = 0, camRange = 110, menu = false, menuAt = 1, lvlSeq = '0', goSeq = '0', ready = false, last = 0, hudAt = 0, tellAt = 0, started = performance.now();
  function go(i) {
    spot = F.clamp(i | 0, 0, SPOTS.length - 1); var p = SPOTS[spot], base = flat ? 0 : p.elev;
    if (p.land) { s = F.start(p.lat, p.lon, base + F.GEAR, p.hdg, 0); s.onGround = true; }
    else { // arrive from a little way off, already flying toward the landmark
      var back = 2600, h = p.hdg * D; s = F.start(p.lat - back * Math.cos(h) / 6371000 / D, p.lon - back * Math.sin(h) / (6371000 * Math.cos(p.lat * D)) / D, base + 420, p.hdg, 135); }
    groundH = base; groundAt = 0; camH = s.hdg; camP = 0; camR = 0; kthr = null;
  }
  function sampleGround(now) {
    if (now - groundAt < (s.alt - (groundH || 0) > 700 ? 1500 : 350)) return; groundAt = now;
    carto.longitude = s.lon; carto.latitude = s.lat; carto.height = 0; var h;
    try { if (tileset) { if (scene.sampleHeightSupported && plane) h = scene.sampleHeight(carto, [plane]); } else h = globe.getHeight(carto); } catch (e) {}
    if (h !== undefined && isFinite(h) && h > -600 && h < 3000) groundH = h;
  }

  /* ---- what the pilot asks for: the phone first, the TV remote or a keyboard when no phone is flying ---- */
  addEventListener('keydown', function (e) { keys[e.key] = 1; if (menu) { if (e.key === 'ArrowDown') menuAt = (menuAt + 1) % SPOTS.length; else if (e.key === 'ArrowUp') menuAt = (menuAt + SPOTS.length - 1) % SPOTS.length; else if (e.key === 'Enter') { go(menuAt); menu = false; } showMenu(); }
    else if (e.key === 'Enter') { menu = true; menuAt = spot; showMenu(); } });
  addEventListener('keyup', function (e) { delete keys[e.key]; });
  function showMenu() { var el = $('menu'); el.hidden = !menu; if (!menu) return; el.dir = AR ? 'rtl' : 'ltr';
    el.innerHTML = '<h2>' + T.spots + '</h2>' + SPOTS.map(function (p, i) { return '<div class="' + (i === menuAt ? 'on' : '') + '"><b>' + (i + 1) + '</b>' + (AR ? p.ar : p.en) + '</div>'; }).join(''); }
  function input(dt) {
    var p = ('' + L.poll()).split(','), inp = { x: 0, y: 0, thr: null, level: false };
    if (p[8] === '1') { inp.x = p[0] / 100; inp.y = p[1] / 100; }
    else if (!menu) { inp.x = (keys.ArrowRight ? 1 : 0) - (keys.ArrowLeft ? 1 : 0); inp.y = (keys.ArrowUp ? 1 : 0) - (keys.ArrowDown ? 1 : 0); }
    if (+p[2] >= 0) inp.thr = p[2] / 100;
    if (keys.MediaFastForward || keys.PageUp || keys.w) kthr = Math.min(1, (kthr == null ? s.thr : kthr) + dt * 0.5);
    if (keys.MediaRewind || keys.PageDown || keys.s) kthr = Math.max(0, (kthr == null ? s.thr : kthr) - dt * 0.5);
    if (inp.thr == null && kthr != null) inp.thr = kthr;
    if (p[3] !== lvlSeq) { lvlSeq = p[3]; inp.level = true; }
    if (p[4] !== goSeq) { goSeq = p[4]; go(+p[5]); menu = false; showMenu(); }
    var m = p[6] === '1'; if (p[8] === '1' && m !== menu) { menu = m; menuAt = spot; showMenu(); }
    return inp;
  }

  /* ---- one frame: fly, place the plane, swing the camera after it ---- */
  function frame() {
    var now = performance.now(), dt = Math.min(0.1, (now - (last || now)) / 1000); last = now;
    if (!s) return;
    var inp = input(dt);
    if (ready) { sampleGround(now); F.step(s, inp, dt, groundH); }
    C.Cartesian3.fromRadians(s.lon, s.lat, s.alt, C.Ellipsoid.WGS84, pos);
    hpr.heading = s.hdg; hpr.pitch = s.show; hpr.roll = s.roll;
    if (plane) { C.Transforms.headingPitchRollToFixedFrame(pos, hpr, C.Ellipsoid.WGS84, FF, mm); plane.modelMatrix = mm; }
    // the camera hangs behind and above on a spring: it is late into every turn and falls back as the plane speeds up
    camH += F.turn(camH, s.hdg) * Math.min(1, dt * 2.3); camP += (s.show - camP) * Math.min(1, dt * 1.6); camR += (s.roll - camR) * Math.min(1, dt * 1.4);
    camRange += (92 + s.v * 0.16 - camRange) * Math.min(1, dt * 1.2);
    C.Transforms.eastNorthUpToFixedFrame(pos, C.Ellipsoid.WGS84, enu);
    cam.lookAtTransform(enu, new C.HeadingPitchRange(camH, -(11 * D) + camP * 0.35, camRange));
    cam.twistRight(camR * 0.22);
    cam.frustum.fov = (58 + F.clamp(s.v / F.VMAX, 0, 1) * 10) * D;
    if (now - hudAt > 80) { hudAt = now; hud(); }
    if (now - tellAt > 500) { tellAt = now; tell('on|' + Math.round(s.v * 3.6) + '|' + Math.round(s.alt) + '|' + Math.round(s.hdg / D) + '|' + mode + '|' + spot + '|' + Math.round(s.thr * 100) + '|' + (s.onGround ? 1 : 0) + '|' + (ready ? 1 : 0)); }
  }

  /* ---- the instruments, drawn over the picture ---- */
  function box(g, x, y, w, h, r) { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath(); }
  function hud() {
    var cv = $('hud'), g = cv.getContext('2d'), W = cv.width, H = cv.height, u = H / 540, f = function (px, bold) { g.font = (bold ? 'bold ' : '') + Math.round(px * u) + 'px L, sans-serif'; };
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
    // where we are, what the picture is made of, and anything the pilot must know now
    var p = SPOTS[spot]; g.textAlign = AR ? 'right' : 'left'; var lx = AR ? W - 26 * u : 26 * u; g.fillStyle = glass; var nm = AR ? p.ar : p.en; f(19, true); var nw = g.measureText(nm).width + 28 * u;
    box(g, AR ? lx - nw : lx, 16 * u, nw, 38 * u, 10 * u); g.fill(); g.fillStyle = '#fff'; g.fillText(nm, AR ? lx - 14 * u : lx + 14 * u, 36 * u);
    f(13, false); g.fillStyle = 'rgba(255,255,255,.85)'; g.fillText(note || (mode === 'google' ? T.google : mode === 'sat' ? T.sat : mode === 'basic' ? T.basic : ''), AR ? lx - 6 * u : lx + 6 * u, 68 * u);
    var warn = s.bump > 0 ? T.hard : s.stall ? T.stall : s.level > 0 ? T.auto : !ready ? T.load : (s.onGround && s.v < 3 ? T.ground : '');
    if (warn) { f(24, true); g.textAlign = 'center'; var ww = g.measureText(warn).width + 44 * u; g.fillStyle = s.bump > 0 || s.stall ? 'rgba(190,30,40,.88)' : glass; box(g, W / 2 - ww / 2, H * 0.2, ww, 46 * u, 12 * u); g.fill(); g.fillStyle = '#fff'; g.fillText(warn, W / 2, H * 0.2 + 24 * u); }
  }

  /* ---- start ---- */
  say(T.load);
  go(conf.spot == null ? 1 : conf.spot);
  scene.preUpdate.addEventListener(frame);
  scene.renderError.addEventListener(function () { say(T.nogl); tell('err|render'); });
  C.Model.fromGltfAsync({ url: 'plane.glb', scale: 1, minimumPixelSize: 0, incrementallyLoadTextures: false }).then(function (m) { plane = scene.primitives.add(m); }).catch(function () {});
  world().then(function () {
    var p = SPOTS[spot]; if (flat && s) { s.alt += -p.elev; groundH = 0; }        // no terrain: the land is at sea level everywhere
    // the picture is shown as soon as the land near the plane has arrived, or after a while whatever happens
    var t0 = performance.now(), iv = setInterval(function () { var ok = tileset ? tileset.tilesLoaded : globe.tilesLoaded;
      if (ok || performance.now() - t0 > 12000) { clearInterval(iv); ready = true; say(''); } else if (performance.now() - t0 > 2500) say(''); }, 250);
  });
})();
