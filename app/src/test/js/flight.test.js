// Run with: node app/src/test/js/flight.test.js
const F = require('../../main/assets/fly/core.js'), assert = require('assert');
const run = (s, inp, secs, ground) => { for (let i = 0; i < secs * 50; i++) F.step(s, inp, 0.02, ground); return s; };
const deg = r => r * 180 / Math.PI;

let s = run(F.start(31.95, 35.9, 2000, 90, 150), { x: 0, y: 0, thr: 0.64 }, 30, 900);
assert(Math.abs(s.alt - 2000) < 60, 'hands off, it flies level: ' + s.alt);
assert(Math.abs(deg(s.hdg) - 90) < 1 && s.lon > 35.9 * F.D, 'heading east moves east');
assert(Math.abs(s.v - 150) < 12, 'and holds its speed: ' + s.v);

s = run(F.start(31.95, 35.9, 2000, 0, 150), { x: 1, y: 0.15, thr: 0.7 }, 6, 900);
assert(s.roll > 40 * F.D && s.roll < 60 * F.D, 'stick right banks right: ' + deg(s.roll));
assert(deg(s.hdg) > 20 && deg(s.hdg) < 180, 'and turns clockwise: ' + deg(s.hdg));
s = run(F.start(31.95, 35.9, 2000, 0, 150), { x: -1, y: 0.15, thr: 0.7 }, 6, 900);
assert(deg(s.hdg) > 180 && deg(s.hdg) < 340, 'stick left turns the other way: ' + deg(s.hdg));

s = run(F.start(31.95, 35.9, 2000, 0, 150), { x: 0, y: 1, thr: 1 }, 10, 900);
assert(s.alt > 2300 && s.pitch > 15 * F.D, 'stick back climbs: ' + s.alt);
s = run(F.start(31.95, 35.9, 2000, 0, 150), { x: 0, y: -1, thr: 0.5 }, 5, 900);
assert(s.alt < 1800, 'stick forward dives: ' + s.alt);

s = F.start(31.95, 35.9, 2000, 0, 150); run(s, { x: 0.9, y: 0.6, thr: 0.8 }, 4, 900);
assert(Math.abs(s.roll) > 0.5);
F.step(s, { x: 0, y: 0, thr: 0.8, level: true }, 0.02, 900); run(s, { x: 0, y: 0, thr: 0.8 }, 3, 900);
assert(Math.abs(s.roll) < 3 * F.D && Math.abs(s.pitch) < 5 * F.D, 'auto-level brings the wings level: ' + deg(s.roll) + ' ' + deg(s.pitch));

s = run(F.start(31.95, 35.9, 2500, 0, 150), { x: 0, y: 0.5, thr: 0 }, 25, 900);
assert(s.stall || s.onGround, 'with no power it stalls');
assert(s.alt < 2450, 'and sinks: ' + s.alt);
s = run(s, { x: 0, y: 0, thr: 0, gear: true }, 120, 900);
assert(s.onGround && Math.abs(s.alt - (900 + F.GEAR)) < 0.01, 'it never goes through the ground: ' + s.alt);
assert(s.v < 1, 'and brakes to a stop at idle: ' + s.v);

s = F.start(31.7225, 35.9932, 730 + F.GEAR, 80, 0); s.onGround = true;
run(s, { x: 0, y: 0, thr: 1 }, 14, 730);
assert(s.onGround && s.v > F.VSTALL, 'it gathers speed on the runway and stays down until asked: ' + s.v);
run(s, { x: 0, y: 0.7, thr: 1 }, 12, 730);
assert(!s.onGround && s.alt > 800, 'pull back and it takes off: ' + s.alt);

s = F.start(31.95, 35.9, 2000, 0, 150);
for (let i = 0; i < 200; i++) F.step(s, { x: i % 2 ? 1 : -1, y: 0, thr: 0.7 }, 0.02, 900);
assert(Math.abs(s.roll) < 6 * F.D, 'a jittering stick does not shake the wings: ' + deg(s.roll));
assert(Math.abs(F.turn(350 * F.D, 10 * F.D) - 20 * F.D) < 1e-9 && Math.abs(F.turn(10 * F.D, 350 * F.D) + 20 * F.D) < 1e-9);

