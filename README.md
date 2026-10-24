# Mi TV Declutter

Personal Xiaomi Mi TV cleanup and Android TV home-screen customization by **Sakshya Deep**.

This is the version 1 snapshot: a minimal launcher, HDMI/input fixes, cleanup scripts and the initial cleanup report. The main branch carries version 2; this snapshot is retained under the `v1.0.0` tag.

- [Cleanup report and recovery notes](docs/cleanup-report.md)
- [Package review guide](docs/debloat.md)
- `my-tv.txt`: the device-specific list of disabled apps and settings.

## Build and install

Requires Java 17, Android SDK platform 34 and build-tools 34.0.0, plus Android platform-tools (`adb`). Set `JAVA_HOME` and `ANDROID_HOME` to your installations.

```sh
sh build.sh
sh install.sh TV_IP
```

Enable network debugging on the TV and approve the computer first. Review the installation prompts before switching the default home screen. Preserve your locally generated `key.jks` for future updates; signing keys are excluded from Git.

```sh
sh candidates.sh TV_IP
sh check.sh TV_IP my-tv.txt
sh undo.sh TV_IP
```

Package decisions are specific to this Mi TV. Review the report and package list before applying them elsewhere. Device backups, app installers, SDKs and logs are local-only. Report addresses use `192.0.2.10` as a placeholder. Historical commands describe the original setup and may require local backups or earlier code.

## License

Project modifications and the personal report are by Sakshya Deep. Code is distributed under the [MIT license](LICENSE). Required inherited copyright notices are retained. Existing Android package identifiers are preserved for compatibility with installed launchers.
