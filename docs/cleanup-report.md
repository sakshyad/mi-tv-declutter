# Mi TV Declutter — cleanup report

By Sakshya Deep. Network addresses use the documentation placeholder `192.0.2.10`. This is a historical log; commands may reference local-only backups or earlier source revisions.

A record of everything done to my TV with Mi TV Declutter, so I (or an LLM) can see what was changed and how to undo it.
Newest entries at the bottom. Every change says how to undo it.

## The TV

| | |
|---|---|
| Model | Xiaomi Mi TV, `MiTV-AXSO2` (product `dangalUHD`) |
| OS | Android TV 11 |
| Build | `RTT0.211201.001.1150`, fingerprint `Xiaomi/dangalUHD/dangalUHD:11/RTT0.211201.001/1150:user/release-keys` |
| Security patch | 2024-02-05 |
| RAM | ~2 GB total, ~750 MB available before any changes |
| IP / adb | `192.0.2.10:5555` (Wi-Fi adb, laptop authorized with "Always allow") |
| Original home screen | `com.google.android.tvlauncher` (stock Google TV launcher) |
| Original keyboard | `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME` (Gboard) |

## Backup

Folder: `tv-backup-2026-10-20/` (inside this repo; git-ignored locally via `.gitignore`, never committed)

- `build-props.txt`: full `getprop`
- `enabled-before.txt`, `disabled-before.txt`, `all-packages.txt`, `user-installed.txt`: app lists
- `settings-global.txt`, `settings-secure.txt`, `settings-system.txt`, `device-config.txt`: all settings
- `home-launcher.txt`, `keyboard.txt`, `keyboards-all.txt`, `window-policy.txt` (which app each remote key goes to)
- `meminfo-before.txt`: memory before
- `apks/<package>/`: installers of my 9 own apps (301 MB), reinstall with `adb install-multiple apks/<package>/*.apk`
- `restore.sh`: compares the TV to the snapshot and reverses differences

Restore:
```sh
sh tv-backup-2026-10-20/restore.sh            # dry run, shows differences
sh tv-backup-2026-10-20/restore.sh --apply    # reverts them
```
After a reboot the dry run also lists counters the TV changes itself (e.g. `Phenotype_boot_count`); harmless, but review before `--apply`.

Not backed up (not possible without root): the firmware/system image and app data (logins, history). Neither is touched by Mi TV Declutter. Last resort: factory reset (does not change firmware).

Already off before Mi TV Declutter (leave as is): `com.google.android.videos`, `com.mitv.dream`, `com.google.android.play.games`.

My own apps: Spotify (`com.spotify.tv.android`), SonyLIV (`com.sonyliv`), Hotstar (`in.startv.hotstar`), Zee5 (`com.graymatrix.did`), TeamViewer QuickSupport (`com.teamviewer.quicksupport.market`), JioCinema/Jio (`com.jio.media.stb.ondemand`), AirCast (`com.sand.aircasttv`), Apple TV (`com.apple.atve.androidtv.appletv`), AirConsole (`com.airconsole.androidtv`).

## Change log

| # | Date | Change | Command used | Undo |
|---|---|---|---|---|
| 1 | 2026-10-20 | Connected over Wi-Fi adb, laptop authorized on TV | `adb connect 192.0.2.10:5555` | Developer options → Revoke USB debugging authorizations |
| 2 | 2026-10-20 | Took backup (read-only, nothing changed on TV) | see Backup | n/a |

## Notes

- zsh gotcha: don't use `path` as a loop variable in shell commands, it overwrites `PATH`.
- All files for this project (backup, logs, scripts) live inside this repo. The backup folder is excluded from git locally (`.gitignore`), so `git status` and `git pull` ignore it.

## App survey (2026-10-20, read-only)

