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
s = run(s, { x: 0, y: 0, thr: 0 }, 120, 900);
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
console.log('flight physics: all checks passed');
