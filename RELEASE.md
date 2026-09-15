## Release Process

### Versioning
- Semantic versioning: MAJOR.MINOR.PATCH
- Baseline after Sprint 4: 0.4.0

**One number, one place.** `appVersion` in `gradle.properties` is the source. From it come
the Android `versionName` (`app/build.gradle.kts`), the desktop `packageVersion` and the
`APP_VERSION` constant behind both About dialogs. Never type a version anywhere else —
the About dialog once carried a hand-written string and sat two minor releases behind
what was shipping.

Two things do **not** follow automatically and have to be bumped by hand:

- `versionCode` in `app/build.gradle.kts` — Android's own counter, one up per release.
- The line `**Current version: vX.Y.Z**` at the top of `README.md`.

### Build
1. Run the tests — all four levels, the way CI does:
   - `./gradlew.bat :shared:desktopTest :desktopApp:test :server:test :app:testDebugUnitTest --console=plain`
   - Green shared tests do not prove the Android app compiles. `:app:assembleDebug`
     builds the Android target of `shared` as well, which is what actually proves it.
2. Build the Android app:
   - `./gradlew.bat :app:assembleDebug`
   - Output: `app/build/outputs/apk/debug/app-debug.apk`
3. Desktop distributable, only when one is actually wanted:
   - `./gradlew.bat :desktopApp:packageDistributionForCurrentOS`
   - Output: `desktopApp/build/compose/binaries/main/msi/` and `…/exe/`
   - **Two tools this needs and the dev machine does not have by default.** Both were
     missing when the first installer was attempted (14.09.2026):
     - **A JDK that carries `jpackage`.** Android Studio's JetBrains Runtime does not —
       it ships `jlink` but no `jpackage`, so the Gradle JVM cannot package at all, not
       even the plain app image. Install a full JDK 21 next to it
       (`winget install --id EclipseAdoptium.Temurin.21.JDK`); it replaces nothing.
     - **WiX 3.x.** `jpackage` does not build `.msi`/`.exe` itself, it drives WiX.
       JDK 21 needs the 3.x line — 4 and 5 do not work
       (`winget install --id WiXToolset.WiXToolset` gives 3.14.1).
   - Point the build at the full JDK for that one run, rather than changing the Gradle
     JVM for everything:
     ```powershell
     $env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory | Where-Object Name -like 'jdk-21*' | Select-Object -First 1).FullName
     $env:PATH = "C:\Program Files (x86)\WiX Toolset v3.14\bin;$env:PATH"
     ```
   - `:desktopApp:createDistributable` skips WiX and yields a runnable
     `…/binaries/main/app/FinTracker Desktop/FinTracker Desktop.exe` — still needs the
     `jpackage` JDK, but no installer toolchain.
   - The database is `~/FinTracker/fintracker.db` either way, so an installed copy and a
     copy run from `build/` share one set of data.

### Git tag
- Tag a commit that CI has reported green, not just the newest one.
- `git tag vX.Y.Z`
- `git push origin vX.Y.Z`

A tag is the cheap half and worth having on its own: it gives the state a name while the
repository stays private. Publishing is the separate step below.

### GitHub Release (optional)
- Create a Release for the tag, with notes in plain text.
- **No binary assets** — since v0.8.1 releases carry text only. An APK in a private
  repository serves nobody, and an installer has never been part of a release. If one is
  ever meant to ship, that is its own piece of work, not a step here.

### macOS (public copy only)

The rule above says why the private repository carries no files: nobody can reach them.
That reasoning does not carry over to the public code-only copy, where the whole point of
a release is that somebody without a GitHub account can download it. So macOS is the one
exception, and it lives entirely over there.

- Run **macOS build** from the Actions tab of `willywonka644/fintracker-kmp`. It builds
  nothing on its own — no push triggers it — because a `.dmg` is wanted when somebody is
  about to be handed one.
- Leave **release_tag** empty for a test run: the two files stay as workflow artifacts,
  which need a GitHub login to fetch. Give a tag (`v0.10.0`) to create that release in the
  public copy and attach both files, which then have plain public download links.
- **Two files, both architectures.** `-arm64` for Apple Silicon, `-x64` for Intel. Guessing
  which one someone has shows up as "the app does not open", so neither is guessed.
- The workflow refuses to run outside the public copy (`if: github.repository == …`). macOS
  minutes cost ten times the Linux rate on a private repository, and the file would be
  behind the same closed door as everything else there. The guard sits in the file rather
  than the file being left out of the mirror, so the two trees stay identical.

**Two things that are not the project's choice and have to be lived with:**

- **The version in the bundle is not the version of the app.** macOS rejects a bundle
  version starting with zero outright, so `0.10.0` becomes `1.10.0` in the metadata Finder
  shows under Get Info. Derived in `desktopApp/build.gradle.kts`, not typed anywhere. The
  file name and the About dialog both carry the real `0.10.0`.
- **The app is unsigned, so the first launch is refused.** Signing needs a paid Apple
  Developer account; notarising needs the same. Opening it once through
  **System Settings › Privacy & Security › Open Anyway** is the whole workaround, and it is
  needed once per machine. Say this when handing the file over — otherwise it reads as a
  broken download.

Building a `.dmg` locally needs a Mac; `jpackage` only ever produces a package for the
system it runs on. The icon is the one part CI adds by hand (`sips` + `iconutil`, the only
tools that write `.icns`); without `-PmacIconFile=…` a local build simply gets the default
icon.
