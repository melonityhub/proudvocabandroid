# Release signing

`release.keystore` (PKCS#12) is the **stable release key** for ProudVocab. It is
committed on purpose: the app is distributed by sideloading APKs from GitHub
Releases (no Play Store), and Android only installs an update when the new APK
is signed with the same key as the installed one. A key that only lives in CI
secrets would not be available to a fresh clone or to a fork; a committed key
means every CI run — and every clone — produces identically signed release
APKs. This is the same pattern open-source sideloaded apps (e.g. F-Droid
projects) commonly use.

- `release.properties` holds the matching store/key passwords and the key
  alias (`proudvocab`).
- `app/build.gradle.kts` uses it for the `release` build type. Environment
  variables `PV_KEYSTORE_FILE` / `PV_KEYSTORE_PASSWORD` / `PV_KEY_ALIAS` /
  `PV_KEY_PASSWORD` (e.g. from GitHub Actions secrets) take priority, so the
  key can later be moved to secrets without any code changes.

## Rotating the key (or moving it to secrets)

1. Generate a new key (`keytool` or `openssl`) — RSA 2048+, ~10 000-day
   validity, PKCS#12 output.
2. Either replace `release.keystore` + `release.properties`, or set the
   `PV_KEYSTORE_*` secrets and let the environment override the committed key.
3. Bump `versionCode` in `app/build.gradle.kts`.

> **One-time effect of any key change:** devices that installed a build signed
> with the old key must uninstall before reinstalling.
>
> **Trade-off:** anyone with clone access can sign future updates of
> `com.proudvocab.android`. Treat a repo compromise as a signing compromise —
> if that matters more than convenience, move the key to Actions secrets.
