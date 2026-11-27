/* The light engine: a small WebGL 1 renderer written for old TV graphics chips (OpenGL ES 2.0, no fine arithmetic in pixel programs).
   It draws satellite pictures draped over real terrain heights, the airliner, the sky and the approach lines, and nothing else.
   The land is kept as square map tiles in four or five sizes around the plane: big coarse ones far away, small sharp ones near. */
(function () {
  'use strict';
  var S = window.FlySim, F = S.F, D = S.D, T = S.T, Q = S.Q, RW = S.RW, onLine = S.onLine;
  var $ = function (id) { return document.getElementById(id); };
  var R = 6371000, LAT0 = 31.9 * D, KX = R * Math.cos(LAT0);          // the flat map all positions are measured on: metres east = lon * KX, metres north = lat * R
  var IMG = 'https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/', HGT = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/';
  var N = 16, HZ = 12;                                               // a tile is a 16 by 16 grid of squares; heights come from zoom 12 or coarser

  var cv = document.createElement('canvas'); cv.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%'; $('c').innerHTML = ''; $('c').appendChild(cv);
  var gl = null;
  try { gl = cv.getContext('webgl', { antialias: false, alpha: false, depth: true, stencil: false, powerPreference: 'high-performance', preserveDrawingBuffer: false }) || cv.getContext('experimental-webgl'); } catch (e) {}
  if (!gl) { S.say(T.nogl); S.tell('err|nogl|light: no webgl'); return; }
  var glName = ''; try { var di = gl.getExtension('WEBGL_debug_renderer_info'); glName = ('' + (di ? gl.getParameter(di.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER))).slice(0, 30); } catch (e) {}
  function resize() { var h = Q === 3 ? Math.round(innerHeight * Math.min(2, window.devicePixelRatio || 1)) : [360, 540, 720][Q], w = Math.round(h * innerWidth / Math.max(1, innerHeight) / 2) * 2; if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h; } }
  resize(); addEventListener('resize', resize);
  $('credits').textContent = 'Imagery: Esri, Maxar, Earthstar Geographics  ·  Terrain: Mapzen, AWS Terrain Tiles';

  /* ---- shaders: short, and written so that medium precision in the pixel program is enough ---- */
  function program(vs, fs) {
    function sh(type, src) { var s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error('shader: ' + gl.getShaderInfoLog(s)); return s; }
    var p = gl.createProgram(); gl.attachShader(p, sh(gl.VERTEX_SHADER, vs)); gl.attachShader(p, sh(gl.FRAGMENT_SHADER, 'precision mediump float;\n' + fs)); gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error('link: ' + gl.getProgramInfoLog(p));
    var o = { p: p }, i, n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS); for (i = 0; i < n; i++) { var u = gl.getActiveUniform(p, i); o[u.name] = gl.getUniformLocation(p, u.name); }
    n = gl.getProgramParameter(p, gl.ACTIVE_ATTRIBUTES); for (i = 0; i < n; i++) { var a = gl.getActiveAttrib(p, i); o[a.name] = gl.getAttribLocation(p, a.name); }
    return o;
  }
  var HAZE = [0.78, 0.86, 0.94], FOG = 'float d = length(p); vFog = 1.0 - exp(-d * uFog);';
  var pLand, pFlat, pSky, pPlane;
  try {
    pLand = program('attribute vec3 aPos; attribute vec2 aUv; uniform mat4 uVP; uniform vec3 uOff; uniform float uFog; varying vec2 vUv; varying float vFog;\nvoid main(){ vec3 p = aPos + uOff; ' + FOG + ' vUv = aUv; gl_Position = uVP * vec4(p, 1.0); }',
      'uniform sampler2D uTex; uniform vec3 uHaze; varying vec2 vUv; varying float vFog;\nvoid main(){ gl_FragColor = vec4(mix(texture2D(uTex, vUv).rgb, uHaze, vFog), 1.0); }');
    pFlat = program('attribute vec3 aPos; uniform mat4 uVP; uniform vec3 uOff; uniform float uFog; varying float vFog;\nvoid main(){ vec3 p = aPos + uOff; ' + FOG + ' gl_Position = uVP * vec4(p, 1.0); }',
      'uniform vec4 uCol; uniform vec3 uHaze; varying float vFog;\nvoid main(){ gl_FragColor = vec4(mix(uCol.rgb, uHaze, vFog), uCol.a); }');
    pSky = program('attribute vec3 aPos; attribute vec3 aCol; uniform mat4 uVP; varying vec3 vCol;\nvoid main(){ vCol = aCol; gl_Position = uVP * vec4(aPos, 1.0); }', 'varying vec3 vCol;\nvoid main(){ gl_FragColor = vec4(vCol, 1.0); }');
    pPlane = program('attribute vec3 aPos; attribute vec3 aNor; uniform mat4 uVP; uniform mat4 uM; varying float vL;\nvoid main(){ vec3 n = normalize((uM * vec4(aNor, 0.0)).xyz); vL = 0.52 + 0.48 * max(dot(n, vec3(0.35, -0.45, 0.82)), 0.0) + 0.1 * max(-n.z, 0.0); gl_Position = uVP * (uM * vec4(aPos, 1.0)); }',
      'uniform vec3 uCol; varying float vL;\nvoid main(){ gl_FragColor = vec4(uCol * vL, 1.0); }');
  } catch (e) { S.say(T.nogl + ' ' + e.message); S.tell('err|render|light ' + ('' + e.message).slice(0, 150)); return; }

  /* ---- small matrix helpers (column-major, as WebGL wants) ---- */
  function perspective(fovy, aspect, near, far) { var f = 1 / Math.tan(fovy / 2), o = new Float32Array(16); o[0] = f / aspect; o[5] = f; o[10] = (far + near) / (near - far); o[11] = -1; o[14] = 2 * far * near / (near - far); return o; }
  function mul(a, b) { var o = new Float32Array(16); for (var c = 0; c < 4; c++) for (var r = 0; r < 4; r++) o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3]; return o; }
  /** A view from eye toward the origin; x, y, z are the camera's right, up and backward directions. */
  function viewFrom(eye, right, up, back, withEye) { var o = new Float32Array(16);
    o[0] = right[0]; o[4] = right[1]; o[8] = right[2]; o[1] = up[0]; o[5] = up[1]; o[9] = up[2]; o[2] = back[0]; o[6] = back[1]; o[10] = back[2]; o[15] = 1;
    if (withEye) { o[12] = -(right[0] * eye[0] + right[1] * eye[1] + right[2] * eye[2]); o[13] = -(up[0] * eye[0] + up[1] * eye[1] + up[2] * eye[2]); o[14] = -(back[0] * eye[0] + back[1] * eye[1] + back[2] * eye[2]); } return o; }
  function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
  function unit(a) { var l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; }

  /* ---- map tiles ---- */
  function tileX(lon, z) { return (lon / D + 180) / 360 * Math.pow(2, z); }
  function tileY(lat, z) { return (1 - Math.log(Math.tan(lat) + 1 / Math.cos(lat)) / Math.PI) / 2 * Math.pow(2, z); }
  function lonOf(x, z) { return (x / Math.pow(2, z) * 360 - 180) * D; }
  function latOf(y, z) { return Math.atan(Math.sinh(Math.PI * (1 - 2 * y / Math.pow(2, z)))); }

  var heights = {}, hQueue = [], hBusy = 0, hCanvas = document.createElement('canvas'), hCtx; hCanvas.width = hCanvas.height = 256; hCtx = hCanvas.getContext('2d', { willReadFrequently: true });
  /** The height grid of one terrain tile: asked for once, null until it arrives, false if it cannot be had. */
  function heightTile(z, x, y) { var k = z + '/' + x + '/' + y, h = heights[k]; if (h !== undefined) return h; heights[k] = null; hQueue.push([z, x, y, k]); return null; }
  function pumpHeights() { while (hBusy < 3 && hQueue.length) (function (q) { hBusy++; var im = new Image(); im.crossOrigin = 'anonymous';
    im.onload = function () { hBusy--; try { hCtx.drawImage(im, 0, 0); var d = hCtx.getImageData(0, 0, 256, 256).data, a = new Float32Array(65536); for (var i = 0; i < 65536; i++) a[i] = d[i * 4] * 256 + d[i * 4 + 1] + d[i * 4 + 2] / 256 - 32768; heights[q[3]] = a; } catch (e) { heights[q[3]] = false; } };
    im.onerror = function () { hBusy--; heights[q[3]] = false; hErr++; }; im.src = HGT + q[0] + '/' + q[1] + '/' + q[2] + '.png'; })(hQueue.shift()); }
  /** The land's height at a place, from the terrain tile that holds it. undefined while that tile has not arrived. */
  function heightAt(lat, lon, z) { z = Math.min(z, HZ); var fx = tileX(lon, z), fy = tileY(lat, z), x = Math.floor(fx), y = Math.floor(fy), h = heightTile(z, x, y); if (!h) return h === false ? fallbackH : undefined;
    var u = (fx - x) * 255, v = (fy - y) * 255, i = Math.min(254, Math.floor(u)), j = Math.min(254, Math.floor(v)), a = u - i, b = v - j, o = j * 256 + i;
    return (h[o] * (1 - a) + h[o + 1] * a) * (1 - b) + (h[o + 256] * (1 - a) + h[o + 257] * a) * b; }

  /** A height inside one terrain tile, between its samples; places on the very edge stay inside it. */
  function within(h, u, v) { u = Math.max(0, Math.min(254.999, u)); v = Math.max(0, Math.min(254.999, v)); var i = Math.floor(u), j = Math.floor(v), a = u - i, b = v - j, o = j * 256 + i;
    return (h[o] * (1 - a) + h[o + 1] * a) * (1 - b) + (h[o + 256] * (1 - a) + h[o + 257] * a) * b; }
  var index = (function () { var a = [], i, j; for (j = 0; j < N; j++) for (i = 0; i < N; i++) { var p = j * (N + 1) + i; a.push(p, p + N + 1, p + 1, p + 1, p + N + 1, p + N + 2); }
    var b = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, b); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(a), gl.STATIC_DRAW); return { b: b, n: a.length }; })();
  var tiles = {}, tileCount = 0, loading = 0, queue = [], tErr = 0, hErr = 0, fallbackH = 900, stamp = 0;
  /** Makes the tile's surface once its picture and its heights are both here. Positions are metres from the tile's own corner, so small numbers stay exact. */
  function build(t) {
    var z = t.z, hz = Math.min(z, HZ), sh = z - hz, hx = t.x >> sh, hy = t.y >> sh;
    var h = heightTile(hz, hx, hy); if (h === null) return false;
    var lon0 = lonOf(t.x, z), v = new Float32Array((N + 1) * (N + 1) * 5), o = 0, i, j, lo, hi = -1e9; lo = 1e9;
    t.ox = lon0 * KX; t.oy = latOf(t.y + 1, z) * R;
    for (j = 0; j <= N; j++) { var lat = latOf(t.y + j / N, z); for (i = 0; i <= N; i++) { var lon = lonOf(t.x + i / N, z), hh = h ? within(h, (tileX(lon, hz) - hx) * 255, (tileY(lat, hz) - hy) * 255) : fallbackH; if (hh < -500) hh = -430;
      v[o++] = lon * KX - t.ox; v[o++] = lat * R - t.oy; v[o++] = hh; v[o++] = i / N; v[o++] = j / N; if (hh < lo) lo = hh; if (hh > hi) hi = hh; } }
    t.buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, t.buf); gl.bufferData(gl.ARRAY_BUFFER, v, gl.STATIC_DRAW); return true;
  }
  function upload(t) { t.tex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t.tex);
    try { gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB, gl.RGB, gl.UNSIGNED_SHORT_5_6_5, t.img); } catch (e) { gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB, gl.RGB, gl.UNSIGNED_BYTE, t.img); }
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    if (t.img.width === 256 && t.img.height === 256) { gl.generateMipmap(gl.TEXTURE_2D); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_NEAREST); } else gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    t.img = null; }
  function drop(k) { var t = tiles[k]; if (!t) return; if (t.tex) gl.deleteTexture(t.tex); if (t.buf) gl.deleteBuffer(t.buf); if (t.im) { t.im.onload = t.im.onerror = null; t.im.src = ''; if (t.state === 1) loading--; } delete tiles[k]; tileCount--; }
  function pump() { queue.sort(function (a, b) { return a.pri - b.pri; });
    while (loading < 6 && queue.length) (function (t) { if (t.state !== 0) return; t.state = 1; loading++; var im = new Image(); t.im = im; im.crossOrigin = 'anonymous';
      im.onload = function () { loading--; t.im = null; t.img = im; t.state = 2; }; im.onerror = function () { loading--; t.im = null; t.state = 9; tErr++; };
      im.src = IMG + t.z + '/' + t.y + '/' + t.x; })(queue.shift()); }

  /** Which tiles the picture needs now: five by five of each size around a point a little ahead of the plane; the smallest sizes only when low. */
  var levels = [], wantAt = 0;
  function want(s, ground) {
    var agl = s.alt - (ground == null ? fallbackH : ground), zs = agl < 900 ? [9, 11, 13, 15, 17] : agl < 5000 ? [9, 11, 13, 15] : [9, 11, 13];
    stamp++; levels = []; queue = [];
    for (var li = 0; li < zs.length; li++) { var z = zs[li], r = z === 9 ? 1 : 2, n = Math.pow(2, z), size = 2 * Math.PI * KX / n;
      var ahead = Math.min(size * 0.6, s.v * 6), lat = s.lat + ahead * Math.cos(s.hdg) / R, lon = s.lon + ahead * Math.sin(s.hdg) / KX;
      var cx = Math.floor(tileX(lon, z)), cy = Math.floor(tileY(lat, z)), list = [];
      for (var dy = -r; dy <= r; dy++) for (var dx = -r; dx <= r; dx++) { var x = cx + dx, y = cy + dy; if (y < 0 || y >= n) continue; var k = z + '/' + x + '/' + y, t = tiles[k];
        if (!t) { t = tiles[k] = { z: z, x: x, y: y, state: 0, k: k }; tileCount++; }
        t.stamp = stamp; t.pri = li * 100 + Math.abs(dx) + Math.abs(dy) - (z <= 11 ? 1000 : 0); if (t.state === 0) queue.push(t); list.push(t); }
      levels.push({ z: z, far: size * (r + 1.2) * 1.5, list: list }); }
    if (tileCount > 190) { var old = []; for (var kk in tiles) if (tiles[kk].stamp !== stamp) old.push(tiles[kk]); old.sort(function (a, b) { return a.stamp - b.stamp; }); for (var i = 0; i < old.length && tileCount > 150; i++) drop(old[i].k); }
    pump(); pumpHeights();
  }

  /* ---- the sky: a bowl of colour around the camera, pale at the horizon and deep blue overhead, with the far land's haze below ---- */
  var sky = (function () { var v = [], rings = [[-90, 0.62, 0.66, 0.6], [-6, 0.7, 0.76, 0.76], [0, HAZE[0], HAZE[1], HAZE[2]], [5, 0.66, 0.8, 0.95], [18, 0.42, 0.64, 0.93], [45, 0.22, 0.46, 0.86], [90, 0.13, 0.33, 0.74]], seg = 16;
    function pt(e, a, c) { var ce = Math.cos(e * D); v.push(ce * Math.sin(a), ce * Math.cos(a), Math.sin(e * D), c[1], c[2], c[3]); }
    for (var i = 0; i < rings.length - 1; i++) for (var k = 0; k < seg; k++) { var a0 = k / seg * 2 * Math.PI, a1 = (k + 1) / seg * 2 * Math.PI, lo = rings[i], hi = rings[i + 1];
      pt(lo[0], a0, lo); pt(lo[0], a1, lo); pt(hi[0], a1, hi); pt(lo[0], a0, lo); pt(hi[0], a1, hi); pt(hi[0], a0, hi); }
    var b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(v), gl.STATIC_DRAW); return { b: b, n: v.length / 6 }; })();

  /* ---- the airliner: read from the same plane.glb the full engine uses ---- */
  var parts = [];
  fetch('plane.glb').then(function (r) { return r.arrayBuffer(); }).then(function (ab) {
    var dv = new DataView(ab), jl = dv.getUint32(12, true), json = JSON.parse(new TextDecoder().decode(new Uint8Array(ab, 20, jl))), bin = 20 + jl + 8;
    function acc(i) { var a = json.accessors[i], bv = json.bufferViews[a.bufferView]; return new Float32Array(ab.slice(bin + (bv.byteOffset || 0), bin + (bv.byteOffset || 0) + a.count * 12)); }
    json.nodes.forEach(function (node) { json.meshes[node.mesh].primitives.forEach(function (pr) { var pos = acc(pr.attributes.POSITION), nor = acc(pr.attributes.NORMAL), n = pos.length / 3, v = new Float32Array(n * 6);
      for (var i = 0; i < n; i++) { v[i * 6] = pos[i * 3]; v[i * 6 + 1] = pos[i * 3 + 1]; v[i * 6 + 2] = pos[i * 3 + 2]; v[i * 6 + 3] = nor[i * 3]; v[i * 6 + 4] = nor[i * 3 + 1]; v[i * 6 + 5] = nor[i * 3 + 2]; }
      var b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferData(gl.ARRAY_BUFFER, v, gl.STATIC_DRAW); var m = json.materials[pr.material];
      parts.push({ b: b, n: n, col: m.pbrMetallicRoughness.baseColorFactor.slice(0, 3), gear: node.name === 'gear', two: !!m.doubleSided }); }); });
  }).catch(function (e) { S.error('plane: ' + e.message); });

  /* ---- the approach lines: a flat green ribbon down each three-degree path ---- */
  var ribbon = gl.createBuffer(), ribbonAt = null;
  function ribbons() { var v = [], base = RW.elev; [[300, -1], [3360, 1]].forEach(function (e) { var a = onLine(e[0]), b = onLine(e[0] + e[1] * 10000), ax = a[1] * D * KX, ay = a[0] * D * R, bx = b[1] * D * KX, by = b[0] * D * R;
      var dx = bx - ax, dy = by - ay, l = Math.hypot(dx, dy), nx = -dy / l * 5, ny = dx / l * 5, h1 = base + 3, h2 = base + 3 + 10000 * Math.tan(3 * D);
      v.push(ax - nx, ay - ny, h1, ax + nx, ay + ny, h1, bx + nx, by + ny, h2, ax - nx, ay - ny, h1, bx + nx, by + ny, h2, bx - nx, by - ny, h2); });
    ribbonAt = { x: v[0], y: v[1], z: 0 }; for (var i = 0; i < v.length; i += 3) { v[i] -= ribbonAt.x; v[i + 1] -= ribbonAt.y; }
    gl.bindBuffer(gl.ARRAY_BUFFER, ribbon); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(v), gl.STATIC_DRAW); ribbonElev = base; }
  var ribbonElev = null;

  /* ---- one picture ---- */
  var drawn = 0, lost = false, ident = new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]);
  cv.addEventListener('webglcontextlost', function (e) { e.preventDefault(); lost = true; S.error('graphics lost'); S.tell('err|render|light context lost'); });
  function draw(v) {
    var s = v.s, px = s.lon * KX, py = s.lat * R, W = cv.width, H = cv.height, aspect = W / H, i, t;
    var fovy = 2 * Math.atan(Math.tan(v.fov / 2) / aspect);          // the simulator gives the angle across the picture; the projection wants it top to bottom
    if (v.now - wantAt > 350) { wantAt = v.now; want(s, heightAt(s.lat, s.lon, HZ)); }
    // finish at most two tiles a frame, so the picture never stalls
    var done = 0; for (var k in tiles) { t = tiles[k]; if (t.state === 2 && done < 2) { if (build(t)) { upload(t); t.state = 3; done++; } } }
    // the camera, behind and above the plane, which sits at the origin
    var ce = Math.cos(v.camE), back = [-Math.sin(v.camH) * ce, -Math.cos(v.camH) * ce, Math.sin(v.camE)], eye = [back[0] * v.range, back[1] * v.range, back[2] * v.range];
    var right = unit(cross([0, 0, 1], back)), up = cross(back, right), c = Math.cos(v.camR), sn = Math.sin(v.camR);
    var r2 = [right[0] * c - up[0] * sn, right[1] * c - up[1] * sn, right[2] * c - up[2] * sn], u2 = [up[0] * c + right[0] * sn, up[1] * c + right[1] * sn, up[2] * c + right[2] * sn];
    var view = viewFrom(eye, r2, u2, back, true), rot = viewFrom(eye, r2, u2, back, false);
    gl.viewport(0, 0, W, H); gl.disable(gl.BLEND); gl.disable(gl.CULL_FACE); gl.depthMask(true); gl.clearColor(HAZE[0], HAZE[1], HAZE[2], 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    // sky first, with no depth
    gl.disable(gl.DEPTH_TEST); gl.useProgram(pSky.p); gl.uniformMatrix4fv(pSky.uVP, false, mul(perspective(fovy, aspect, 0.1, 10), rot));
    gl.bindBuffer(gl.ARRAY_BUFFER, sky.b); gl.enableVertexAttribArray(pSky.aPos); gl.enableVertexAttribArray(pSky.aCol); gl.vertexAttribPointer(pSky.aPos, 3, gl.FLOAT, false, 24, 0); gl.vertexAttribPointer(pSky.aCol, 3, gl.FLOAT, false, 24, 12);
    gl.drawArrays(gl.TRIANGLES, 0, sky.n); gl.disableVertexAttribArray(pSky.aCol);
    // the land: coarse and far first, then each finer size over it, each with its own depth range
    gl.enable(gl.DEPTH_TEST); gl.useProgram(pLand.p); gl.uniform3f(pLand.uHaze, HAZE[0], HAZE[1], HAZE[2]); gl.uniform1f(pLand.uFog, 1 / 60000); gl.uniform1i(pLand.uTex, 0); gl.activeTexture(gl.TEXTURE0);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, index.b); gl.enableVertexAttribArray(pLand.aPos); gl.enableVertexAttribArray(pLand.aUv); drawn = 0;
    for (var li = 0; li < levels.length; li++) { var lv = levels[li], far = Math.max(lv.far, 4000), near = Math.max(1.5, far / 4000);
      gl.clear(gl.DEPTH_BUFFER_BIT); gl.uniformMatrix4fv(pLand.uVP, false, mul(perspective(fovy, aspect, near, far), view));
      for (i = 0; i < lv.list.length; i++) { t = lv.list[i]; if (t.state !== 3) continue;
        gl.uniform3f(pLand.uOff, t.ox - px, t.oy - py, -s.alt); gl.bindTexture(gl.TEXTURE_2D, t.tex); gl.bindBuffer(gl.ARRAY_BUFFER, t.buf);
        gl.vertexAttribPointer(pLand.aPos, 3, gl.FLOAT, false, 20, 0); gl.vertexAttribPointer(pLand.aUv, 2, gl.FLOAT, false, 20, 12); gl.drawElements(gl.TRIANGLES, index.n, gl.UNSIGNED_SHORT, 0); drawn++; } }
    gl.disableVertexAttribArray(pLand.aUv);
    // the approach ribbons and the plane, in front of everything near
    var vp = mul(perspective(fovy, aspect, 2, 30000), view); gl.clear(gl.DEPTH_BUFFER_BIT);
    if (v.guides) { if (ribbonElev !== RW.elev) ribbons(); gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.useProgram(pFlat.p); gl.uniformMatrix4fv(pFlat.uVP, false, vp);
      gl.uniform3f(pFlat.uHaze, HAZE[0], HAZE[1], HAZE[2]); gl.uniform1f(pFlat.uFog, 1 / 60000); gl.uniform4f(pFlat.uCol, 0.22, 1, 0.42, 0.6); gl.uniform3f(pFlat.uOff, ribbonAt.x - px, ribbonAt.y - py, -s.alt);
      gl.bindBuffer(gl.ARRAY_BUFFER, ribbon); gl.enableVertexAttribArray(pFlat.aPos); gl.vertexAttribPointer(pFlat.aPos, 3, gl.FLOAT, false, 12, 0); gl.drawArrays(gl.TRIANGLES, 0, 12); gl.disable(gl.BLEND); }
    if (parts.length) { // the plane's own directions on the map: nose, right wing, and up, rolled about the nose
      var ch = Math.cos(s.hdg), sh = Math.sin(s.hdg), cp = Math.cos(s.show), sp = Math.sin(s.show), f = [sh * cp, ch * cp, sp], r0 = [ch, -sh, 0], u0 = cross(r0, f), cr = Math.cos(s.roll), sr = Math.sin(s.roll);
      var rw = [r0[0] * cr - u0[0] * sr, r0[1] * cr - u0[1] * sr, r0[2] * cr - u0[2] * sr], uw = [u0[0] * cr + r0[0] * sr, u0[1] * cr + r0[1] * sr, u0[2] * cr + r0[2] * sr];
      var m = new Float32Array([-rw[0], -rw[1], -rw[2], 0, uw[0], uw[1], uw[2], 0, f[0], f[1], f[2], 0, 0, 0, 0, 1]);          // the model's +X is its left wing, +Y up, +Z the nose
      gl.useProgram(pPlane.p); gl.uniformMatrix4fv(pPlane.uVP, false, vp); gl.enableVertexAttribArray(pPlane.aPos); gl.enableVertexAttribArray(pPlane.aNor);
      for (i = 0; i < parts.length; i++) { var p = parts[i]; if (p.gear) { if (s.gear < 0.03) continue; var lift = (1 - s.gear) * 2.3, mg = new Float32Array(m); mg[12] = uw[0] * lift; mg[13] = uw[1] * lift; mg[14] = uw[2] * lift; gl.uniformMatrix4fv(pPlane.uM, false, mg); } else gl.uniformMatrix4fv(pPlane.uM, false, m);
        gl.uniform3f(pPlane.uCol, p.col[0], p.col[1], p.col[2]); gl.bindBuffer(gl.ARRAY_BUFFER, p.b); gl.vertexAttribPointer(pPlane.aPos, 3, gl.FLOAT, false, 24, 0); gl.vertexAttribPointer(pPlane.aNor, 3, gl.FLOAT, false, 24, 12); gl.drawArrays(gl.TRIANGLES, 0, p.n); }
      gl.disableVertexAttribArray(pPlane.aNor); }
  }

  /* ---- the engine, as the simulator sees it ---- */
  var B = { name: 'light', flat: false, mode: 'light', note: '', slowHud: true,
    ready: function () { var near = 0; for (var i = 0; i < levels.length; i++) if (levels[i].z <= 13) for (var j = 0; j < levels[i].list.length; j++) if (levels[i].list[j].state === 3) near++; return near >= 30; },
    ground: function (lat, lon) { return heightAt(lat, lon, HZ); },
    moved: function (p) { fallbackH = p.elev; wantAt = 0; },
    info: function () { return glName + ' ' + cv.width + 'x' + cv.height + ' tiles' + drawn + '/' + tileCount + ' load' + loading + ' q' + queue.length + (tErr ? ' imgerr' + tErr : '') + (hErr ? ' hgterr' + hErr : '') + ' P' + parts.length; } };
  window.flyLight = { tiles: function () { return tileCount; }, drawn: function () { return drawn; }, gl: gl };
  S.start(B);
  function loop() { requestAnimationFrame(loop); if (lost) return; var v = S.tick(); if (!v) return; try { draw(v); } catch (e) { S.error('draw: ' + e.message); } S.drawn(); }
  requestAnimationFrame(loop);
})();
