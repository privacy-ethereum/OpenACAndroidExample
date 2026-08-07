# OpenAC Taiwan Citizen Digital Certificate Android Example

<p align="center">
<a href="https://drive.google.com/file/d/15ukmBzA5Ih1SFu0uuf1LursOIYai7ooU/view?usp=sharing"><img src="https://i.imgur.com/kct14sr.png"
alt="Demo of the Nextcloud Android files app"
height="40"></a>
</p>

An Android example app that integrates Taiwan's Citizen Digital Certificate ([TW FidO](https://fido.moi.gov.tw/pt/)) with [openac-rsa-x509-kotlin](https://github.com/privacy-ethereum/openac-rsa-x509-kotlin) to generate a zero-knowledge proof from the certificate signature and send it to a server for verification.

## Demo

|                    Download Circuit                     |                    TW FidO Signature                    |
| :-----------------------------------------------------: | :-----------------------------------------------------: |
| ![Download Circuit](images/openac-android-download.gif) | ![TW FidO Signature](images/openac-android-tw-fido.gif) |
|                      \~ 12 seconds                      |                       \~9 seconds                       |

|                   Generate Proof                   |                   Verify Proof                    |
| :------------------------------------------------: | :-----------------------------------------------: |
| ![Generate Proof](images/openac-android-prove.gif) | ![Verify Proof](images/openac-android-verify.gif) |
|                    \~ 6 seconds                    |                   \~ 10 seconds                   |

### Circuit Download Card

Always visible. Shows a live progress bar and percentage while fetching. The MOICA and ZK Pipeline cards are hidden until the circuit keys and SMT snapshot are ready.

- **Download Circuit + Keys** — fetches and decompresses `cert_chain_rs4096_proving.key`, `user_sig_rs2048_proving.key`, and `g3-tree-snapshot.json.gz` from their CDNs; shows total download time on completion

### MOICA Signature Card _(visible after circuit + keys are ready)_

Enter a masked **ID Number**, then follow the four numbered steps:

- **0. Get TBS Challenge** — POSTs to the challenge server and stores the challenge bytes (`tbs`) and challenge ID
- **1. Get SP Ticket** — calls `getSpTicket` with the TBS as `signData`; enabled after a challenge is received
- **2. Open MOICA App** — launches the MOICA app via deep-link for the user to sign (app-to-app flow); enabled after an SP ticket is obtained
- **3. Poll ATH Result** — calls `getAthOrSignResult` and displays the signed response and cert snippets; enabled after an SP ticket is obtained

### circuit*input.json Card *(visible after Generate Input completes)\_

Expandable card showing the generated circuit input JSON, with a copy-to-clipboard button.

### ZK Pipeline Card _(visible after circuit + keys are ready)_

- **4. Generate Input** — calls `generateCertChainRs4096Input` to produce the circuit input from the ATH result and SMT snapshot; enabled after ATH polling succeeds
- **5. Generate Proof** — calls `proveCertChainRs4096` and `proveUserSigRs2048` and reports total proof time (ms)
- **6. Verify Proof** — downloads verifying keys on demand, then POSTs the proof binaries to the link-verify server endpoint; enabled after prove succeeds
- **Run All (Prove → Verify)** — convenience button that runs steps 5–6 in sequence (Generate Input must be run separately first)

## Getting Started

Clone the repo and open it in Android Studio.

```bash
git clone https://github.com/privacy-ethereum/openac-taiwan-citizen-digital-certificate-android-example
```

### 1. Start the verifier server

Clone and run [go-zkid-verifier](https://github.com/privacy-ethereum/go-zkid-verifier), then expose it via [ngrok](https://ngrok.com):

```bash
# In the go-zkid-verifier directory
make download-keys
make build
make serve
```

```bash
# In a separate terminal
ngrok http 8080
```

ngrok will print a public URL like `https://b33f-54-237-15-198.ngrok-free.app`. Copy it.

### 2. Update the server URLs in the app

Open `app/src/main/java/com/example/openacandroidexample/ProofViewModel.kt` and replace the two URL constants near the top of the file with your ngrok URL:

```kotlin
private const val SERVER_URL      = "https://<your-subdomain>.ngrok-free.app/challenge"
private const val LINK_VERIFY_URL = "https://<your-subdomain>.ngrok-free.app/link-verify"
```

### 3. Configuration — `Secrets.kt`

The app requires TW FidO SP service credentials. Apply for these as an SP (Service Provider) at [https://fido.moi.gov.tw/pt/](https://fido.moi.gov.tw/pt/), then create or update the file below **before building** (it is git-ignored):

```
app/src/main/java/com/example/openacandroidexample/Secrets.kt
```

```kotlin
package com.example.openacandroidexample

object Secrets {
    const val fidoSpServiceID: String = "your-sp-service-id"
    const val fidoAESKey: String = "your-32-byte-aes-key-base64"
}
```

| Constant          | Description                                                                        |
| ----------------- | ---------------------------------------------------------------------------------- |
| `fidoSpServiceID` | SP service ID issued by MOICA for TW FidO                                          |
| `fidoAESKey`      | 32-byte AES-256 key (base64-encoded) used to compute `sp_checksum` via AES-256-GCM |

Credentials can also be supplied at test time via environment variables `FIDO_SP_SERVICE_ID` and `FIDO_AES_KEY`; the app falls back to `Secrets.kt` if those are absent.

### 4. Build and run

1. Open the project in Android Studio (or run `open . -a Android\ Studio` from the project root).
2. Select a physical Android device and ensure the [TW FidO app (行動自然人憑證)](https://play.google.com/store/apps/details?id=tw.gov.moi.moica.mobile) is installed.
3. Build and run.
4. On first launch, tap **Download Circuit + Keys** and wait for all files to download.
5. Tap **0. Get TBS Challenge** to fetch a challenge from the server.
6. Enter your ID number (身分證字號), then follow the MOICA steps in order.
7. Tap **Run All (Prove → Verify)** (or individual step buttons) to generate and verify the ZK proofs.
                                            |

## See also

- [openac-rsa-x509-kotlin](https://github.com/privacy-ethereum/openac-rsa-x509-kotlin) — Kotlin bindings for the OpenAC proving backend (`generateCertChainRs4096Input`, `proveCertChainRs4096`, `proveUserSigRs2048`)
- [zkID releases](https://github.com/privacy-ethereum/zkID/releases/tag/RSA-X.509-Cert-latest) — circuit and key files
- [moica-revocation-smt](https://github.com/privacy-ethereum/moica-revocation-smt) — SMT snapshot releases


## Community

- X account: <a href="https://twitter.com/zkmopro"><img src="https://img.shields.io/twitter/follow/zkmopro?style=flat-square&logo=x&label=zkmopro"></a>
- Telegram group: <a href="https://t.me/zkmopro"><img src="https://img.shields.io/badge/telegram-@zkmopro-blue.svg?style=flat-square&logo=telegram"></a>

## Acknowledgements

It is currently incubated by [PSE](https://pse.dev/).

## License

Licensed under either of

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE) or http://www.apache.org/licenses/LICENSE-2.0)
- MIT license ([LICENSE-MIT](LICENSE-MIT) or http://opensource.org/licenses/MIT)

at your option.
