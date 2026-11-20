#!/usr/bin/env bash
set -euo pipefail
rm -f "${HOME}/.config/autostart/pishell.desktop"
rm -f "${HOME}/.local/share/applications/pishell.desktop"
rm -f "${HOME}/.local/bin/pishell"
rm -rf "${HOME}/.local/share/pishell"
echo "PiShell removed. Your config was preserved at ~/.config/pishell."
