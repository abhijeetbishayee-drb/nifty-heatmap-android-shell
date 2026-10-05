# nifty-heatmap-android-shell

Shared Android code for the two **Sector Chakra** apps (formerly "Nifty Heatmap"):

| Repo | Output | Distribution |
|---|---|---|
| [`nifty-heatmap`](https://github.com/abhijeetbishayee-drb/nifty-heatmap) | signed **APK** | sideload / direct download |
| `nifty-heatmap-play` (private) | signed **AAB** | Google Play |

Both pull this repo in as a git submodule at `nifty-heatmap-android-shell/` and
include the `shell` library module from it. Each app repo only holds what must
differ between them: application ID, signing, version numbering, and (later, on
the Play side) sign-in and billing.

## What the shell is

One activity (`ShellActivity`) with a WebView showing the live boards on GitHub
Pages — Nifty 50, F&O Sectors, RRG (Beta), the Rollover / PCR / 44 EMA boards, and the US boards (Heatmap / RRG / 44 EMA / PCR)
boards. The boards are **not** reimplemented natively: the previous Kivy app did
that and drifted from the website. A change to the website reaches the apps
without an app release.

- Links on `abhijeetbishayee-drb.github.io` under the board paths stay in the app;
  everything else (e.g. a tile's NSE quote page) opens in the browser.
- Native retry screen if the page itself can't load; data-poll failures are left
  to the page.
- Back walks the board history before leaving the app.
- JS timers are paused while the app is in the background.
- Pure Java/framework code, no native libraries — so the 16 KB page-size rule and
  ABI splits don't apply.

## Changing it

Edit here, push, then bump the submodule in **both** app repos:

```bash
cd nifty-heatmap-android-shell && git pull origin main && cd ..
git add nifty-heatmap-android-shell && git commit -m "Bump shell"
```
