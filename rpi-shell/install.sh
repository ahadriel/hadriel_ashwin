#!/usr/bin/env bash
set -euo pipefail

APP_NAME="PiShell"
SOURCE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="${HOME}/.local/share/pishell"
BIN_DIR="${HOME}/.local/bin"
CONFIG_DIR="${HOME}/.config/pishell"
AUTOSTART_DIR="${HOME}/.config/autostart"

if [[ "$(uname -m)" != "aarch64" && "$(uname -m)" != "armv7l" && "$(uname -m)" != "armv6l" ]]; then
  echo "PiShell targets Raspberry Pi ARM systems. Detected: $(uname -m)"
  echo "Continuing is possible for development, but this is not the intended target."
fi

if ! command -v apt-get >/dev/null 2>&1; then
  echo "This installer currently supports Raspberry Pi OS / Debian with apt."
  exit 1
fi

echo "[1/4] Installing the small GTK 3 runtime dependency..."
sudo apt-get update
sudo apt-get install -y --no-install-recommends python3 python3-gi gir1.2-gtk-3.0

echo "[2/4] Installing PiShell into ${INSTALL_DIR}..."
mkdir -p "${INSTALL_DIR}" "${BIN_DIR}" "${CONFIG_DIR}" "${AUTOSTART_DIR}"
cp -a "${SOURCE_DIR}/app/." "${INSTALL_DIR}/"
cp -a "${SOURCE_DIR}/assets" "${INSTALL_DIR}/assets"
install -m 0755 "${SOURCE_DIR}/uninstall.sh" "${INSTALL_DIR}/uninstall.sh"

cat > "${BIN_DIR}/pishell" <<'LAUNCHER'
#!/usr/bin/env bash
exec /usr/bin/python3 "${HOME}/.local/share/pishell/main.py" "$@"
LAUNCHER
chmod 0755 "${BIN_DIR}/pishell"

if [[ ! -f "${CONFIG_DIR}/config.json" ]]; then
  install -m 0644 "${SOURCE_DIR}/config.default.json" "${CONFIG_DIR}/config.json"
fi

echo "[3/4] Registering desktop entry and opt-in autostart..."
cat > "${HOME}/.local/share/applications/pishell.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=PiShell
Comment=Lightweight service launcher for Raspberry Pi
Exec=${BIN_DIR}/pishell
Icon=preferences-desktop
Terminal=false
Categories=System;Utility;
EOF

cat > "${AUTOSTART_DIR}/pishell.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=PiShell
Exec=${BIN_DIR}/pishell
X-GNOME-Autostart-enabled=true
EOF

echo "[4/4] Done."
echo "Log out and back in, or run: ${BIN_DIR}/pishell"
echo "Config: ${CONFIG_DIR}/config.json"
echo "To uninstall: ${INSTALL_DIR}/uninstall.sh"
