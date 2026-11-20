# PiShell

A lightweight GTK 3 desktop home surface and application launcher, developed on Linux Mint and Raspberry Pi OS.

## What this build includes

- Fullscreen home surface with a scenic colour-rich wallpaper
- Top status bar and live clock
- CPU temperature (when exposed by the host), memory use and system load
- Home, application launcher and service-library views
- Search across installed desktop applications using the system's application metadata
- Floating quick-launch dock for Files, Terminal, Browser and the app launcher
- Quick-settings information for Wi-Fi radio and audio volume (actual controls remain with the desktop's system tray)
- Configurable CasaOS, HyperHDR, Downtify, Twingate and future JARVIS shortcuts

PiShell is a GTK application layered over the existing desktop session. It does not replace the window manager, panel, compositor, network manager or audio stack. This keeps the first iteration reversible and avoids disrupting server services.

## Install

On Raspberry Pi OS, Debian, Ubuntu or Linux Mint:

```bash
git clone --branch feature/rpi-lightweight-shell https://github.com/ahadriel/hadriel_ashwin.git
cd hadriel_ashwin/rpi-shell
bash install.sh
```

The installer uses apt and system GTK 3 bindings. It supports ARM and x86-64 for development/testing. Run `pishell` to launch manually. The installer registers autostart; remove `~/.config/autostart/pishell.desktop` to disable it.

## Configure services

Edit `~/.config/pishell/config.json`. Each service can use a URL, a local command or a disabled placeholder. Update default URLs to match your installation; ports are not auto-detected.

## Keyboard

- `Esc`: exit PiShell
- `F1`: Home
- `F2`: Application launcher

## Uninstall

```bash
~/.local/share/pishell/uninstall.sh
```

Configuration is preserved by the uninstaller.

## Performance and limitations

This is an early shell prototype, not a complete standalone desktop environment. It intentionally keeps the existing desktop infrastructure. No CPU, RAM or thermal improvement is claimed until measured on the actual Raspberry Pi with the same workload and display settings. Some telemetry (such as CPU temperature) depends on hardware-provided Linux interfaces.
