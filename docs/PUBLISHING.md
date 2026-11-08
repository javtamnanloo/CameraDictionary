# Publishing Camera Dictionary: GitHub and Google Play

A step-by-step checklist for a first-time publisher. Tick the boxes as you go.

---

## Part 1: GitHub (backup and sharing of the code)

**Words you'll see:**
- A **repository** ("repo") is the project folder plus its full history.
- A **commit** is a saved snapshot of the project with a short message.
- A **push** uploads your commits to GitHub.

- [x] GitHub tool installed and signed in (7 November 2026).
- [x] Code published at <https://github.com/javtamnanloo/CameraDictionary> (public, MIT license).
- [ ] **Every time you change something**, save a snapshot and upload it. From the project
      folder, run `git add -A`, then `git commit -m "Describe the change"`, then `git push`.
      Or use Android Studio's **Git** menu: **Commit…**, then **Push…**.

Never upload your signing key. `.gitignore` already blocks `*.jks`, `*.keystore` and
`keystore.properties`.

---

## Part 2: Google Play

### Rules worth knowing before you start

- The **app ID** `io.github.javtamnanloo.cameradictionary` is permanent once uploaded.
- New **personal** developer accounts must run a **closed test with at least 12 testers for
  14 days in a row** before the app can be released to everyone. Start recruiting friends,
  classmates or a reading group early.
- A free app can never be changed to paid later.

### Steps

- [ ] **1. Create a developer account** at <https://play.google.com/console>. Choose a
      **personal** account, pay the one-time US$25 fee, and complete identity verification.
      Google shows a developer contact email on the store page: use your public one,
      jav.tamnanloo@gmail.com (the same address the app's More tab shows).
- [ ] **2. Create the upload key.** Google Play uses it to check that updates really come from
      you. You can do this in Android Studio with **Build › Generate Signed App Bundle ›
      Create new…**, or Claude can create it for you. Store the key file **outside** the project
      folder, and keep the file and its passwords in a password manager **and** on a second
      drive. Then create `keystore.properties` in the project folder (it's git-ignored):

      ```
      storeFile=C:/Users/<you>/keys/cameradictionary-upload.jks
      storePassword=...
      keyAlias=upload
      keyPassword=...
      ```

- [ ] **3. Decide about "Buy me a coffee".** Google Play has made other apps remove donation
      links (WireGuard, AnkiDroid), because it counts them as payments outside Google Play. The
      safe choice is to hide the button in the version you upload: in `MoreScreen.kt`, set
      `SHOW_COFFEE_BUTTON = false`. The link stays in `README.md` on GitHub either way.
- [ ] **4. Build the bundle** (the file you upload): `gradlew bundleRelease`, which produces
      `app/build/outputs/bundle/release/app-release.aab`.
- [ ] **5. Create the app** in Play Console with **Create app**: name "Camera Dictionary",
      default language English, type App, price Free.
- [ ] **6. Store listing:**
      - App icon: `docs/store/icon-512.png`
      - Feature graphic: `docs/store/feature-graphic-1024x500.png`
      - At least 2 phone screenshots (Claude can take them from your phone over USB)
      - Short description (80 characters at most), for example:
        *Point your camera at a word to get its English meaning. Review with a Leitner box.*
      - Full description: you can adapt the Features list in `README.md`.
- [ ] **7. App content** (the left menu, under *Policy*):
      - **Privacy policy URL:**
        `https://github.com/javtamnanloo/CameraDictionary/blob/main/PRIVACY.md`
        This only works if the repo is public.
      - **Ads:** No. **App access:** all features are available without signing in.
      - **Content rating:** choose the *Reference, News, or Educational* category and answer
        honestly.
      - **Target audience:** 13 and older. Choosing younger ages brings extra "Families" rules.
      - **Data safety:** see the draft answers below.
- [ ] **8. Closed test:** go to **Testing › Closed testing › Create track**, upload the `.aab`,
      and add at least 12 testers (an email list or a Google Group). Share the opt-in link. Each
      tester must accept, install the app, and stay opted in for 14 days.
- [ ] **9. Apply for production** (the Dashboard shows the button when the 14 days are done),
      answer the questions about your test, then create a **Production** release. Google's
      review usually takes a few days.
- [ ] **10. Updates:** increase `versionCode` by 1 in `app/build.gradle.kts` (and update
      `versionName`, e.g. "1.1"), build the bundle again, and upload it as a new release.

### Data safety: draft answers (check them against Google's current form)

Google makes you, the developer, responsible for these answers. This draft reflects how the app
works today:

| Data type | Collected? | Shared? | Notes |
|---|---|---|---|
| App activity → **In-app search history** | Yes | No | The looked-up word is sent to the dictionary services. Processed ephemerally; required for the app to work; purpose: App functionality. |
| App info and performance → **Diagnostics** | Yes | No | Sent by Google ML Kit. Purpose: Analytics. |
| Device or other IDs | Yes | No | ML Kit uses Firebase Installations for its diagnostics. |
| Photos, files, location, contacts, personal info | No | No | Photos are analyzed on the phone and never leave it. |

- Is data encrypted in transit? **Yes** (HTTPS only).
- Can users ask for deletion? Saved words are deleted inside the app. The app doesn't store
  anything on a server.
- Reference: [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
