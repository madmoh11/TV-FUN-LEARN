/* Flight physics for the Amman flight simulator. No drawing here, so it can be tested on its own.
   Angles are radians, distances metres, speeds metres a second. Heading is a compass heading: 0 north, growing clockwise. */
(function (root) {
  'use strict';
  var G = 9.81, R = 6371000, D = Math.PI / 180;
  var VMAX = 235, VSTALL = 62, GEAR = 3.2, CEIL = 12000;
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }
  function wrap(a) { a %= 2 * Math.PI; return a < 0 ? a + 2 * Math.PI : a; }
  /** The shortest way round from angle a to angle b. */
  function turn(a, b) { var d = (b - a) % (2 * Math.PI); if (d > Math.PI) d -= 2 * Math.PI; if (d < -Math.PI) d += 2 * Math.PI; return d; }

  /** A plane at a place. speed 0 on the ground, or flying level. */
  function start(latDeg, lonDeg, alt, hdgDeg, speed) {
    return { lat: latDeg * D, lon: lonDeg * D, alt: alt, hdg: wrap(hdgDeg * D), pitch: 0, roll: 0, v: speed, vs: 0, thr: speed > 0 ? 0.6 : 0,
      ix: 0, iy: 0, onGround: false, level: 0, bump: 0, stall: false, show: 0 };
  }

  /** One step of time. inp: x right(+)/left(-) and y up(+)/down(-) between -1 and 1, thr 0..1, level true to ask for wings level.
      ground is the height of the land under the plane, or null when it is not known yet. */
  function step(s, inp, dt, ground) {
    dt = clamp(dt, 0, 0.1);
    // the stick is followed smoothly, so a jittery hand or a late message never jerks the wings
    var k = Math.min(1, dt * 7);
    s.ix += (clamp(inp.x || 0, -1, 1) - s.ix) * k; s.iy += (clamp(inp.y || 0, -1, 1) - s.iy) * k;
    s.thr += (clamp(inp.thr == null ? s.thr : inp.thr, 0, 1) - s.thr) * Math.min(1, dt * 2.5);
    if (inp.level) s.level = 2.2;
    if (s.level > 0) { s.level -= dt; if (Math.abs(inp.x || 0) > 0.35 || Math.abs(inp.y || 0) > 0.35) s.level = 0; }
    var auto = s.level > 0;

    var lift = clamp((s.v / VSTALL) * (s.v / VSTALL), 0, 1);      // how much of its weight the wing carries
    s.stall = !s.onGround && lift < 0.98;

    // the stick asks for an attitude; the plane rolls and pitches toward it at a limited rate
    var wantRoll = auto ? 0 : s.ix * 58 * D, wantPitch = auto ? 2 * D : s.iy * 26 * D;
    if (s.onGround) { wantRoll = 0; wantPitch = lift > 0.85 ? clamp(wantPitch, 0, 14 * D) : 0; }
    if (s.stall) wantPitch = Math.min(wantPitch, -6 * D - (1 - lift) * 14 * D);          // the nose drops when the wing gives up
    s.roll += clamp((wantRoll - s.roll) * 2.4, -55 * D, 55 * D) * dt;
    s.pitch += clamp((wantPitch - s.pitch) * 1.9, -22 * D, 22 * D) * dt;

    // a banked wing turns the plane; arcade-quick, but still slower the faster you go
    if (!s.onGround) s.hdg = wrap(s.hdg + G * Math.tan(s.roll) / Math.max(s.v, 45) * 1.7 * lift * dt);
    else if (s.v > 1) s.hdg = wrap(s.hdg + s.ix * 0.45 * Math.min(1, s.v / 20) * dt);  // steering on the runway

    // climb or sink: where the nose points, less what a steep bank and a slow wing lose
    var wantVs = s.v * Math.sin(s.pitch) * lift - (1 - lift) * 38 - (1 - Math.cos(s.roll)) * s.v * 0.07;
    s.vs += (wantVs - s.vs) * Math.min(1, dt * 2.6);

    // speed: the engines pull toward the throttle's speed, climbing costs speed and diving gives it back
    var acc = (s.thr * VMAX - s.v) * 0.22 - G * Math.sin(s.pitch) * 0.55;
    if (s.onGround) { acc = (s.thr * VMAX - s.v) * 0.14; if (s.thr < 0.06) acc -= 9; }       // slow to roll, and brakes at idle
    s.v = clamp(s.v + acc * dt, 0, VMAX * 1.12);

    var vh = Math.sqrt(Math.max(0, s.v * s.v - s.vs * s.vs));
    s.lat += vh * Math.cos(s.hdg) * dt / R;
    s.lon += vh * Math.sin(s.hdg) * dt / (R * Math.max(0.2, Math.cos(s.lat)));
    s.alt += s.vs * dt;
    if (s.alt > CEIL) { s.alt = CEIL; if (s.vs > 0) s.vs = 0; }

    if (s.bump > 0) s.bump -= dt;
    if (ground != null) {
      var floor = ground + GEAR;
      if (s.alt <= floor) {
        if (!s.onGround && s.vs < -9) s.bump = 2.5;               // a hard landing: felt, never fatal
        s.alt = floor; if (s.vs < 0) s.vs = 0; s.onGround = true;
        if (s.pitch < 0) s.pitch += (0 - s.pitch) * Math.min(1, dt * 8);
        s.roll += (0 - s.roll) * Math.min(1, dt * 8);
      } else if (s.alt > floor + 0.6) s.onGround = false;
    }
    // what the eye sees: a slow plane flies nose-high
    s.show = s.pitch + (s.onGround ? 0 : (1 - lift) * 7 * D + clamp((120 - s.v) / 120, 0, 1) * 3 * D);
    return s;
  }

  var api = { step: step, start: start, turn: turn, wrap: wrap, clamp: clamp, VMAX: VMAX, VSTALL: VSTALL, GEAR: GEAR, D: D };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.FlyCore = api;
})(typeof self !== 'undefined' ? self : this);
