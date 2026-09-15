# FinTracker sync server on a Raspberry Pi

How to set the server up on a **fresh** Pi without remembering Phase 7. Written from what was actually done on 2026-09-07 to 09 (#93, #94, #96), not from the plan.

The test for this document is not completeness. It is **repeatability**.

## Placeholders

Real values are not in here. The repository may become public one day, and what once enters the history only leaves it by rewriting all of it (#85).

| Placeholder | Where the real value lives |
|---|---|
| `<pi-user>` | Chosen while writing the card |
| `<pi-name>` | Hostname, also chosen while writing the card (a telling name, not `raspberrypi`) |
| `<tailscale-address>` | `tailscale ip -4` on the Pi, of the form `100.x.y.z` |
| `<stick-uuid>` | `sudo blkid` — tied to that one stick; determine it again on new hardware |
| `<api-key>` | `/etc/fintracker/server.env` on the Pi. **Never write it anywhere else** |

---

## What you need

- A Raspberry Pi 5 (4 GB is enough; 2.8 GB measured free in operation) with a case
- An SD card for the system and **a USB stick for the database** — the separation is deliberate, see part C
- A Tailscale account, signed in through an identity provider **that has two-factor authentication enabled**. The VPN is exactly as strong as that account; this is the largest real risk of the whole arrangement.
- Cooling: the official case with its fan and stick-on heatsink is enough. Measured 48.8–51.0 °C at `throttled=0x0` — no Active Cooler needed.

---

## A — Write the card

Raspberry Pi Imager, **Raspberry Pi OS Lite (64-bit)**. Set in the gear dialog beforehand: hostname, username, password, Wi-Fi with country, timezone, and **enable SSH**. Without that last one there is no way onto a Pi that has no screen.

**Check carefully that Lite is really selected.** The first attempt wrote the Desktop edition by mistake, and it only showed up in the upgrade list (Chromium, Firefox, X server). Repair without starting over:

```bash
sudo systemctl set-default multi-user.target
```

Three traps when writing the card from Windows, all of which have sprung at least once:

- **"You need to format the disk" refers to the ext4 partition and is a *success* signal.** Agreeing destroys the image.
- **`bootfs` sometimes gets no drive letter.** The letter that looks taken may be an empty card reader slot of size 0. Assign one through `diskmgmt.msc`.
- **Current Pi OS configures itself through `cloud-init`**, no longer through `custom.toml` or `firstrun.sh`. What sits on `bootfs` is `user-data`, `network-config` and `meta-data`. Looking for the old filenames makes a correctly written card look broken.

The **first boot takes three to four minutes** and runs in stages. That is normal.

```bash
ssh <pi-user>@<pi-name>.local
```

---

## B — Base setup

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y openjdk-21-jre-headless sqlite3
java -version
```

**Why this JVM:**

- **JRE, not JDK** — nothing is built on the Pi.
- **headless** — the machine has no screen.
- **21** — Debian 13 no longer carries OpenJDK 17, only 21 and 25. A third-party Temurin 17 would have matched the CI version of the time, but at the price of a hand-maintained package source on a machine that runs unattended. The CI was raised to 21 instead (#106), so what is tested is what runs.

Cap the journal so it cannot fill the card:

```bash
sudo mkdir -p /etc/systemd/journald.conf.d && printf '[Journal]\nSystemMaxUse=50M\n' | sudo tee /etc/systemd/journald.conf.d/50-size.conf && sudo systemctl restart systemd-journald
```

`log2ram` was considered and **dropped again**: it was meant to help against a flood of log lines that has not existed since #105. A third-party source for a problem that is already solved is not worth it.

---

## C — Storage for the database

The database does **not** belong on the system card. The reason is not only wear but **separation**: the heavily written medium (system, logs) then holds nothing valuable, and the medium holding the data is only touched during a sync. If the card dies, the database survives and moves to a freshly built Pi.

**Do not leave the stick on its factory format.** Sticks arrive as FAT32 or exFAT, where SQLite cannot set its file locks reliably — the kind of fault that costs data quietly.

```bash
lsblk                                  # find the device, e.g. /dev/sda1
sudo mkfs.ext4 -L fintracker /dev/sda1
sudo blkid /dev/sda1                   # read the UUID
sudo mkdir -p /srv/fintracker
```

The line for `/etc/fstab`:

```
UUID=<stick-uuid> /srv/fintracker ext4 defaults,noatime,nofail 0 2
```

```bash
sudo mount -a && findmnt /srv/fintracker
```

`noatime` saves writes; `nofail` keeps the Pi from refusing to boot without the stick. **`nofail` is also what creates the hazard that `RequiresMountsFor` in part F guards against** — read the two together.

---

## D — Deploy the application

On the development machine:

```bash
./gradlew :server:installDist
scp -r server/build/install/server <pi-user>@<pi-name>.local:/tmp/fintracker-new
```

No fat JAR is needed; `:server` uses the `application` plugin. On Windows use **relative paths** — `scp` reads the colon in `C:/…` as a host separator.

On the Pi:

```bash
sudo systemctl stop fintracker 2>/dev/null
sudo rm -rf /opt/fintracker.old
sudo mv /opt/fintracker /opt/fintracker.old 2>/dev/null
sudo mv /tmp/fintracker-new /opt/fintracker
sudo chown -R root:root /opt/fintracker
sudo chmod -R a+rX /opt/fintracker
sudo chmod a+x /opt/fintracker/bin/server
```

**The permission lines are not optional.** `scp` from Windows arrives with directories at `drwx------`. The service then fails with `status=203/EXEC` and `Permission denied` even though the start script itself is executable and starting it by hand works — the service user cannot enter the directory at all. **Capital `X` in `a+rX`**, or every JAR gets marked executable.

Ownership is `root`, not the service user: the service must not be able to overwrite its own program files.

The previous directory stays behind as `/opt/fintracker.old` — two `mv` commands get you back after a failed start.

---

## E — Configuration and key

```bash
sudo useradd --system --home /home/fintracker --shell /usr/sbin/nologin fintracker
sudo chown -R fintracker:fintracker /srv/fintracker
sudo mkdir -p /etc/fintracker
```

Generate the key **straight into the file**, so it never appears on a screen and never enters shell history:

```bash
sudo sh -c 'umask 077; { echo "FINTRACKER_API_KEY=$(openssl rand -hex 32)"; echo "FINTRACKER_SERVER_DB=/srv/fintracker/fintracker.db"; echo "FINTRACKER_BIND_HOST=<tailscale-address>"; } > /etc/fintracker/server.env'
sudo chmod 600 /etc/fintracker/server.env
sudo chown root:root /etc/fintracker/server.env
```

Fill in `FINTRACKER_BIND_HOST` only once part G is done and the address exists. Without the variable the server listens on `127.0.0.1` — safe, but unreachable for the clients.

Three things the server refuses, each on purpose:

| Condition | Behaviour |
|---|---|
| `FINTRACKER_API_KEY` missing | start aborts (#89) |
| `FINTRACKER_BIND_HOST=0.0.0.0` | start aborts (#94) |
| database corrupted | start aborts (#96) |

Each of these would otherwise keep running while looking entirely normal. **An aborted start here is not a fault in this guide; it is the built-in warning.**

---

## F — The service

All unit files live in the repository under `server/deploy/`, so they can be
installed rather than typed:

```bash
sudo install -m 644 fintracker.service /etc/systemd/system/
sudo mkdir -p /etc/systemd/system/fintracker.service.d
sudo install -m 644 10-tailscale.conf 20-sigterm.conf 30-startlimit.conf /etc/systemd/system/fintracker.service.d/
```

Each file carries its reasoning in its own header. The three points worth
knowing before you run the above:

**`RequiresMountsFor=/srv/fintracker` is the most important line.** Without it, a missing stick — unplugged, dead, waved through by `nofail` — would leave the server looking at an empty directory, where it would create a new, empty database. Phone and desktop would then merge against that. A service that does not run is a visible fault; one with an empty database is an invisible one.

The additions are drop-ins rather than edits to the unit on purpose, so it stays visible what was added when and why. Each exists because something went wrong once:

| File | Contents | What for |
|---|---|---|
| `10-tailscale.conf` | `After=`/`Wants=tailscaled.service` | Otherwise the service binds, after a boot, to an address that does not exist yet |
| `20-sigterm.conf` | `SuccessExitStatus=143` | Without it every intended stop is logged as `Failed` — a clean stop would read like a crash |
| `30-startlimit.conf` | `StartLimitIntervalSec=300`, `StartLimitBurst=5` | Without it a service that rightly refuses to start restarts **forever** |

On that third point, the arithmetic that is easy to miss: systemd only breaks a restart loop when **five starts fall inside ten seconds**. With `RestartSec=5` plus JVM startup, one cycle is longer than the window, so the condition is never met. Measured: `NRestarts=16` and still climbing, state `activating`, writing to the SD card the whole time.

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now fintracker && systemctl status fintracker --no-pager
```

---

## G — Access through Tailscale

No open port, no port forwarding, no DynDNS. Forwarding a port would put the server on the open internet, guarded by nothing but an API key in front of hand-written code holding financial data. Tailscale takes it out of the public network entirely. Accepted knowingly: a third party is now part of the connection. That is also what makes running without HTTPS defensible — the traffic goes through the encrypted VPN.

```bash
curl -fsSL https://pkgs.tailscale.com/stable/debian/trixie.noarmor.gpg | sudo tee /usr/share/keyrings/tailscale-archive-keyring.gpg >/dev/null
curl -fsSL https://pkgs.tailscale.com/stable/debian/trixie.tailscale-keyring.list | sudo tee /etc/apt/sources.list.d/tailscale.list
sudo apt update && sudo apt install -y tailscale
sudo tailscale up          # prints a URL; sign in with it in a browser
tailscale ip -4            # this is <tailscale-address>
```

If Tailscale does not carry the Debian release yet, use the previous one in both URLs — it works fine.

**Then, in the admin console, and this is not optional:**

- **Disable key expiry for the Pi.** The 180-day default is right for clients — a lost phone drops out of the tailnet by itself. For a headless machine that runs around the clock it means the sync stops working in half a year and **nothing says so**.
- Check device approval. Not included in the free plan; the substitute is keeping the device list short and known.

Then put `FINTRACKER_BIND_HOST` into `/etc/fintracker/server.env` and restart the service.

**The counter-check is the actual proof** — the server has to be unreachable from your own home network **without** the VPN:

```bash
curl --max-time 5 http://<tailscale-address>:8080/health   # OK
curl --max-time 5 http://<pi-local-ip>:8080/health         # must fail
```

The second call has to end in a refused connection, **not** in a response with a status code. That `/health` answers without a key is built exactly for this: a `401` would mean a connection was made, which would leave the result ambiguous.

The strongest version of this test runs from **another machine on the home network that does not have Tailscale installed yet**. That opportunity exists only once — before you install it there.

---

## H — Backups

Why at all, when both clients hold complete copies: the clients can restore the server — **but nothing can restore the clients once a corrupted server has poisoned them during a sync.** The merge decides by timestamp and cannot tell good data from damaged data.

From the repository, `server/deploy/`:

```bash
sudo install -m 755 fintracker-backup.sh /usr/local/bin/fintracker-backup
sudo install -m 644 fintracker-backup.service fintracker-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo /usr/local/bin/fintracker-backup          # first run by hand
sudo systemctl enable --now fintracker-backup.timer
systemctl list-timers fintracker-backup.timer --no-pager
```

The run by hand matters: its output names the path, the `integrity_check` **and the contents** (`Konten=… Buchungen=…`). That last part because `integrity_check ok` is a statement about file structure — **a completely empty database answers it just as happily**.

**Deliberately not covered:** total loss of the Pi. An off-site copy would be another complete set of account data requiring the same protection as the original (#85) — a decision of its own.

---

## I — Set up the clients

In **desktop** (Settings → Synchronisation → Server-Sync) and on **Android**:

- Server address: `<tailscale-address>` — scheme and `:8080` are filled in for you
- API key: from `/etc/fintracker/server.env`, straight into the field, not by way of anything else

Then **test the connection**. The test runs in two stages — reachability first, then the key — so the message tells you which of the two questions is open. **Saving is a separate step**; until you save, the sync keeps using the previous values.

**For the very first sync, start with the device that holds the most current data.** And back up both client databases before it.

On **Android**, note that only **one** VPN connection can be active at a time. While Tailscale is connected, other VPN-based services (tracking protection apps, for instance) are switched off. If you want to keep those, connect Tailscale only for a sync — after which "VPN not connected" becomes the most common reason for "server unreachable" (#109).

---

## Checking that it works

```bash
systemctl is-active fintracker
sudo ss -tlnp | grep 8080                        # must show <tailscale-address>:8080
curl --max-time 5 http://<tailscale-address>:8080/health
```

**The real acceptance test is an actual `reboot`, not a `systemctl restart`:**

```bash
sudo reboot
# after about a minute:
systemctl show fintracker -p NRestarts
systemctl is-active fintracker
```

**`NRestarts=0` is the number that matters.** Anything else means the service failed on its first attempt and `Restart=on-failure` caught it — everything then *looks* healthy while the startup ordering is in fact wrong.

### Failure modes

| What you see | What it is |
|---|---|
| `status=203/EXEC`, `Permission denied` | Permissions from part D not applied — `scp` from Windows |
| Start aborts with a message about the API key | `FINTRACKER_API_KEY` missing or empty (#89) |
| Start aborts, message mentions every interface | `FINTRACKER_BIND_HOST=0.0.0.0` (#94) |
| Start aborts, message names a corrupted database | `PRAGMA integrity_check` fired — **restore a backup** (#96) |
| Service does not start at all, message about the mount | Stick not mounted; `RequiresMountsFor` did its job. **This is the good case** |
| State stays `activating`, `NRestarts` climbing | `30-startlimit.conf` is missing |
| Log full of `DEBUG io.netty` | Old JAR without `logback.xml` (#105) |
| Client reports "no answer" | First check whether the VPN is connected **on that device** |

---

## Rotating the API key

Needed as soon as the key has been anywhere it does not belong — on a screen, in a chat log, in a file outside the Pi.

```bash
NEU=$(openssl rand -hex 32); sudo sed -i "s|^FINTRACKER_API_KEY=.*|FINTRACKER_API_KEY=$NEU|" /etc/fintracker/server.env; sudo systemctl restart fintracker; echo "$NEU"
```

Type the printed value straight into both apps and **copy it nowhere else**. From the restart onwards every route except `/health` answers the old key with `401` — which is why the first stage of the connection test stays green and the second one tells you where you stand.

---

## Recovery

**A lost server is not an emergency.** Both clients hold complete copies, and deletions travel as tombstones rather than as gaps. Verified on 2026-09-09: an empty server filled itself completely from a single client push, tombstones included.

Two routes, and the difference matters:

1. **Restore the file** (preferred): stop the service, copy a backup from `/var/backups/fintracker/` to `/srv/fintracker/fintracker.db`, set ownership to `fintracker`, start the service.
2. **Refill from a client**: empty database, then sync from a client.

Route 2 triggers a known side effect (#108): the freshly created server writes its **default categories with a current timestamp**, and those win against the client's versions. Harmless as long as nobody has renamed a default category — otherwise the rename is reverted on every device. **So prefer route 1** as long as a backup exists.

---

## What this guide does not cover

- **An off-site copy.** See part H.
- **A push interrupted midway** over a flaky connection. `SyncWriter` writes in one transaction, so an aborted push should leave the database unchanged — but that is not demonstrated.
- **Multiple users.** The server knows exactly one dataset and has no user management.
