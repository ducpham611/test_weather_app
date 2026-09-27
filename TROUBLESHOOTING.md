# "No internet connection" when the device is online

## What was wrong in the first build

Two bugs in my code, both fixed in this version.

**1. Every network error was labelled "no internet".**
The ViewModel mapped *any* `IOException` to `NO_NETWORK`. A failed TLS
handshake, a DNS failure and a blocked port all throw `IOException`, so the app
reported "no connection" no matter what actually broke. The message was wrong,
not the diagnosis.

**2. The TLS setup was too strict.**
The client was pinned to `MODERN_TLS` with only TLS 1.2 and 1.3:

```kotlin
ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
```

That restricts both the protocol **and** the cipher suite list. If Conscrypt
fails to load, or the network runs through an intercepting proxy, no handshake
is possible and every request dies before it leaves the phone. Other apps kept
working because they accept a wider range.

There was also a third, smaller problem: the app refused to even try a refresh
when `ConnectivityManager` reported offline. That flag is unreliable on lab
devices and captive networks.

## What changed

| Fix | Effect |
|---|---|
| `MODERN_TLS` **+** `COMPATIBLE_TLS` | Works through proxies and older TLS stacks |
| Explicit TLS 1.2 socket factory on API 21 | TLS 1.2 exists on Android 5.0 but ships disabled |
| Errors classified by exception type | DNS, TLS, timeout and server errors now read differently |
| Connectivity pre-check removed | The app always tries, instead of guessing |
| Retry interceptor rewritten | It swallowed the original exception; now it rethrows it |
| Removed manual `Accept-Encoding: gzip` | It disabled OkHttp's automatic decompression |
| Debug logging of every request | `adb logcat -s Network` shows the real failure |

## How to find the real cause on the device

### Option 1 — the built-in self-test (no cable needed)

**Settings → Connection diagnostics** (Chẩn đoán kết nối).

It reports, separately:
- device clock,
- whether Conscrypt loaded,
- any system proxy,
- a raw DNS lookup of `api.open-meteo.com`,
- a real HTTPS request, with the exact exception if it fails.

There is a **Copy** button so you can paste the report.

You can also long-press the "Cập nhật lúc…" timestamp on the main screen to see
the last raw error.

### Option 2 — logcat

```
adb logcat -s Network:V WeatherApp:V
```

## Reading the result

| Diagnostics says | Meaning | Fix |
|---|---|---|
| DNS **FAILED**, `UnknownHostException` | No real internet, or the network needs a proxy | Check the lab's network settings |
| HTTPS **FAILED**, `SSLHandshakeException` | Certificate rejected | Check the device date/time first |
| HTTPS **FAILED**, `SSLPeerUnverifiedException` | An intercepting proxy is replacing certificates | Trust the proxy CA, or test elsewhere |
| HTTPS **FAILED**, `SocketTimeoutException` | Blocked by a firewall, or very slow | Try another network |
| Conscrypt **NOT installed** | Native library missing for this ABI | Build a universal APK, not a split |

## About Samsung Remote Test Lab specifically

RTL devices sit behind Samsung's own network, and this is worth knowing:

- **Outbound access is restricted.** RTL is meant for UI testing, and traffic to
  arbitrary hosts is often blocked or forced through a proxy. Browsers and
  preinstalled apps may be whitelisted while `api.open-meteo.com` is not.
- **The clock is frequently wrong**, which breaks certificate validation.
- **GPS is not real**, so "use my location" will not behave normally. Test with
  a city or district chosen by hand instead.

So run the diagnostics first. If DNS fails, or you get an SSL error on a device
whose clock is wrong, the lab network is the cause and there is nothing to fix
in the app. Confirm by testing the same APK on a normal phone on Wi-Fi or
mobile data.
