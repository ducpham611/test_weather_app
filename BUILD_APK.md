# Building the APK with GitHub Actions

You do not need Android Studio or an Android SDK on your computer. GitHub's
runners already have everything installed.

---

## Step 1 — Put the project on GitHub

1. Unzip `weather-app.zip`.
2. Create a new **empty** repository on GitHub (no README, no .gitignore).
3. From inside the unzipped `weather-app` folder:

```bash
git init
git add .
git commit -m "VN Weather app"
git branch -M main
git remote add origin https://github.com/YOUR_NAME/YOUR_REPO.git
git push -u origin main
```

If you prefer not to use the command line, you can drag the folder's contents
into GitHub's **"uploading an existing file"** page instead. Make sure the
hidden `.github` folder is included — the web uploader skips hidden folders, so
the command line is safer.

---

## Step 2 — Get the debug APK (the quick path)

The push already started the build.

1. Open your repository → the **Actions** tab.
2. Click the **Build APK** run at the top and wait for the green tick
   (about 3–6 minutes the first time).
3. Scroll to **Artifacts** at the bottom of the run page.
4. Download **vnweather-debug-apk** — a zip containing `app-debug.apk`.
5. Unzip it, copy the APK to your phone, and install it.
   Android will ask you to allow **"Install unknown apps"** for your file manager
   or browser. This APK is signed with the standard debug key, so it installs
   and runs straight away.

You can also rerun the build any time: **Actions → Build APK → Run workflow**.

⚠️ A debug APK is fine for testing on your own phones, but it is not suitable
for the Play Store and it is slightly larger and slower because minification
is off.

---

## Step 3 — Get a signed release APK (for real distribution)

### 3a. Create a keystore once

On any computer with Java installed:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias vnweather
```

Answer the prompts and remember both passwords.

<b>Keep `release.jks` safe and backed up.</b> If you lose it, you can never
update the app on Google Play again.

### 3b. Turn the keystore into text

```bash
base64 -w 0 release.jks > release.jks.base64     # Linux
base64 -i release.jks | tr -d '\n' > release.jks.base64   # macOS
```

### 3c. Add four repository secrets

Repository → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | the whole contents of `release.jks.base64` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `vnweather` |
| `KEY_PASSWORD` | the key password |

### 3d. Run the release build

Either push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

…or go to **Actions → Release APK → Run workflow**.

The run produces:
- `app-release.apk` — install this directly on a phone.
- `app-release.aab` — upload this one to Google Play.

Both appear under **Artifacts**, and a tagged run also attaches them to a
GitHub Release.

---

## What the workflows do

| File | Trigger | Output |
|---|---|---|
| `.github/workflows/build-apk.yml` | every push, PR, or manual | runs unit tests, then a debug APK |
| `.github/workflows/release-apk.yml` | `v*` tags or manual | signed release APK + AAB |

Both use JDK 17 and Gradle 8.9 directly, so the missing `gradle-wrapper.jar`
is not a problem. Gradle caching is on, so later builds take roughly 1–2 minutes.

---

## If a build fails

- Open the failed run and expand the red step to read the error.
- Test failures upload a **test-reports** artifact with the full HTML report.
- **"SDK location not found"** — should not happen on GitHub runners; it means
  a stray `local.properties` was committed. Delete it.
- **"Unsigned APK"** on release — one of the four secrets is missing or
  misspelled. Names are case-sensitive.