- Free memory before cleanup: 750-970 MB available of 2 GB (varies with what's open). Biggest user: stock home screen `com.google.android.tvlauncher` ~208 MB, then Play services ~116 MB, voice search `katniss` ~69 MB, Play Store ~100 MB, PatchWall `com.mitv.tvhome.atv` ~40 MB.
- Miracast on this TV = `com.xiaomi.mitv.smartshare` (Wi-Fi Display screen) + `com.xiaomi.wfdsinkhelperservice` + `com.mitv.milinkservice`. Keep all three on.
- AirDroid Cast TV = `com.sand.aircasttv`. Phone casting (YouTube/Chrome cast) = `com.google.android.apps.mediashell`.
- HDMI/tuner = `com.droidlogic.tvinput` + `com.android.tv` (Live TV/input viewer). Keep on.
- Remote's extra buttons go to `com.droidlogic` (Settings, F-keys) and `com.xiaomi.android.tvsetup.partnercustomizer` (app buttons). Both marked never.
- Home screen candidates: `com.google.android.tvlauncher` (current) and the Settings fallback. PatchWall is not a Home candidate here.
- Must-keep apps (user): Netflix `com.netflix.ninja`, Hotstar `in.startv.hotstar`, Prime Video `com.amazon.amazonvideo.livingroom`, Zee5 `com.graymatrix.did`, SonyLIV `com.sonyliv`, Apple TV `com.apple.atve.androidtv.appletv`, AirDroid Cast `com.sand.aircasttv`, Miracast (above), YouTube `com.google.android.youtube.tv`, Play Store `com.android.vending`.

## HDMI inputs (must stay working)

- HDMI 2 = PS5, HDMI 3 = Jio set-top box (user configured).
- Input services: `com.droidlogic.tvinput/.services.Hdmi1InputService/HW5`, `Hdmi2InputService/HW6`, `Hdmi3InputService/HW7`, plus AV1 (`HW1`) and antenna/ADTV (`HW16`). All in `com.droidlogic.tvinput`.
- Custom input names aren't in `settings` (`tv_input_custom_labels` = null). They're stored privately by `com.droidlogic.tvinput` / `com.android.tv` / `com.android.tv.settings`, which can't be read without root. Those apps are never turned off, and turning an app off never clears its data, so the names stay.
- HDMI-CEC is off (`hdmi_control_enabled=0`). Leave it as is.
- Saved: `tv-backup-2026-10-20/tv-input.txt`, `hdmi-control.txt`.
- Check after each batch: switch to HDMI 2 and HDMI 3, confirm the picture and the names.

## Decisions (2026-10-20)

Keep on (user's choice): Google voice search `com.google.android.katniss`, phone casting `com.google.android.apps.mediashell`, phone remote `com.google.android.tv.remote.service`, Jio `com.jio.media.stb.ondemand`, plus the must-keep apps and Miracast above.

## Turned off: batch 1, ads and tracking (2026-10-20)

Command for each: `adb shell pm disable-user --user 0 <package>`. Undo one: `adb shell pm enable <package>`.

| Package | What it was |
|---|---|
| `com.google.android.tvrecommendations` | Google's sponsored row |
| `com.google.android.backdrop` | Google photo screensaver |
| `com.android.dreams.basic` | basic screensaver |
| `com.google.android.feedback` | Google feedback |
| `com.android.printspooler` | printing |
| `com.android.htmlviewer` | HTML viewer |
| `com.miui.tv.analytics` | Xiaomi analytics |
| `com.xiaomi.statistic` | Xiaomi usage stats |
| `com.mitv.tvhome.mitvplus` | Mi TV+ free channels with ads |
| `com.mitv.tvhome.michannel` | Mi channel rows |
| `com.xm.webcontent` | PatchWall web content |
| `com.xiaomi.mitv.handbook` | built-in manual |
| `com.xiaomi.mimusic2` | Mi Music |

`my-tv.txt` (repo root) lists every app turned off, one `off <package>` per line. `sh check.sh 192.0.2.10 --fix my-tv.txt` turns them back off if an update brings them back.
Undo all of them: `for p in $(sed -n 's/^off //p' my-tv.txt); do adb shell pm enable $p; done`, or `sh tv-backup-2026-10-20/restore.sh --apply`.

Batch 1 check after restart: TV booted normally to `com.google.android.tvlauncher`. HDMI 1/2/3 input services present (HDMI 3 connected), Miracast `com.xiaomi.mitv.smartshare` still on. Free memory ~970 MB → 1,033 MB.

## Turned off: batch 2, apps not needed (2026-10-20)

Same command/undo as batch 1. Update service `com.xiaomi.mitv.updateservice` deliberately kept ON for security updates.


| Package | What it was | You lose |
|---|---|---|
| `com.mitv.tvhome.atv` | PatchWall (Xiaomi home) | PatchWall button on remote may do nothing |
| `com.google.android.youtube.tvmusic` | YouTube Music | |
| `com.google.android.marvin.talkback` | TalkBack screen reader | |
| `com.xiaomi.mitv.tvmanager` | TV Manager (cleaner) | |
| `com.mitv.gallery` | photo gallery | USB photos |
| `com.xiaomi.mitv.mediaexplorer` | media explorer | USB videos/music browsing |
| `com.mitv.videoplayer` | video player | USB video playback |
| `com.spotify.tv.android` | Spotify (my app) | reinstall not needed, just `pm enable` |
| `com.teamviewer.quicksupport.market` | TeamViewer QuickSupport (my app) | |
| `com.airconsole.androidtv` | AirConsole (my app) | |

Batch 2 check after restart: booted normally to home, HDMI 1/2/3 present (HDMI 3 connected), Miracast (smartshare, wfdsinkhelperservice, milinkservice), AirDroid Cast and phone casting all still on. 26 apps off in total (3 were off before). Free memory right after boot ~894 MB (memory just after a boot moves around while apps start; compare again later).

User confirmed after batch 1: HDMI 3 opens the Jio box correctly.

## Known issue: Chromecast "untrusted device" (not caused by the cleanup)

Casting from YouTube on the phone shows "Untrusted device, couldn't be verified. This could be caused by outdated device firmware."
Cause, from `adb logcat`: the cast app (`com.google.android.apps.mediashell`, on and running) can't sign its device proof because the TV's Widevine DRM key won't load:
```
WVCdm: MapOEMCryptoResult crypto_session_method = LoadCertificatePrivateKey, result = 30
cast_shell: Cast.WidevineCastAuth.Error.SessionOpenFailed ... JWT signing failed
```
So it's the TV's firmware/DRM certificate, not any app turned off (none of them touch DRM or casting).
Options: check for a firmware update (Settings → Device Preferences → About → System update); use Miracast / AirDroid Cast meanwhile; or turn batch 1 back on to rule it out completely if casting worked before today.

Update: casting left as is, by my choice (status of casting before the cleanup unknown). Needs a firmware update from Xiaomi; can't be fixed over adb without root.

## Power cut (2026-10-20)

The TV restarted on its own from a power cut after batch 2. Reconnected with `adb connect 192.0.2.10:5555`; all 26 off apps stayed off.

## Step 5: Mi TV Declutter home screen installed (2026-10-20)

- Command: `YES=1 sh install.sh 192.0.2.10 "<apps>"`. It downloads `tvhome.apk` v1.4.0 from the GitHub release and installs `com.ansub.tvhome`. `YES=1` answered the "turn off old home screen" prompt (I approved).
- Apps on it, in order: Netflix, Hotstar, Prime Video, Zee5, SonyLIV, Apple TV, YouTube, AirDroid Cast, Smart Share (Miracast), Jio, Play Store. HDMI inputs are shown automatically.
  `com.netflix.ninja,in.startv.hotstar,com.amazon.amazonvideo.livingroom,com.graymatrix.did,com.sonyliv,com.apple.atve.androidtv.appletv,com.google.android.youtube.tv,com.sand.aircasttv,com.xiaomi.mitv.smartshare,com.jio.media.stb.ondemand,com.android.vending`
- Turned off by install.sh: `com.google.android.tvlauncher` (Google's home screen, ~208 MB). It's saved on the TV in `/data/local/tmp/tvhome-disabled.txt`, which undo.sh reads.
- Home now opens `com.ansub.tvhome/.MainActivity`.
- Edit tiles with the remote: hold OK on a tile (move/replace/remove), or the "Add app" tile.
- Undo: `sh undo.sh 192.0.2.10` turns tvlauncher back on and removes the new home screen.
- Free memory just before install (TV up ~3 min): ~830 MB.

## Step 6: settings (2026-10-20)

| Setting | Before | After | Undo |
|---|---|---|---|
| animations (`global window_animation_scale`, `transition_animation_scale`, `animator_duration_scale`) | 0.0 already | unchanged | `settings put global <key> 1.0` |
| `secure screensaver_enabled` | 1 | 0 | `adb shell settings put secure screensaver_enabled 1` |
| `secure screensaver_activate_on_sleep` | 1 | 0 | `... put secure screensaver_activate_on_sleep 1` |
| `secure screensaver_activate_on_dock` | 1 | 0 | `... put secure screensaver_activate_on_dock 1` |
| `system screen_off_timeout` (sleep after no remote input) | 900000 (15 min) | 300000 (5 min) | `adb shell settings put system screen_off_timeout 900000` |

These lines are also in `my-tv.txt`, so `sh check.sh 192.0.2.10 --fix my-tv.txt` re-applies them if an update resets them.
Old screensaver was `com.google.android.backdrop/.Backdrop` (turned off in batch 1).
Watch out: if the TV goes to sleep while gaming on HDMI 2 (PS5) with no remote presses, set it back to 15 min, or longer.

## Step 7: final restart (2026-10-20)

Waited 30 s after the last change, then `adb reboot`. TV booted straight to the Mi TV Declutter home screen; HDMI 1/2/3 present; screensaver off and 5-min sleep held; 27 apps off (3 were off before + 23 by me + tvlauncher).

Free memory (`MemAvailable`):
| When | Free |
|---|---|
| Before anything (TV on a while) | ~750-970 MB |
| ~1 min after boot, after batch 2 | ~894 MB |
| ~1 min after boot, after home screen + settings | ~1,010 MB |

## Home screen customised: Inputs and Settings moved to top icons (2026-10-20)

My request: no separate HDMI 1/2/3 and Settings tiles. Instead an **Inputs** icon at the top that opens a panel listing HDMI 1/2/3, and **Settings** as a gear icon at the top.

Code change (in this repo, uncommitted; `git diff` shows it):
- `src/com/ansub/tvhome/MainActivity.java`:
  - Bottom row is now only Play Store (if not in the app list) + Add app.
  - Top-right icons, left to right: [quick shortcuts] Inputs (HDMI icon, only if the TV has HDMI) · Settings (gear) · Network.
  - New `showInputs()` opens the side panel (the same `panel()` used for Add app) with the HDMI inputs; picking one runs the existing `openInput()`.
  - Removed the now-unused `isInput()`.
- `ci/smoke-test.sh`: looks for the Settings icon (`content-desc="Settings"`) instead of a "Settings" tile text.
- Undo the code: `git checkout src/com/ansub/tvhome/MainActivity.java ci/smoke-test.sh`, then reinstall the official one: `rm -rf out && sh install.sh 192.0.2.10 "<apps>"` (downloads the release).

Build setup (all inside this repo, excluded from git via `.gitignore`):
- `.android-sdk/`: Google Android command-line tools 11076708 + `platforms;android-34` + `build-tools;34.0.0` (Android SDK license accepted, ~461 MB).
- `.jdk17/`: Temurin JDK 17.0.20.1 (build fails with Java 21: d8 crashes on `MainActivity$2.class`, even on unmodified code).
- `key.jks`: signing key made on first build (password `tvhome123`, build.sh default). **Keep it**: future builds must use the same key, or install.sh has to uninstall/reinstall.
- `out/tvhome.apk`: the built app (36 KB). install.sh uses it automatically when it exists.
- Build: `JAVA_HOME=$PWD/.jdk17/jdk-17.0.20.1+1/Contents/Home ANDROID_HOME=$PWD/.android-sdk sh build.sh`
- Install: `YES=1 sh install.sh 192.0.2.10 "<apps>"`. Because the key differs from the official release, it uninstalled the release copy and installed mine (tile order reset to the list passed in).

Checked on the TV: Home → `com.ansub.tvhome`; top shows Inputs, Settings, Wi-Fi; the Inputs panel lists HDMI1, HDMI2, HDMI3.
Note: the TV reports the inputs as "HDMI1/2/3". The PS5/Jio names aren't set through Android's input names (Settings → Inputs), so the panel can't show them. Rename them there and the panel picks the names up.
Note: if I later run plain `install.sh` after deleting `out/`, it reinstalls the official release, which puts the HDMI/Settings tiles back.

## Sleep timeout changed to 30 min (2026-10-20)

- `system screen_off_timeout`: 300000 (5 min) → **1800000 (30 min)**, so the TV doesn't sleep during PS5/Jio sessions (those don't use the TV remote).
- Command: `adb shell settings put system screen_off_timeout 1800000`. `my-tv.txt` updated to match, so `check.sh --fix` keeps 30 min.
- Undo: back to 5 min `... put system screen_off_timeout 300000`, or the original 15 min `... 900000`.

## Home screen: Add app moved into the app grid (2026-10-20)

- Before: 6 apps in row 1, 5 in row 2, and Add app alone on a 3rd row.
- Now: Add app (and Play Store, if it's not one of the apps) takes the next free slot after the apps, so it's 2 full rows of 6.
- Code: `src/com/ansub/tvhome/MainActivity.java` in `buildTiles()`. The row count includes those extra tiles, and they're placed with `gridX/gridY` like the apps instead of on a separate bottom row.
- Rebuilt with build.sh and installed with `adb install -r out/tvhome.apk` (same key, so the tile order is kept). Checked on the TV: Add app is at the end of row 2.
- Undo: `git checkout src/com/ansub/tvhome/MainActivity.java` loses *all* home screen code changes; to undo only this one, put back the old "Bottom row" block (see `git diff`). Then rebuild and install.
