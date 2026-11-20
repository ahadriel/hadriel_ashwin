#!/usr/bin/env python3
"""PiShell: lightweight GTK 3 service launcher for Raspberry Pi OS."""
import json
import os
import shutil
import subprocess
import time
import webbrowser
from pathlib import Path

import gi
gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, GLib, Gdk

APP_DIR = Path.home() / ".local/share/pishell"
CONFIG_PATH = Path.home() / ".config/pishell/config.json"
CSS = """
window { background: #101522; color: #f5f7ff; }
.root { background-image: url('file://%s'); background-size: cover; }
.topbar { background: rgba(10, 16, 29, 0.78); border-radius: 18px; padding: 12px 18px; }
.brand { color: #ffffff; font-size: 19px; font-weight: 800; letter-spacing: 2px; }
.muted { color: #d4deef; font-size: 12px; }
.clock { color: #ffffff; font-size: 21px; font-weight: 700; }
.stat { background: rgba(8, 15, 28, 0.72); border: 1px solid rgba(255,255,255,0.18); border-radius: 15px; padding: 12px; }
.stat-title { color: #b9c8dc; font-size: 11px; }
.stat-value { color: #ffffff; font-size: 17px; font-weight: 700; }
.service { background: rgba(9, 17, 31, 0.80); border: 1px solid rgba(255,255,255,0.20); border-radius: 18px; padding: 17px; }
.service:hover { background: rgba(25, 42, 66, 0.94); border-color: rgba(255,255,255,0.55); }
.service-title { color: #ffffff; font-size: 17px; font-weight: 700; }
.service-detail { color: #d0dced; font-size: 12px; }
.footer { color: rgba(255,255,255,0.80); font-size: 11px; }
button { outline: none; }
"""

