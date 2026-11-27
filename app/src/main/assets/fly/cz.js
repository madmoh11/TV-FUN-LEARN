/* The full engine: CesiumJS, for a TV whose graphics chip can run it. It can show Google's photographed 3D city. */
(function () {
  'use strict';
  var S = window.FlySim, C = window.Cesium, F = S.F, D = S.D, T = S.T, conf = S.conf, Q = S.Q, RW = S.RW, onLine = S.onLine, L = S.L;
  var $ = function (id) { return document.getElementById(id); };
  function giveUp(why) { if (window.flyBoot && window.flyBoot.light) window.flyBoot.light(why); else { S.say(T.nogl); S.tell('err|nogl|' + why); } }
  if (!C) { giveUp('no library'); return; }
  /* The 3D library does its heavy work in background workers. A web view that cannot start them draws no land at all, so this is checked first:
     a worker made on the spot, and one of the library's own files. If only the file fails, the page is fetched again through the app's own web server. */
  var ua = navigator.userAgent.match(/Chrome\/(\d+)/), wk = { v: ua ? +ua[1] : 0, blob: '?', file: '?', via: location.protocol === 'http:' ? 'http' : 'app' };
  function tryWorker(url, key, done) { var w, t; try { w = new Worker(url, { type: 'module' }); } catch (e) { wk[key] = 'no'; done && done(); return; }
    w.onmessage = function () { wk[key] = 'ok'; clearTimeout(t); w.terminate(); done && done(); }; w.onerror = function () { wk[key] = 'fail'; clearTimeout(t); try { w.terminate(); } catch (e) {} done && done(); };
    t = setTimeout(function () { if (wk[key] === '?') wk[key] = key === 'file' ? 'ok?' : 'slow'; try { w.terminate(); } catch (e) {} done && done(); }, 5000); }
  try { tryWorker(URL.createObjectURL(new Blob(['postMessage(1)'], { type: 'text/javascript' })), 'blob'); } catch (e) { wk.blob = 'no'; }
  tryWorker(C.buildModuleUrl('Workers/createVerticesFromHeightmap.js'), 'file', function () {
    if (wk.file === 'fail' && wk.via === 'app') { var alt = ''; try { alt = L.alt ? L.alt() : ''; } catch (e) {} if (alt) location.replace(alt); } });
  function make(old) {
    return new C.Viewer('c', { animation: false, baseLayerPicker: false, fullscreenButton: false, geocoder: false, homeButton: false, infoBox: false, sceneModePicker: false, selectionIndicator: false,
      timeline: false, navigationHelpButton: false, navigationInstructionsInitiallyVisible: false, scene3DOnly: true, shadows: false, baseLayer: false, creditContainer: 'credits', showRenderLoopErrors: false, orderIndependentTranslucency: false,
      msaaSamples: 1, requestRenderMode: false, contextOptions: old ? { requestWebgl1: true, webgl: { antialias: false, alpha: false, failIfMajorPerformanceCaveat: false } } : { webgl: { antialias: false, powerPreference: 'high-performance', alpha: false } } });
  }
  var viewer, why = '';
  try { viewer = make(false); } catch (e) { why = '' + (e && e.message || e);
    $('c').innerHTML = '';
    try { viewer = make(true); } catch (e2) { why += ' / ' + (e2 && e2.message || e2); } }          // an older graphics chip: ask for the older kind of 3D
  if (!viewer) { $('c').innerHTML = ''; giveUp(why.slice(0, 120)); return; }
  var scene = viewer.scene, cam = viewer.camera, globe = scene.globe;
  var gl = scene.context._gl, glInfo = '', fine = true, slowAt = 0;
  try { var hp = gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_FLOAT); fine = !!hp && hp.precision > 0; var di = gl.getExtension('WEBGL_debug_renderer_info');
    glInfo = (scene.context.webgl2 ? 'GL2' : 'GL1') + ' ' + ('' + (di ? gl.getParameter(di.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER))).slice(0, 44) + (fine ? '' : ' no-highp') + (scene.context.depthTexture ? '' : ' no-depthtex') + ' tex' + gl.getParameter(gl.MAX_TEXTURE_SIZE); } catch (e) {}
  var compat = !scene.context.webgl2 || !fine;
  if (compat && conf.eng !== 2) { try { viewer.destroy(); } catch (e) {} $('c').innerHTML = ''; giveUp('old chip: ' + glInfo); return; }       // this chip cannot run the library at a usable speed
  resize(); addEventListener('resize', resize);
  window.flyViewer = viewer;
  // the TV's graphics chip is small: no shadows, no extra smoothing, and a picture sized to the chosen quality
  viewer.shadows = false; scene.postProcessStages.fxaa.enabled = false; scene.highDynamicRange = false;
  function resize() { var h = $('c').clientHeight || 540; viewer.resolutionScale = F.clamp((compat ? 400 : [432, 540, 720][Q]) / h, 0.3, 2); }
  scene.screenSpaceCameraController.enableInputs = false;
  // a clear afternoon over Amman, with the sun lighting the land and haze melting the far hills into the sky
  viewer.clock.currentTime = C.JulianDate.fromIso8601('2025-05-15T12:20:00Z'); viewer.clock.shouldAnimate = false;
  globe.enableLighting = true; globe.dynamicAtmosphereLighting = true; globe.showGroundAtmosphere = true; globe.baseColor = C.Color.fromCssColorString('#6b6a5c');
  globe.maximumScreenSpaceError = [4, 3, 2][Q]; globe.tileCacheSize = 80;
  scene.fog.enabled = true; scene.fog.density = 0.00028; scene.fog.minimumBrightness = 0.3;
  if (scene.skyAtmosphere) scene.skyAtmosphere.show = true;
  if (scene.sun) scene.sun.show = true; if (scene.moon) scene.moon.show = false; if (scene.skyBox) scene.skyBox.show = true;
  if (compat) { scene.logarithmicDepthBuffer = false; globe.enableLighting = false; globe.dynamicAtmosphereLighting = false; globe.showGroundAtmosphere = false; scene.fog.enabled = false;
    if (scene.skyAtmosphere) scene.skyAtmosphere.show = false; if (scene.sun) scene.sun.show = false; if (scene.skyBox) scene.skyBox.show = false;
    scene.backgroundColor = C.Color.fromCssColorString('#8fc2ee'); globe.maximumScreenSpaceError = 6; cam.frustum.near = 2; }
  C.RequestScheduler.requestsByServer['tile.googleapis.com:443'] = 18;

  /* ---- the world: Google's photographed 3D city if there is a key, then Cesium ion's copy of it, then satellite pictures on real terrain, then a map that ships with the app ---- */
  var tileset = null, mode = '', note = '', flat = false, keyErr = '', stage = 'start';
  function within(p, ms) { return Promise.race([p, new Promise(function (_, no) { setTimeout(function () { no(new Error('timeout')); }, ms); })]); }
  var tileOpts = { showCreditsOnScreen: true, maximumScreenSpaceError: [32, 24, 16][Q], cacheBytes: 110e6, maximumCacheOverflowBytes: 60e6, dynamicScreenSpaceError: true, preloadFlightDestinations: false, skipLevelOfDetail: false };
  if (compat) { tileOpts.maximumScreenSpaceError = 48; tileOpts.cacheBytes = 40e6; tileOpts.maximumCacheOverflowBytes = 20e6; }
  function useTiles(ts, name) { tileset = scene.primitives.add(ts); globe.show = false; mode = name; }
  async function ground() {
    // the pictured ground: a map that ships with the app underneath, satellite pictures over it when there is internet
    try { viewer.imageryLayers.addImageryProvider(await C.TileMapServiceImageryProvider.fromUrl(C.buildModuleUrl('Assets/Textures/NaturalEarthII'))); } catch (e) {}
    mode = 'basic'; stage = 'map';
    try { var img = await within(C.ArcGisMapServerImageryProvider.fromUrl('https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer', { enablePickFeatures: false }), 15000);
      viewer.imageryLayers.addImageryProvider(img); mode = 'sat'; } catch (e) { keyErr += ' sat ' + (e && (e.statusCode || e.message) || e); }
    stage = 'terrain';
    flat = true;
    try { if (conf.ion) { viewer.terrainProvider = await within(C.createWorldTerrainAsync(), 15000); flat = false; } } catch (e) {}
    if (flat && mode === 'sat') try { viewer.terrainProvider = await within(C.ArcGISTiledElevationTerrainProvider.fromUrl('https://elevation3d.arcgis.com/arcgis/rest/services/WorldElevation3D/Terrain3D/ImageServer'), 15000); flat = false; } catch (e) {}
    globe.depthTestAgainstTerrain = !compat; stage = 'done';
  }
  async function world() {
    if (conf.ion) C.Ion.defaultAccessToken = conf.ion;
    if (conf.g) try { useTiles(await within(C.Cesium3DTileset.fromUrl('https://tile.googleapis.com/v1/3dtiles/root.json?key=' + encodeURIComponent(conf.g), tileOpts), 20000), 'google'); return; } catch (e) { note = T.badkey; keyErr = 'google ' + (e && (e.statusCode || e.message) || e); }
    if (conf.ion) try { useTiles(await within(C.Cesium3DTileset.fromIonAssetId(2275207, tileOpts), 20000), 'google'); note = ''; return; } catch (e) { note = T.badkey; keyErr += ' ion ' + (e && (e.statusCode || e.message) || e); }
    if (!conf.g && !conf.ion) note = T.nokey;
    await ground();
  }

  /* ---- the plane ---- */
  var FF = C.Transforms.localFrameToFixedFrameGenerator('north', 'west');      // heading 0 is north and grows clockwise, like a compass
  var plane = null, hpr = new C.HeadingPitchRoll(), pos = new C.Cartesian3(), carto = new C.Cartographic(), mm = new C.Matrix4(), enu = new C.Matrix4();
  var guides = [], gearNode = null, gearM = new C.Matrix4();
  var B = { name: 'full', flat: false, mode: '', note: '', slowHud: compat,
    ready: function () { return stage !== 'start' && (tileset ? tileset.tilesLoaded : globe.tilesLoaded); },
    ground: function (lat, lon) { carto.longitude = lon; carto.latitude = lat; carto.height = 0;
      if (tileset) return scene.sampleHeightSupported && plane ? scene.sampleHeight(carto, [plane]) : undefined; return globe.getHeight(carto); },
    info: function () { var tl = 0; try { tl = globe._surface._tilesToRender.length; } catch (e) {}
      return 'Chrome' + wk.v + ' wk:' + wk.blob + '/' + wk.file + ' ' + wk.via + ' P' + (plane ? (plane.ready ? 2 : 1) : 0) + ' ' + glInfo + ' buf' + gl.drawingBufferWidth + 'x' + gl.drawingBufferHeight + ' ' + stage + ' ' + mode + ' L' + viewer.imageryLayers.length + ' T' + tl + (compat ? ' compat' : '') + (keyErr ? ' [' + keyErr.slice(0, 70) + ']' : ''); } };
  function frame() {
    B.mode = mode; B.note = note; B.flat = flat;
    var v = S.tick(); if (!v) return; var s = v.s;
    for (var gi = 0; gi < guides.length; gi++) guides[gi].show = v.guides;          // the approach lines are for the air
    C.Cartesian3.fromRadians(s.lon, s.lat, s.alt, C.Ellipsoid.WGS84, pos);
    hpr.heading = s.hdg; hpr.pitch = s.show; hpr.roll = s.roll;
    if (plane) { C.Transforms.headingPitchRollToFixedFrame(pos, hpr, C.Ellipsoid.WGS84, FF, mm); plane.modelMatrix = mm;
      if (!gearNode && plane.ready) try { gearNode = plane.getNode('gear'); } catch (e) {}
      if (gearNode) { gearNode.show = s.gear > 0.03; gearNode.matrix = C.Matrix4.fromTranslation(new C.Cartesian3(0, (1 - s.gear) * 2.3, 0), gearM); } }      // the wheels fold up into the body
    C.Transforms.eastNorthUpToFixedFrame(pos, C.Ellipsoid.WGS84, enu);
    cam.lookAtTransform(enu, new C.HeadingPitchRange(v.camH, -v.camE, v.range));
    cam.twistRight(v.camR);
    cam.frustum.fov = v.fov;
    // a TV that cannot keep up with the photographed city is taken back to the pictured ground by itself
    if (tileset && v.now - v.started > 25000 && v.fps >= 0 && v.fps < 4) { if (!slowAt) slowAt = v.now; else if (v.now - slowAt > 10000) { try { scene.primitives.remove(tileset); } catch (e) {} tileset = null; globe.show = true; note = T.weak; slowAt = 0;
      ground().then(function () { B.flat = flat; if (flat) RW.elev = 0; }); } } else slowAt = 0;
  }
  S.start(B);
  scene.preUpdate.addEventListener(frame);
  scene.postRender.addEventListener(S.drawn);
  scene.renderError.addEventListener(function (sc, e) { var m = 'render: ' + ('' + (e && e.message || e)).slice(0, 80); S.error(m); giveUp(m); });
  C.Model.fromGltfAsync({ url: 'plane.glb', scale: 1, minimumPixelSize: 0, incrementallyLoadTextures: false }).then(function (m) { plane = scene.primitives.add(m); }).catch(function () {});
  world().then(function () {
    B.flat = flat; S.rebase();
    // a green line down each approach, three degrees steep, ending where the wheels should touch
    try { var base = flat ? 0 : 719; [[300, -1], [3360, 1]].forEach(function (e) { var a = onLine(e[0]), b = onLine(e[0] + e[1] * 10000);
      guides.push(viewer.entities.add({ polyline: { positions: C.Cartesian3.fromDegreesArrayHeights([a[1], a[0], base + 3, b[1], b[0], base + 3 + 10000 * Math.tan(3 * D)]), width: 4, arcType: C.ArcType.NONE, material: C.Color.fromCssColorString('#39ff6a').withAlpha(0.55) } })); }); } catch (e) {}
  });
})();
