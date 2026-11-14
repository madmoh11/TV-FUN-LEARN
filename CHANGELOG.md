# Changelog

## 3.6

- New Family tab on the phone page.
- Family hall of fame: every named player collects points from all five games, with weekly champions
  (player of the week, math champion, word champion). The board is saved on the TV.
- Quick vote: ask a question with two to four choices; everyone votes from their phone and the result shows on the TV.
- Family timer: a countdown in the corner of the TV with a big alert and a chime when time is up.

## 3.5

- A shared look for the whole app: one cartoon font for Arabic and English on the TV and on the phone page, one colour
  palette, the same title ribbon, start button, countdown and winner confetti in every game.
- Space Race redrawn in a colourful cartoon style: painted space background, cartoon spacecraft and planet, drifting
  stars, shooting stars, animated flames and a stretch when a rocket is pushed.
- New app icon and TV banner.

## 3.4

- New game: Space Race, a vocabulary race for up to 6 players. An English word appears on the phone and the player
  picks its Arabic meaning; correct answers move a rocket across the TV.
- A bank of 1,000 words in ten "galaxies", kept in `app/src/main/assets/data/words_bank.json`.
- Words answered wrongly come back until they are answered right twice; mastered words do not repeat until the galaxy is finished.
- Speed bonus, turbo after three in a row, a golden meteor every fifth question, Robo-X for a lone player,
  new rockets every 50 mastered words, and a review board of missed words at the end.

## 3.3

- New game: Math Tug of War for up to 6 players in two teams. Solve sums on your phone to pull the rope;
  easy, medium, hard or a level that gets harder; knockout, time limit and sudden death; the computer plays for an empty team.

## 3.2

- Voice changer for the microphone: monster, deep man, woman, child, chipmunk, robot, alien and cave echo.
- TV box volume from the phone: up, down and mute.

## 3.1

- Live camera: a phone shows its camera in a corner of the TV, like a reporter at home. Choose the corner and the
  size (up to full screen), switch between front and back camera, and optionally send your voice too.

## 3.0

First public release.

- English and Arabic on both the TV and the phone page. The TV follows its system language; the phone page follows
  the browser language and has a language switch.
- Translations live in plain files, so a new language needs no code changes (see `docs/TRANSLATING.md`).
- Every TV now creates its own HTTPS key on first run. No key ships inside the app.
- Games: basketball, air hockey and snake, for one or two players.
- Drawing with ten tools, six page styles, laser pointer and spotlight.
- Family comments, emoji, live microphone and pictures on the TV.
- Pranks: cracked screen, fly, lion and a fake shutdown message.
- Prayer alert with offline prayer-time calculation.
