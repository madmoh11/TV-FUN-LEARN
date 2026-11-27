# Credits

**Lamma (لمّة)** is designed and developed by **Mohammad Aljaouni** ([@madmoh11](https://github.com/madmoh11)).

## Thank you

A warm thank-you to **Gravity Sound** for recording real breaking glass and sharing it freely with everyone.
The "cracked screen" prank would not feel real without it.

| Used in the app | Original work | Author | Licence |
| --- | --- | --- | --- |
| `app/src/main/assets/glass1.pcm` | [Glass breaking 3 (Gravity Sound)](https://commons.wikimedia.org/wiki/File:Glass_breaking_3_(Gravity_Sound).wav) | Gravity Sound | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| `app/src/main/assets/glass2.pcm` | [Glass breaking 4 (Gravity Sound)](https://commons.wikimedia.org/wiki/File:Glass_breaking_4_(Gravity_Sound).wav) | Gravity Sound | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |

Changes made: the recordings were trimmed and converted to raw 16-bit PCM. These two files keep their CC BY 4.0 licence;
the MIT licence of this project covers everything else.

## Font

Text is set in **Baloo Bhaijaan 2** by Ek Type, used under the
[SIL Open Font License 1.1](app/src/main/assets/fonts/OFL.txt).

## Other material

- The lion, fly and the other insects, basketball court and ball, and the space background, planet and spacecraft, were generated for this project with an AI image model.
- All other sounds (fly buzz, roar, chimes, game effects) are synthesised by the app's own code in `Synth.kt`.
- QR codes are drawn with [ZXing](https://github.com/zxing/zxing) (Apache License 2.0).
- Prayer times use the standard astronomical method, calculated on the device.
- Flag images come from [flagcdn.com](https://flagcdn.com) (Flagpedia), which offers them free of charge. The flags themselves are in the public domain.
- The Quran text is the Uthmani text of the [Tanzil Project](https://tanzil.net), version 1.1, used unchanged under the
  Creative Commons Attribution 3.0 licence. The file keeps its copyright notice: `app/src/main/assets/quran/quran-uthmani.txt`.
  The Tanzil "simple clean" text, under the same terms, is in `quran-simple-clean.txt` next to it. It is never shown: it tells the app
  how a word of the mushaf is spelled when a speech recogniser hears it.
- The recitation of Sheikh Mahmoud Khalil Al-Husary is streamed from [EveryAyah.com](https://everyayah.com); it is not part of the app.
- The mushaf typeface is [Amiri Quran](https://www.amirifont.org) by Khaled Hosny, under the SIL Open Font Licence.
- Surah names and juz boundaries come from [alquran.cloud](https://alquran.cloud).

## Adhkar

The morning and evening adhkar come from
[Seen-Arabic/Morning-And-Evening-Adhkar-DB](https://github.com/Seen-Arabic/Morning-And-Evening-Adhkar-DB) (MIT licence),
which follows the book Hisn al-Muslim. The passages of the Quran among them are not taken from that file: the app
keeps only the ayah numbers and shows the Tanzil text.

## Call to prayer

"The Adhan - Muslim Call to Prayer - Aaqib Azeez", uploaded to Wikimedia Commons by Atcovi, licensed
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The recording is not part of the app: the TV
downloads it unchanged from Wikimedia Commons the first time.

## Home screen backgrounds

The four background pictures were generated for this app with an AI image model.

## Weather

Weather data by [Open-Meteo](https://open-meteo.com/), licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

## News studio

The five studio backdrops were generated for this app with an AI image model. The presenter is cut out of the camera
picture with [MediaPipe Tasks Vision](https://github.com/google-ai-edge/mediapipe) 0.10.14 and its selfie segmentation
model, both by Google, licensed [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0).