// wheels, flaps and brakes
s = F.start(31.95, 35.9, 2000, 0, 150); assert(s.gear === 0, 'it starts in the air with the wheels up');
run(s, { x: 0, y: 0, thr: 0.64, gear: true }, 4, 900); assert(s.gear === 1, 'the wheels take a moment and come down: ' + s.gear);
let clean = run(F.start(31.95, 35.9, 2000, 0, 150), { x: 0, y: 0, thr: 0.64 }, 30, 900).v, dirty = run(F.start(31.95, 35.9, 2000, 0, 150), { x: 0, y: 0, thr: 0.64, gear: true, flaps: 2 }, 30, 900).v;
assert(dirty < clean - 15, 'wheels and flaps slow the plane: ' + clean + ' ' + dirty);
s = run(F.start(31.95, 35.9, 2000, 0, 56), { x: 0, y: 0.1, thr: 0.24, flaps: 2 }, 3, 900); assert(!s.stall, 'with full flaps it still flies at 200 km/h');
s = run(F.start(31.95, 35.9, 2000, 0, 56), { x: 0, y: 0.1, thr: 0.24, flaps: 0 }, 3, 900); assert(s.stall, 'without flaps it does not');
s = F.start(31.7225, 35.9932, 730 + F.GEAR, 80, 0); s.onGround = true; run(s, { x: 0, y: 0, thr: 0, gear: false }, 5, 730); assert(s.gear === 1, 'the wheels cannot be raised while it stands on them');
const stop = brake => { const p = F.start(31.7225, 35.9932, 730 + F.GEAR, 80, 0); p.onGround = true; p.v = 70; let t = 0; while (p.v > 1 && t < 200) { F.step(p, { x: 0, y: 0, thr: 0, brake }, 0.02, 730); t += 0.02; } return t; };
assert(stop(true) < stop(false) * 0.6, 'the brakes stop it much sooner: ' + stop(true) + ' ' + stop(false));
s = F.start(31.95, 35.9, 760, 0, 75); run(s, { x: 0, y: -0.2, thr: 0.2, gear: false }, 20, 730);
assert(s.onGround && s.belly && s.touch === 1, 'landing with the wheels up is a belly landing: ' + s.alt); assert(Math.abs(s.alt - 730 - F.BELLY) < 0.01);
s = F.start(31.95, 35.9, 745, 0, 70); run(s, { x: 0, y: 0, thr: 0.25, gear: true, flaps: 2 }, 0.1, 700); for (let i = 0; i < 4000 && !s.onGround; i++) F.step(s, { x: 0, y: -0.03, thr: 0.25, gear: true, flaps: 2 }, 0.02, 730);
assert(s.onGround && !s.belly && s.touch === 1 && s.touchVs < 0 && s.touchVs > -9, 'a gentle landing on its wheels is recorded: ' + s.touchVs);

// the approach aid, on Queen Alia's runway 08L/26R
const RW = { lat1: 31.7267, lon1: 35.9696, lat2: 31.7322, lon2: 36.0075, elev: 719, width: 61 };
let a = F.approach(F.start(31.7267, 35.9696, 719 + F.GEAR, 81.4, 0), RW); const RWH = a.hdg;
assert(Math.abs(a.dist) < 5 && Math.abs(a.cross) < 5 && a.on && Math.abs(a.len - 3660) < 60 && Math.abs(a.hdg / F.D - 81.4) < 1.5, 'standing on the threshold: ' + JSON.stringify(a));
const out = (km, h, side) => { const hd = RWH, d = km * 1000; return F.start(31.7267 - (d * Math.cos(hd) + side * Math.sin(hd)) / 6371000 / F.D, 35.9696 - (d * Math.sin(hd) - side * Math.cos(hd)) / (6371000 * Math.cos(31.7267 * F.D)) / F.D, 719 + F.GEAR + h, 81.4, 80); };
a = F.approach(out(8, 8300 * Math.tan(3 * F.D), 0), RW); assert(Math.abs(a.dist - 8000) < 30 && Math.abs(a.cross) < 20 && a.lights === 2 && a.show, 'on the glide path, two white and two red: ' + JSON.stringify(a));
assert(F.approach(out(8, 800, 0), RW).lights === 4 && F.approach(out(8, 200, 0), RW).lights === 0, 'too high is all white, too low is all red');
assert(F.approach(out(5, 300, 200), RW).cross > 150 && F.approach(out(5, 300, -200), RW).cross < -150, 'right of the centre line is positive');
s = F.start(31.7322, 36.0075, 1200, 261.4, 80); a = F.approach(s, RW); assert(Math.abs(a.dist) < 10 && Math.abs(a.hdg / F.D - 260.3) < 1.5, 'from the east it is the other end: ' + JSON.stringify(a));
assert(!F.approach(F.start(31.95, 35.9, 2000, 0, 150), RW).show && !F.approach(F.start(31.9, 35.9, 719, 81, 0), RW).on, 'far away there is no aid, and off the runway is off');
console.log('flight physics: all checks passed');
