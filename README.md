<h1 align="center">Noop Ridge</h1>

<p align="center"><b>Your strap. Your data. Your phone. Offline, on-device, no cloud.</b></p>

<p align="center"><sub>Noop 12.1 · Ridge 1.0 — the all-new <b>Ridge</b> design, with one consolidated metrics repository (metricsV2) and expanded Key Metrics &amp; Insights.</sub></p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208%2B-E8B84B?style=flat-square">
  <img alt="Local first" src="https://img.shields.io/badge/local-first-E8B84B?style=flat-square">
  <img alt="Account free" src="https://img.shields.io/badge/account-free-C8902F?style=flat-square">
  <img alt="WHOOP 4 and 5" src="https://img.shields.io/badge/works%20with-WHOOP%204.0%20%26%205.0-6B737B?style=flat-square">
  <a href="LICENSE"><img alt="License: PolyForm Noncommercial 1.0.0" src="https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-6B737B?style=flat-square"></a>
</p>

<p align="center">
  <a href="#download">⬇&nbsp;Download</a> ·
  <a href="#features">Features</a> ·
  <a href="#strap-support">Strap support</a> ·
  <a href="#privacy">Privacy</a> ·
  <a href="android/README.md">Build from source</a>
</p>

---

## Download

| Platform | Build | Notes |
|---|---|---|
| **Android** | `noop-ridge-12.1.0.apk` (see [Releases](https://github.com/eccentricmeerkat/noopridge/releases)) | The full app. `minSdk 26` (Android 8+). Sideload: enable "install unknown apps". Blocked by Play Protect? See **Installing on Android** below. |

Application id `com.noop.app.ridge`. It installs beside the original Noop and any staging build, and has its own data.
**Moving from a staging build?** Export a backup first (**Hub › Settings › Backup & restore › Export backup**), install this
release, then **Import backup**. The `.noopbak` file carries the whole database and the settings that matter; it does not
carry the exact date of birth, layouts, the Sage key or the Health Connect permission, so set those again.

> **Installing on Android (Play Protect blocked it?).** Noop Ridge isn't on the Play Store. It is a
> **source-available APK** you sideload, because the project is anonymous and has no paid Play identity to publish
> under. Android treats it as an "unknown app" and **Google Play Protect** may warn or block on install. Nothing is
> wrong with the file; it is just missing a Play signature.
>
> - **Tap "Install anyway."** When the warning appears, choose **More details → Install anyway**.
> - **No "Install anyway" button?** Grant the source directly: **Settings → Apps → Special app access → Install
>   unknown apps**, pick the **browser or file manager you are installing from**, turn on **"Allow from this
>   source"**, then open the APK again.
> - **Still blocked?** It is your call for an app you trust: **Play Store → your profile icon → Play Protect → ⚙
>   Settings**, switch **"Scan apps with Play Protect" off**, install, then switch it **back on**.
> - **Reinstalling is safe**, but it starts fresh: data stays on the device and there is no cloud copy. Export a
>   backup first if you want to keep it.

Prefer to build it yourself? See [`android/README.md`](android/README.md).

Everything runs **offline by default**. Nothing about you leaves the device unless you switch on a feature that sends
it. Noop makes only three kinds of network request: the optional **Sage** AI coach (off until you add your own API
key), a once-a-day check for a newer release (sends nothing about you and never installs anything; switch it off in
**Hub › About** and it makes no request at all), and a default-off Experimental one-way **push** to an endpoint you
own. Noop operates no server, account or telemetry service.

---

Noop Ridge is a standalone, fully **offline** companion app for WHOOP straps (4.0 and 5.0). It pairs directly with the
strap over Bluetooth, stores everything on your own phone in SQLite, imports your existing WHOOP history, and computes
Charge, Effort, Rest, HRV and sleep **locally**, with no WHOOP account and no WHOOP cloud.

There is also **experimental** support for the **Oura Ring**: real overnight data from a ring you own, with real
limits. See [Oura ring support](#oura-ring-support).

It is built on prior community interoperability work and exists for one reason: to let someone who owns a WHOOP strap
read **their own biometric data** from **their own device**, on a phone **they** control.

> **Not affiliated with WHOOP.** Noop is an independent, unofficial interoperability project. It is not affiliated
> with, endorsed by, or connected to WHOOP, Inc. "WHOOP" is used only to identify the hardware Noop talks to. Use it
> only with a device you own, and not in breach of any agreement that applies to you. **Noop is not a medical
> device**; every derived metric is an approximation, not clinical data. See [`DISCLAIMER.md`](DISCLAIMER.md).

---

## Contents

- [Why Noop](#why-noop)
- [Features](#features)
- [Strap support](#strap-support)
- [Oura ring support](#oura-ring-support)
- [What to expect when you start](#what-to-expect-when-you-start)
- [How your data flows](#how-your-data-flows)
- [Privacy](#privacy)
- [Attribution](#attribution)
- [Disclaimer](#disclaimer)
- [License](#license)
- [Docs](#docs)

---

## Why Noop

You bought the strap. The biometric stream it produces is yours. Noop is built on that premise:

- **Own your data.** Noop reads heart rate, R-R intervals, SpO₂, skin temperature, respiration, motion, battery and
  event data straight off the strap over Bluetooth and writes it to a local SQLite database. Nothing is uploaded.
- **Account-free and local.** Noop never logs into a WHOOP account and never contacts a WHOOP server. It does not
  bypass any login, paywall or DRM; it talks to a device you own and reads data you generated.
- **Bring your history.** Import the WHOOP CSV export (and Health Connect data) once, and it is permanently on your
  phone. A `.noopbak` backup moves everything between installs.
- **One value per metric.** The metrics repository (metricsV2) gives every screen the same number for the same metric,
  so Today, Trends, Sleep and Insights never disagree.
- **Transparent math.** Charge, Effort, Rest, HRV and sleep are recomputed on-device from documented, citable methods
  (Task Force 1996 HRV, Karvonen %HRR, Edwards / Banister TRIMP, Tanaka HRmax, and so on). The algorithms are
  approximations of, not reproductions of, any proprietary model.

---

## Features

Ridge organises the app into **five tabs**: **Today**, **Trends**, **Baseline**, **Sleep** and **Hub**.

| Area | What it does |
|---|---|
| **Today** | The home dashboard: your **Charge**, **Effort** and **Rest** scores as living orbs, a plan for the day (including tonight's sleep target), key-metric tiles with sparklines, live strap battery and heart rate, and recent workouts. |
| **Trends** | Long-range trends across Charge, Effort, Rest, HRV, resting heart rate and more, with averages and tracked-day counts that match the underlying data. |
| **Baseline** | Your personal normal bands (mean ± 1 SD of the last 60 tracked nights) for resting heart rate, HRV, respiratory rate and skin temperature, so you can see when a night sits outside your usual range. |
| **Sleep** | Last night's Rest score, **Hours vs Needed**, the hypnogram and stage breakdown, a 14-night **sleep debt** chart, and a **Tonight** card with a recommended bedtime that **repays part of your sleep debt** (a quarter of it, up to an hour) and your wake time. Browse back through past nights. |
| **Hub** | Everything else: **Insights** (Key Metrics and the Explore metric explorer, with a day-by-day view of any metric), **Body**, **Your data** (imports, backup, and the **Fused** view showing every source's value and which one is used), **Sage**, **Devices**, **Settings** and **About**. |
| **Vital signs and Live** | Real-time heart rate from the strap, and a live workout session you can start and record. Calories for a recorded workout are estimated from heart rate, weight, age and sex. |
| **Breathe** | HRV haptic breathing biofeedback. The strap measures HRV and buzzes to pace your breath; each session reports a pre/post HRV outcome. |
| **Alarms** | Arms the strap's own alarm for your wake time, with an optional smart-wake window. |
| **Workouts** | Detected and manual sessions with an HR curve, time in each zone, average and maximum heart rate, calories and the Effort each one added. |
| **Sage** | An optional **AI coach** you can ask about your data in plain language. Off until you add your own key (Anthropic, OpenAI, Google Gemini, or any OpenAI-compatible endpoint, including a local model). It sends only a short text summary of recent metrics plus your question, never raw streams or identifiers. Disconnect the key and it is off. |
| **Backup & restore** | **Export backup** writes a `.noopbak` file (the whole database plus the settings that matter); **Import backup** restores it. **Export CSV** gives you the data as spreadsheets. |
| **What's new** | The in-app release notes, under **Hub › About**. |

Settings keeps language, clock format and units, the strap and its connection, notifications, Health Connect, Sage,
backup, and an opt-in **Experimental** section.

---

## Strap support

Noop is an independent, **experimental** project: capable, but a work in progress.

| Strap | Status |
|---|---|
| **WHOOP 4.0** | ✅ The tested, supported path. Live HR, Charge, Effort, Rest, sleep and history offload: the full experience. |
| **WHOOP 5.0 / MG** | 🧪 **Live heart rate works** (confirmed on real hardware). Pick "WHOOP 5.0 / MG" before connecting, and read the pairing note below, because you cannot just scan for it. Deeper 5/MG metrics are still being mapped; an opt-in **Settings → Experimental** toggle is there for owners who want to help document the protocol. |
| **Oura Ring (Gen 3 measured; Ring 5 reported)** | 🧪 **Experimental, not a supported strap.** Reads real overnight data: heart rate, sleep stages, skin temperature, SpO₂ and motion. A ring-only day gets a **Sleep** and a **Strain** score but **no Charge**. See **Oura ring support** below. |

> ### WHOOP 5.0 / MG analysis limits
>
> Noop's screens can only be as complete as the sensor inputs it can reliably decode. On WHOOP 5.0 / MG, important
> overnight inputs remain unavailable or incomplete:
>
> | Input / output | Current direct-from-strap status |
> |---|---|
> | Sleep duration / detection | Experimental; can fall back to heart rate when motion is sparse |
> | Sleep stages | Approximate, and not reliable while full overnight motion and cardiorespiratory inputs remain incomplete |
> | Skin temperature | Raw values decode on supported historical layouts; not available consistently across 5/MG firmware |
> | Blood oxygen / SpO₂ | Not recoverable offline from current time-multiplexed PPG data |
> | Overnight HRV and respiratory rate | Incomplete unless sufficient R-R intervals are captured |
>
> Seeing the Sleep, Baseline or Insights screens does not mean their deepest analysis is available from a WHOOP 5.0
> / MG alone yet. Scoring and correlations cannot conjure a measurement the strap has not given up. We would always
> rather tell you that straight.
>
> ### Pairing a WHOOP 5.0 / MG: read this first
>
> A WHOOP strap holds an encrypted Bluetooth **bond with only one device at a time**, and yours is normally bonded to
> the **official WHOOP app** on your phone. **You cannot just scan for it in Noop.** If the strap is still bonded to
> the WHOOP app, Noop's pairing is refused and the strap log shows *"Encryption is insufficient"* / *"bond
> refused."* (Live **heart rate** is the exception: it rides the standard Bluetooth heart-rate profile, so it streams
> without a bond. Pairing, needed for the deeper features, does not.)
>
> **To pair properly:**
> 1. **Close the official WHOOP app** on your phone (fully quit it, or turn that phone's Bluetooth off) so it is not
>    holding the bond.
> 2. **Put the strap in pairing mode.** On a 5.0/MG, **tap the band repeatedly** (firm taps on the sensor) until the
>    **LEDs flash blue**.
> 3. In Noop: **choose "WHOOP 5.0 / MG" → Scan & Connect.** Success looks like *"CLIENT_HELLO acked — link
>    established"* in the strap log (not *"bond refused"*). It can take a couple of attempts.
>
> **Only one device at a time.** Because the strap holds a single bond, do not leave it connected to two phones (or
> the WHOOP app) at once. Live heart rate will still show on all of them, but **none** of them will have the real
> encrypted bond. If HR streams fine yet **buzz, alarm and history don't work**, that is the tell: free the strap from
> everything else, then pair here.
>
> Bonding to Noop may take the strap's bond away from the WHOOP app, so the official app might need to re-pair
> afterwards. This is the **hardest part of 5/MG support**; if it refuses, you are almost certainly still bonded to
> the WHOOP app or another device.

The app always tells you what is live now versus still building, both in onboarding and on each screen.

### Oura ring support

Noop has **experimental, clean-room** support for the **Oura Ring**. It is not a supported strap and it is not on the
same footing as a WHOOP 4.0: it lives behind the experimental-device path in the pairing wizard, and parts of it are
permanently limited by what the ring will hand over. It reads real data from a ring you own, over Bluetooth, with no
Oura account and no Oura cloud.

**Which ring.** Every row below was measured on a **Gen 3**. A **Ring 5** has been reported working over the same path
(pairing/auth, live HR and inter-beat intervals, skin temperature, battery), marked *Ring 5 ✓*. Nothing has been run on
a **Ring 4** yet.

> **Not affiliated with Oura.** Independent interoperability work with hardware you own. "Oura" is used only to
> identify that hardware. Noop does not use, decompile or redistribute any Oura app code, and does not bypass any
> login, paywall or DRM.

| Input / output | Status |
|---|---|
| **Overnight heart rate** | ✅ Works. Reconstructed from the ring's banked inter-beat intervals; validated against a WHOOP strap worn the same night. *Ring 5 ✓* |
| **Sleep stages & timeline** | ✅ Works. Uses the **ring's own** hypnogram rather than re-staging it. |
| **Skin temperature** | ✅ Works. *Ring 5 ✓* |
| **Motion** | ✅ Stored. |
| **Live wear status** | ✅ Works. Noop can tell whether the ring is on your finger. |
| **Live heart rate** | 🟡 Partial. Only one of the ring's channels arrives near-live, and it is quality-filtered, so it does not tile continuously. *Ring 5 ✓* |
| **SpO₂** | 🟡 Mostly there. The ring's overnight SpO₂ percentage is decoded and stored. A nightly Blood Oxygen number is available behind a default-off display toggle in Settings and is still labelled a candidate. |
| **Charge / Effort on a ring-only day** | 🟡 **Sleep and Effort, not Charge.** Charge needs an HRV baseline, and the ring's banked intervals cannot give one, so no Charge is shown on a ring-only day. |
| **Step count** | 🚧 Estimated only, and not shown as a step count. The ring does not transmit a step total Noop can read. |
| **HRV (RMSSD / SDNN)** | ❌ **Not possible from this data.** The ring banks intervals in records rather than true beat-to-beat values, which inflates HRV spread beyond anything physiological. Noop **refuses** to show a number rather than a plausible-looking wrong one. |
| **Respiratory rate** | ❌ **Not possible from this data**, for the same reason: it is derived from beat timing, and the ring's banked intervals carry no breathing signal. |
| **Exercise / activity heart rate** | ❌ Server-gated by Oura's cloud; unreachable on a Noop-only pairing. |

### What to expect when you start

Noop computes your scores on your own phone, so like any recovery wearable it needs a little data before everything
fills in:

- **Live heart rate** shows the moment the strap connects.
- **Effort and sleep** appear after you have worn it and synced. The strap's last ~14 days offload automatically over
  the first few minutes.
- **Charge** needs a few nights for the app to learn your personal baseline, then sharpens each night.
- **In a hurry?** Import your WHOOP export under **Hub › Your data** and your history fills in about a minute.

---

## How your data flows

```
WHOOP strap ──BLE──▶ collect + offload ──▶ protocol decode (CRC-checked frames)
                                                    │
WHOOP CSV ─┐                                        ▼
Health     ├─▶ importers ───────────────▶ Room (local SQLite)
Connect   ─┘                                        │
                                                    ▼
                                        analytics (Charge, Effort, Rest,
                                        HRV, sleep, on-device)
                                                    │
                                                    ▼
                          metricsV2 registry ──▶ Ridge screens (Jetpack Compose)
```

Every arrow stays on your phone.

---

## Privacy

**Offline by default.** Noop has no server, no telemetry and no account. Your strap data, imports and computed metrics
live in a local SQLite database on your phone. They leave only through an export or an optional network feature you
deliberately configure, including the default-off Experimental one-way push to your own endpoint.

The app makes only these network requests, none carrying anything about you: the optional **Sage** AI coach (off until
you add your own key), and a once-a-day read of the latest release number so a sideloaded install can tell you an
update exists. That check is on by default, can be switched off in **Hub › About**, shows new versions in the
notifications menu, and never installs anything. Network access is the only reason the app holds the `INTERNET`
permission.

---

## Attribution

Noop Ridge is built on **Noop** by **ryanbr** ([github.com/ryanbr/noop](https://github.com/ryanbr/noop)), the original
project, whose protocol work, analytics and Android app it extends with the Ridge design and metricsV2. Noop in turn
stands on community interoperability and protocol-documentation work. With thanks:

- **`ryanbr/noop`**: the original Noop, the base this build is derived from.
- **`johnmiddleton12/my-whoop`**: the WHOOP 4.0 BLE protocol; the collection logic is adapted from this work.
- **`b-nnett/goose`**: the WHOOP 5.0 / MG BLE protocol documentation (the `fd4b0001-…` service family, CRC16-Modbus
  header and "puffin" packet types) that Noop's WHOOP 5.0 path is ported from.

Noop contains no WHOOP proprietary code, firmware, logos or assets, and performs no DRM circumvention. Full detail in
[`ATTRIBUTION.md`](ATTRIBUTION.md).

---

## Disclaimer

Noop is an independent, unofficial, non-commercial interoperability project. It is **not affiliated with, endorsed by,
or connected to WHOOP, Inc.** All references to "WHOOP" are nominative, used only to identify the third-party hardware
Noop interoperates with.

**Noop is not a medical device.** Heart rate, HRV, Charge, Effort, sleep stages, SpO₂, respiratory rate and skin
temperature are **approximations** computed from published methods. They are not clinically validated and are not
medical advice. Do not use them to diagnose, treat or make health decisions; consult a qualified professional.

Provided **as-is**, with **no warranty**, for **personal and educational use**. You use it at your own risk. Read the
full notice in [`DISCLAIMER.md`](DISCLAIMER.md).

---

## License

Noop is **source-available** under the [PolyForm Noncommercial License 1.0.0](LICENSE): **free for personal and other
non-commercial use**. Read it, run it, fork it, and contribute. Commercial use is not granted by this license. (PolyForm
Noncommercial is a proper software license with patent terms; it is deliberately *not* an OSI "open-source" licence,
because that would permit the commercial use this project's non-commercial nature rules out.)

The license covers Noop's own original code and docs. Protocol facts (frame layouts, command numbers, byte offsets) are
uncopyrightable and free to reuse; bundled dependencies keep their own licenses. By opening a pull request you agree
your contribution is licensed under the same terms; see [`CONTRIBUTING.md`](CONTRIBUTING.md).

Keep the [`LICENSE`](LICENSE) and `Copyright 2026 NoopApp` notice intact if you mirror or fork it, and point people
back to the canonical homes: [github.com/eccentricmeerkat/noopridge](https://github.com/eccentricmeerkat/noopridge) for
Ridge and [github.com/ryanbr/noop](https://github.com/ryanbr/noop) for the original.

---

## Docs

- [`android/README.md`](android/README.md): build, run, test and sign the Android app.
- [`CHANGELOG.md`](CHANGELOG.md): release history (the current release's notes are also in-app under **What's new**).
- [`DISCLAIMER.md`](DISCLAIMER.md): trademark, interoperability, and medical/legal notice.
- [`ATTRIBUTION.md`](ATTRIBUTION.md): full credits and licensing notes.
- [`CONTRIBUTING.md`](CONTRIBUTING.md), [`SECURITY.md`](SECURITY.md), [`SUPPORT.md`](SUPPORT.md), [`TERMS.md`](TERMS.md).
- The BLE protocol write-ups live in the original project: [`docs/PROTOCOL.md`](https://github.com/ryanbr/noop/blob/main/docs/PROTOCOL.md).