def load_config():
    try:
        with CONFIG_PATH.open("r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError):
        return {"title": "PI CONTROL", "subtitle": "Your services, one calm place.", "services": []}

def system_stats():
    cpu_temp = "—"
    try:
        raw = Path("/sys/class/thermal/thermal_zone0/temp").read_text().strip()
        cpu_temp = f"{int(raw) / 1000:.0f}°C"
    except (OSError, ValueError):
        pass
    ram_used = ram_total = 0
    try:
        values = {}
        for line in Path("/proc/meminfo").read_text().splitlines():
            key, value = line.split(":", 1)
            values[key] = int(value.strip().split()[0])
        ram_total = values.get("MemTotal", 0) // 1024
        ram_available = values.get("MemAvailable", values.get("MemFree", 0)) // 1024
        ram_used = max(0, ram_total - ram_available)
    except (OSError, ValueError):
        pass
    try:
        load = os.getloadavg()[0]
        cpu_load = f"{load:.2f}"
    except (OSError, AttributeError):
        cpu_load = "—"
    return cpu_temp, f"{ram_used}/{ram_total} MB", cpu_load

class PiShell(Gtk.Window):
    def __init__(self):
        super().__init__(title="PiShell")
        self.config = load_config()
        self.set_decorated(False)
        self.fullscreen()
        self.set_keep_above(True)
        self.connect("key-press-event", self.on_key)
        self.connect("destroy", Gtk.main_quit)

        provider = Gtk.CssProvider()
        wallpaper = APP_DIR / "assets" / "colorfield.svg"
        provider.load_from_data((CSS % wallpaper.as_uri()).encode("utf-8"))
        Gtk.StyleContext.add_provider_for_screen(
            Gdk.Screen.get_default(), provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION
        )

        root = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=18)
        root.get_style_context().add_class("root")
        root.set_border_width(24)
        self.add(root)

        top = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        top.get_style_context().add_class("topbar")
        brand_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
        brand = Gtk.Label(label=self.config.get("title", "PI CONTROL"))
        brand.set_xalign(0)
        brand.get_style_context().add_class("brand")
        subtitle = Gtk.Label(label=self.config.get("subtitle", "Your services, one calm place."))
        subtitle.set_xalign(0)
        subtitle.get_style_context().add_class("muted")
        brand_box.pack_start(brand, False, False, 0)
        brand_box.pack_start(subtitle, False, False, 0)
        top.pack_start(brand_box, True, True, 4)
        self.clock = Gtk.Label()
        self.clock.get_style_context().add_class("clock")
        top.pack_end(self.clock, False, False, 4)
        root.pack_start(top, False, False, 0)

        stats = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        self.stat_labels = []
        for title in ("CPU temperature", "Memory used", "System load"):
            card = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=5)
            card.get_style_context().add_class("stat")
            t = Gtk.Label(label=title); t.set_xalign(0); t.get_style_context().add_class("stat-title")
            v = Gtk.Label(label="—"); v.set_xalign(0); v.get_style_context().add_class("stat-value")
            card.pack_start(t, False, False, 0); card.pack_start(v, False, False, 0)
            stats.pack_start(card, True, True, 0)
            self.stat_labels.append(v)
        root.pack_start(stats, False, False, 0)

        heading = Gtk.Label(label="YOUR SERVICES")
        heading.set_xalign(0)
        heading.get_style_context().add_class("muted")
        root.pack_start(heading, False, False, 0)

        grid = Gtk.Grid()
        grid.set_row_spacing(12); grid.set_column_spacing(12)
        grid.set_column_homogeneous(True)
        for index, item in enumerate(self.config.get("services", [])):
            button = Gtk.Button()
            button.set_relief(Gtk.ReliefStyle.NONE)
            button.get_style_context().add_class("service")
            content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=7)
            name = Gtk.Label(label=item.get("name", "Service"))
            name.set_xalign(0); name.get_style_context().add_class("service-title")
            detail = Gtk.Label(label=item.get("detail", ""))
            detail.set_xalign(0); detail.get_style_context().add_class("service-detail")
            content.pack_start(name, False, False, 0)
            content.pack_start(detail, False, False, 0)
            button.add(content)
            button.set_sensitive(item.get("kind") != "disabled")
            button.connect("clicked", self.launch_service, item)
            grid.attach(button, index % 3, index // 3, 1, 1)
        root.pack_start(grid, False, False, 0)

        footer = Gtk.Label(label="ESC  •  Exit PiShell     |     Edit shortcuts: ~/.config/pishell/config.json")
        footer.get_style_context().add_class("footer")
        footer.set_xalign(0)
        root.pack_end(footer, False, False, 0)

        self.refresh()
        GLib.timeout_add_seconds(2, self.refresh)

    def refresh(self):
        self.clock.set_text(time.strftime("%a, %d %b   •   %I:%M %p"))
        temp, memory, load = system_stats()
        for label, value in zip(self.stat_labels, (temp, memory, load)):
            label.set_text(value)
        return True

    def launch_service(self, _button, item):
        kind, target = item.get("kind"), item.get("target", "").strip()
        if kind == "url" and target:
            webbrowser.open(target)
        elif kind == "command_or_url":
            if target and shutil.which(target):
                subprocess.Popen([target], start_new_session=True)
            elif item.get("fallback_url"):
                webbrowser.open(item["fallback_url"])
            else:
                self.show_message("Twingate client was not found. Install/sign in to the Twingate client first.")
        elif kind == "url":
            self.show_message("Add this service URL in ~/.config/pishell/config.json first.")

    def show_message(self, message):
        dialog = Gtk.MessageDialog(transient_for=self, flags=0,
            message_type=Gtk.MessageType.INFO, buttons=Gtk.ButtonsType.OK, text=message)
        dialog.run(); dialog.destroy()

    def on_key(self, _widget, event):
        if event.keyval == Gdk.KEY_Escape:
            self.destroy()
            return True
        return False

if __name__ == "__main__":
    PiShell().show_all()
    Gtk.main()
