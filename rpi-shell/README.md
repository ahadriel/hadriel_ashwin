# PiShell

A lightweight, colorful launcher desktop for Raspberry Pi OS (64-bit ARM first).

PiShell is designed for a Raspberry Pi 4B that is also running server workloads. It is a small GTK 3 application, not a new Linux distribution and not a heavyweight desktop environment. It provides a full-screen home surface with service shortcuts, a clock, and basic system telemetry.

## Design goals

- Keep the existing Raspberry Pi OS installation and services intact.
- Avoid Electron, Chromium kiosk shells, GNOME, KDE, and background daemons.
- Use the system Python + GTK 3 bindings; no pip packages or bundled runtime.
- Make service URLs editable in `~/.config/pishell/config.json`.
- Ship a colorful, original landscape wallpaper (not a spiral/rainbow pattern).
- Make startup opt-in and easy to reverse.

## Current status

Early prototype. The installer adds GTK 3 runtime bindings if missing and registers PiShell in the current desktop user's autostart. It does **not** remove the existing desktop, alter networking, or touch HyperHDR/Twingate/CasaOS data.

## Install on the Raspberry Pi 4B

Use the 64-bit Raspberry Pi OS desktop session:

```bash
git clone --branch feature/rpi-lightweight-shell https://github.com/ahadriel/hadriel_ashwin.git
cd hadriel_ashwin/rpi-shell
bash install.sh
```

Log out and back in, or launch `PiShell` from the app menu. The first launch opens the config file location if you want to change service addresses.

## Default service shortcuts

- CasaOS: `http://localhost` (change this if CasaOS is on another port/IP)
- HyperHDR: `http://localhost:8090`
- Downtify: starts unconfigured until its URL is entered in the config
- Twingate: opens the local Twingate client if installed; otherwise opens its website
- JARVIS: reserved shortcut for the future local assistant

Edit `~/.config/pishell/config.json` to change names, URLs, and commands.

## Performance notes

PiShell is intentionally simple, but no UI can guarantee a specific CPU or thermal improvement without measuring the exact Pi OS image, compositor, display resolution, and running services. This prototype does not install a new compositor or disable the current panel automatically. A later opt-in "lean session" can be benchmarked before replacing any existing desktop components.

## Uninstall

```bash
~/.local/share/pishell/uninstall.sh
```
