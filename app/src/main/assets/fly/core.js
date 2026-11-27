/* Flight physics for the Amman flight simulator. No drawing here, so it can be tested on its own.
   Angles are radians, distances metres, speeds metres a second. Heading is a compass heading: 0 north, growing clockwise. */
(function (root) {
  'use strict';
  var G = 9.81, R = 6371000, D = Math.PI / 180;
  var VMAX = 235, VSTALL = 62, GEAR = 3.9, BELLY = 3.4, CEIL = 12000;
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }
  function wrap(a) { a %= 2 * Math.PI; return a < 0 ? a + 2 * Math.PI : a; }
  /** The shortest way round from angle a to angle b. */
  function turn(a, b) { var d = (b - a) % (2 * Math.PI); if (d > Math.PI) d -= 2 * Math.PI; if (d < -Math.PI) d += 2 * Math.PI; return d; }

  /** A plane at a place. speed 0 on the ground, or flying level. */
  function start(latDeg, lonDeg, alt, hdgDeg, speed) {
    return { lat: latDeg * D, lon: lonDeg * D, alt: alt, hdg: wrap(hdgDeg * D), pitch: 0, roll: 0, v: speed, vs: 0, thr: speed > 0 ? 0.6 : 0,
      ix: 0, iy: 0, onGround: false, level: 0, bump: 0, stall: false, show: 0,
      gear: speed > 0 ? 0 : 1, gearWant: speed > 0 ? 0 : 1, flaps: 0, flapPos: 0, brake: false, belly: false, touch: 0, touchVs: 0, vstall: VSTALL };
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
    // wheels and flaps: they take a few seconds to travel, and the wheels stay down while the plane stands on them
    if (inp.gear != null) s.gearWant = inp.gear ? 1 : 0;
    if (s.onGround && !s.belly) s.gearWant = 1;
    s.gear = clamp(s.gear + (s.gearWant > s.gear ? 1 : -1) * dt / 3, Math.min(s.gear, s.gearWant), Math.max(s.gear, s.gearWant));
    if (inp.flaps != null) s.flaps = clamp(Math.round(inp.flaps), 0, 2);
    s.flapPos += clamp(s.flaps - s.flapPos, -dt / 2.5, dt / 2.5);
    s.brake = !!inp.brake;
    s.vstall = VSTALL - s.flapPos * 8;          // flaps let the wing carry the plane more slowly

    var lift = clamp((s.v / s.vstall) * (s.v / s.vstall), 0, 1);      // how much of its weight the wing carries
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
    acc -= s.v * (0.02 * s.gear + 0.018 * s.flapPos + (s.brake && !s.onGround ? 0.05 : 0));      // wheels, flaps and air brakes all hold it back
    if (s.onGround) { acc = (s.thr * VMAX - s.v) * 0.14 - s.v * 0.012 * s.flapPos; if (s.thr < 0.06) acc -= 5; if (s.brake) acc -= 13; if (s.belly) acc -= 16; }   // slow to roll; brakes, or the belly, stop it
    s.v = clamp(s.v + acc * dt, 0, VMAX * 1.12);

    var vh = Math.sqrt(Math.max(0, s.v * s.v - s.vs * s.vs));
    s.lat += vh * Math.cos(s.hdg) * dt / R;
    s.lon += vh * Math.sin(s.hdg) * dt / (R * Math.max(0.2, Math.cos(s.lat)));
    s.alt += s.vs * dt;
    if (s.alt > CEIL) { s.alt = CEIL; if (s.vs > 0) s.vs = 0; }

    if (s.bump > 0) s.bump -= dt;
    if (ground != null) {
      var floor = ground + (s.gear > 0.9 ? GEAR : BELLY);
      if (s.alt <= floor) {
        if (!s.onGround) { s.touch++; s.touchVs = s.vs; s.belly = s.gear < 0.9; if (s.vs < -9 || s.belly) s.bump = 2.5; }      // a hard or wheels-up landing: felt, never fatal
        s.alt = floor; if (s.vs < 0) s.vs = 0; s.onGround = true;
        if (s.pitch < 0) s.pitch += (0 - s.pitch) * Math.min(1, dt * 8);
        s.roll += (0 - s.roll) * Math.min(1, dt * 8);
      } else if (s.alt > floor + 0.6) { s.onGround = false; s.belly = false; }
    }
    // what the eye sees: a slow plane flies nose-high
    s.show = s.pitch + (s.onGround ? 0 : (1 - lift) * 7 * D + clamp((120 - s.v) / 120, 0, 1) * 3 * D);
    return s;
  }

  /** Where the plane is against a runway it is flying toward. rw: lat, lon of both ends in degrees, elev and width in metres.
      dist: metres still to go to the near end (negative once past it). cross: metres right(+) or left(-) of the centre line.
      slope: the angle in degrees up from the touchdown point to the plane; 3 is right. lights: how many of the four approach lights are white. */
  function approach(s, rw) {
    var la = s.lat / D, lo = s.lon / D, mLat = R * D, mLon = R * D * Math.cos(rw.lat1 * D);
    var ex = (rw.lon2 - rw.lon1) * mLon, ey = (rw.lat2 - rw.lat1) * mLat, len = Math.sqrt(ex * ex + ey * ey), hdg = wrap(Math.atan2(ex, ey));
    var from1 = Math.abs(turn(s.hdg, hdg)) <= Math.PI / 2;                 // flying the runway's own direction, or the opposite one
    var ox = from1 ? rw.lon1 : rw.lon2, oy = from1 ? rw.lat1 : rw.lat2, ux = (from1 ? ex : -ex) / len, uy = (from1 ? ey : -ey) / len;
    var px = (lo - ox) * mLon, py = (la - oy) * mLat, along = px * ux + py * uy, cross = px * uy - py * ux;
    var aim = 300, h = s.alt - rw.elev - GEAR, d = aim - along, slope = d > 30 ? Math.atan2(h, d) / D : (h > 3 ? 90 : 3);
    return { dist: -along, cross: cross, len: len, hdg: from1 ? hdg : wrap(hdg + Math.PI), slope: slope, lights: slope > 3.5 ? 4 : slope > 3.2 ? 3 : slope >= 2.8 ? 2 : slope >= 2.5 ? 1 : 0,
      on: along >= 0 && along <= len && Math.abs(cross) <= rw.width / 2 + 8, show: along > -14000 && along < len && Math.abs(turn(s.hdg, from1 ? hdg : hdg + Math.PI)) < 70 * D && Math.abs(cross) < 4000 };
  }

  var api = { approach: approach, BELLY: BELLY,  step: step, start: start, turn: turn, wrap: wrap, clamp: clamp, VMAX: VMAX, VSTALL: VSTALL, GEAR: GEAR, D: D };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.FlyCore = api;
})(typeof self !== 'undefined' ? self : this);
