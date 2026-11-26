# Changelog

## 3.27

- Reciting to the microphone is easier to live with:
  - Starting the ayah again is followed however long the ayah is, after a mistake or without one, and so is saying
    only the missed word. A word misheard while going over what was already said is not judged.
  - Letters that sound alike (the heavy letters and their light twins) are taken as the recogniser's slip.
  - A choice on the phone, **how easy the listening is**: exact, usual or easy. The easy level forgives one letter in a
    long word and writes a word after three tries, still counted as a mistake.
  - A mistake no longer makes a sound on the TV: the screen turns red and the phone buzzes.

## 3.26

- The home screen can have a **background picture**: four that come with the app (a mosque at dusk, a geometric
  pattern, a desert night and misty mountains) or a photo sent from the phone. A dark veil keeps the text readable.
- Fixed the clock in Jordan and Syria: old TV boxes still take an hour off in winter, as before 2022. Lamma now keeps
  both at three hours ahead of UTC all year, for its clock, the prayer times, the family reminders and the day's
  portion. A setting on the phone page moves the clock an hour either way for any other wrong box.

## 3.25

- Prayer times now match the printed timetable of Amman to the minute: a time is rounded up to the next minute, and
  Maghrib in the Jordanian cities is 6 minutes after sunset.

## 3.24

- Fixed: on a TV set to Arabic the recitation was never found (HTTP 404), because the file number in its address was
  written in Arabic digits. This silenced the surahs of the home screen and the memoriser's journey alike.
- Fixed: the home screen showed through a surah or adhkar page after its first moment, which made it hard to read.
- A surah no longer runs on to the next ayah when an ayah could not be played; it stays and says why.

## 3.23

- New: a **home screen for the TV**. Opening Lamma now shows the clock, the Gregorian and Hijri dates, the next
  prayer with a countdown and the five prayer times of the day, and rows of large tiles for the remote:
  - **The Holy Quran**: al-Kahf (marked on Fridays), al-Baqara, Ya-Sin, al-Mulk, al-Waqi'a, ar-Rahman and a list of all
    114 surahs. A surah is read ayah by ayah in mushaf script with Al-Husary's recitation.
  - **Adhkar and ruqyah**: the morning and evening adhkar with their counts, the ruqyah verses and ayat al-Kursi.
    Passages of the Quran inside the adhkar are taken from the Tanzil text, never typed.
  - **TV apps**: the other apps of the box, so the screen can stand in for its launcher.
- The home screen can open by itself when the TV starts (a setting on the phone page), and it can be made the home
  screen of the box with one ADB command.
- The call to prayer can be sounded when a prayer time starts. The TV saves the recording the first time it is
  online; the phone page has buttons to try it and to stop it.

## 3.22

- The memoriser's journey has moved out of the games into its own **Quran** tab on the phone page, and the Quran
  quiz is no longer listed among the games quiz modes; it starts only from the memoriser's journey.

## 3.21

- The memoriser's journey: the Quran quiz is now titled "Memorisation quiz" ("مسابقة الحفظ" in Arabic), because the
  old Arabic title could be read the wrong way.

## 3.20

- The memoriser's journey: **recite to the microphone**. The TV shows only the ayah numbers and a line for each word.
  The phone listens, and every word is written the moment it is recited right.
  - A wrong or forgotten word writes nothing: the TV turns red and waits until the right word is said. Words that
    needed a second try stay in another colour, and the part goes into the revision plan with its mistakes.
  - Going back a few words for a run-up, putting a word right in the same breath, and the opening "أعوذ بالله" and
    basmala are not mistakes.
  - Two buttons for a grown-up: "Show the word" (a mistake) and "It was right", for when the microphone misheard.
  - It uses the speech recognition of the phone's browser (Chrome or Safari, Arabic) on the secure link, so the
    phone needs the internet and the speech goes to Google or Apple. It follows words, not vowel marks or tajweed.
    The Tanzil "simple clean" text is included to know how each word of the mushaf is written when it is heard.

## 3.19

- The memoriser's journey: the recitation is now saved first and then played, which works on TV boxes whose player
  cannot stream it; an ayah heard once plays again without the internet. When it still cannot be played, the TV and
  the phone say why. A new option plays the recitation from the phone instead of the TV.

## 3.18

- The memoriser's journey: a **Quran challenge**. The TV asks "which ayah comes next?" and "how does this ayah go
  on?" from one surah or from all the memorised juz, and up to six players answer on their phones. Every choice,
  right or wrong, is real Quran text taken from other ayahs.

## 3.17

- New: **The memoriser's journey**, for learning the Quran by heart.
  - Listen and repeat: one ayah on the TV in mushaf script with Al-Husary's recitation, and its words hide step by
    step until only the first is left.
  - Recite from memory: the text is hidden on the TV while a grown-up follows it on the phone and marks each ayah.
  - Today's portion: mark the juz already memorised and every part comes back for revision after 1, 3, 7, 14, 30 and
    60 days; a part with mistakes comes back the next day. A streak counts the days in a row.
  - The whole Quran is included (Tanzil Uthmani text, unchanged). The recitation is streamed, so the TV needs the
    internet for it.

## 3.16

- Move to the beat: the motion sensor can be set up for the way you hold the phone. Stand still, take one step to the
  right, and the phone learns where your right is. The sensor also resets itself during the 3-2-1 countdown.

## 3.15

- Move to the beat: a figure now stands on the track and copies every move the phone sends (step, jump, squat), before
  the round as well, so you can see that your moves arrive.
- The phone shows what the motion sensor feels as a live bar, and says plainly when there is no signal. The sensor
  needs a lighter push by default and also works before the round starts.

## 3.14

- New game: **Move to the beat**, a workout on the beat for one or two players. Moves run towards you on a track: step
  left, step right, jump or duck when they reach the gold line. The phone in your hand is the motion sensor; four
  buttons do the same by touch. Timing gives Perfect or Good, streaks multiply the score, and the music is generated
  by the app at three speeds.

## 3.13

- New game for the whole room: **flags, capitals and spelling**. One question on the TV, everybody answers on their
  phone, and faster right answers earn more. 76 countries with their flags; spelling uses the word bank and says the
  word aloud on the TV and on the phone.
- **Call the family**: ready messages such as "Dinner is ready" or your own text appear on the TV with a chime, now or
  every day at a set time.
- **Kids' chores**: a list of chores with points. A finished chore adds its points to the family board and the TV
  celebrates. Chores open again every day.

## 3.12

- The phone page can live on the phone's home screen with its own icon, like an app. The settings tab shows the steps for
  Android and for iPhone.

## 3.11

- Four more insects for the prank, next to the fly: cockroach, spider, ant and wasp. The crawlers run without a buzz and
  scurry off the screen; the wasp flies and buzzes like the fly.

## 3.10

- New prank: a person's shadow crosses the bottom of the picture, head and shoulders only, like somebody walking in
  front of a cinema screen. A man walks from right to left, a girl from left to right.

## 3.9

- The fly prank: a size slider (40% to 500%) and a soft shadow under the insect, so it looks like it is sitting on the
  glass. The shadow moves away and fades when it takes off.

## 3.8

- The phone page now fits every screen: phones, tablets and laptops, upright or sideways. Cards flow into two or three
  columns on wide screens, the tabs move to the side when the screen is sideways, and the game controllers use the
  space they have.

## 3.7

- The app has a new name: **Lamma** (لمّة, a family get-together), with a new mascot icon and TV banner.
  Earlier versions were called TV Draw. It installs over them as an update.

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
