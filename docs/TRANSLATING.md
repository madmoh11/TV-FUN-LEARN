# Adding a language

The app has two sets of text. Translate both for a complete language. No code changes are needed.

## 1. The phone page

1. Copy `app/src/main/assets/web/lang/en.json` to a new file named with the language code, for example `fr.json`.
2. Set `"_name"` to the language's own name (`"Français"`) and `"_dir"` to `"ltr"` or `"rtl"`.
3. Translate the values. Keep the keys, the emoji and placeholders such as `{0}` as they are.

The new language appears in the language menu automatically, and phones set to that language use it by default.
Missing keys fall back to English.

## 2. The TV screen

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-fr/strings.xml`.
2. Translate the text between the tags. Keep placeholders such as `%d`, `%s` and `%1$s`.
3. Remove the `app_name` line: the app name is not translated.

The TV shows the language of its Android system settings.

## Check your work

```bash
./gradlew testDebugUnitTest -DrenderLang=fr
```

This draws the TV screens in your language into `app/build/renders/`. Look for text that is too long for its place.
Then open a pull request.
