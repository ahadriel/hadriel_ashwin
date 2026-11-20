#!/usr/bin/env python3
"""PiShell — a lightweight GTK3 home surface and application launcher."""
import json, os, shutil, subprocess, time, webbrowser
from pathlib import Path
import gi
gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, GLib, Gdk, Gio

APP_DIR = Path.home() / ".local/share/pishell"
CONFIG_PATH = Path.home() / ".config/pishell/config.json"
CSS = """
window { background: #090f1d; color: #f4f7ff; }
.root { background-image: linear-gradient(rgba(5,10,22,.28),rgba(5,10,22,.62)), url('file://%s'); background-size: cover; }
.topbar { background: rgba(8,15,29,.88); border: 1px solid rgba(255,255,255,.12); border-radius: 20px; padding: 10px 16px; }
.brand { color:#fff; font-size:18px; font-weight:800; letter-spacing:3px; }
.eyebrow { color:#9fb3d3; font-size:10px; letter-spacing:2px; }
.clock { color:#fff; font-size:20px; font-weight:700; }
.panel { background:rgba(9,17,33,.82); border:1px solid rgba(255,255,255,.12); border-radius:22px; padding:18px; }
.panel-title { color:#9fb5d7; font-size:11px; font-weight:700; letter-spacing:2px; }
.hero { color:#fff; font-size:34px; font-weight:800; }
.subtle { color:#b5c5df; font-size:13px; }
.tile { background:rgba(22,37,62,.82); border:1px solid rgba(255,255,255,.12); border-radius:17px; padding:15px; }
.tile:hover { background:rgba(43,67,103,.96); border-color:rgba(116,183,255,.65); }
.tile-title { color:#fff; font-size:15px; font-weight:700; }
.tile-detail { color:#aebfd9; font-size:11px; }
.nav { background:rgba(7,13,25,.86); border:1px solid rgba(255,255,255,.10); border-radius:18px; padding:8px; }
.nav button { color:#b9c9e2; background:transparent; border:0; border-radius:12px; padding:10px 14px; }
.nav button:hover, .nav button:checked { background:rgba(84,139,211,.25); color:#fff; }
.dock { background:rgba(7,13,25,.90); border:1px solid rgba(255,255,255,.14); border-radius:22px; padding:8px 12px; }
.dock button { background:rgba(255,255,255,.07); color:#fff; border:0; border-radius:14px; padding:10px 14px; }
.dock button:hover { background:rgba(89,154,235,.3); }
.search { background:rgba(255,255,255,.10); color:#fff; border:1px solid rgba(255,255,255,.18); border-radius:13px; padding:10px; }
button { outline:none; }
"""
def load_config():
    try: return json.loads(CONFIG_PATH.read_text())
    except Exception: return {"title":"PI SHELL","subtitle":"Your space. Your services.","services":[]}
def run_output(args):
    try: return subprocess.check_output(args, stderr=subprocess.DEVNULL, text=True, timeout=2).strip()
    except Exception: return ""
def stats():
    temp="—"
    try: temp=f"{int(Path('/sys/class/thermal/thermal_zone0/temp').read_text())/1000:.0f}°C"
    except Exception: pass
    used=total=0
    try:
        m={}
        for line in Path('/proc/meminfo').read_text().splitlines():
            k,v=line.split(':',1); m[k]=int(v.strip().split()[0])
        total=m.get('MemTotal',0)//1024; used=total-m.get('MemAvailable',m.get('MemFree',0))//1024
    except Exception: pass
    return temp, f"{used}/{total} MB", f"{os.getloadavg()[0]:.2f}" if hasattr(os,'getloadavg') else "—"
class PiShell(Gtk.Window):
    def __init__(self):
        super().__init__(title="PiShell")
        self.cfg=load_config(); self.set_decorated(False); self.set_default_size(1280,720)
        self.connect("key-press-event",self.on_key); self.connect("destroy",Gtk.main_quit)
        # Request fullscreen after the window is mapped; some window managers ignore
        # fullscreen requests made before the first show_all().
        self.connect("map-event", self.ensure_fullscreen)
        provider=Gtk.CssProvider(); wallpaper=APP_DIR/"assets"/"colorfield.svg"
        provider.load_from_data((CSS % wallpaper.as_uri()).encode())
        Gtk.StyleContext.add_provider_for_screen(Gdk.Screen.get_default(),provider,Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)
        root=Gtk.Box(orientation=Gtk.Orientation.VERTICAL,spacing=14); root.set_border_width(22); root.set_hexpand(True); root.set_vexpand(True); root.get_style_context().add_class("root"); self.add(root)
        top=Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL,spacing=12); top.get_style_context().add_class("topbar")
        brandbox=Gtk.Box(orientation=Gtk.Orientation.VERTICAL,spacing=2)
        brand=Gtk.Label(label="P I  S H E L L"); brand.set_xalign(0); brand.get_style_context().add_class("brand")
        tag=Gtk.Label(label="HOME CONTROL"); tag.set_xalign(0); tag.get_style_context().add_class("eyebrow")
        brandbox.pack_start(brand,False,False,0); brandbox.pack_start(tag,False,False,0); top.pack_start(brandbox,True,True,0)
        self.clock=Gtk.Label(); self.clock.get_style_context().add_class("clock"); top.pack_end(self.clock,False,False,8)
        quick=Gtk.Button(label="QUICK SETTINGS"); quick.connect("clicked",self.quick_settings); top.pack_end(quick,False,False,4)
        root.pack_start(top,False,False,0)
        nav=Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL,spacing=6); nav.get_style_context().add_class("nav")
        for label,cb in [("⌂  HOME",lambda *_: self.stack.set_visible_child_name("home")),("▦  APPS",lambda *_: self.stack.set_visible_child_name("apps")),("◇  SERVICES",lambda *_: self.stack.set_visible_child_name("services"))]:
            b=Gtk.Button(label=label); b.connect("clicked",cb); nav.pack_start(b,False,False,0)
        root.pack_start(nav,False,False,0)
        self.stack=Gtk.Stack(transition_type=Gtk.StackTransitionType.CROSSFADE,transition_duration=160)
        self.stack.add_named(self.build_home(),"home"); self.stack.add_named(self.build_apps(),"apps"); self.stack.add_named(self.build_services(),"services")
        self.stack.set_hexpand(True); self.stack.set_vexpand(True)
        root.pack_start(self.stack,True,True,0)
        dock=Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL,spacing=8); dock.set_halign(Gtk.Align.CENTER); dock.get_style_context().add_class("dock")
        for label,cmd in [("⌂ Files","thunar"),("▣ Terminal","xfce4-terminal"),("🌐 Browser","firefox"),("▦ Apps","__apps__")]:
            b=Gtk.Button(label=label); b.connect("clicked",self.dock_launch,cmd); dock.pack_start(b,False,False,0)
        root.pack_end(dock,False,False,0)
        self.refresh(); GLib.timeout_add_seconds(2,self.refresh)
    def ensure_fullscreen(self,*_args):
        self.fullscreen()
        return False
    def panel(self,title):
        box=Gtk.Box(orientation=Gtk.Orientation.VERTICAL,spacing=12); box.get_style_context().add_class("panel")
        t=Gtk.Label(label=title); t.set_xalign(0); t.get_style_context().add_class("panel-title"); box.pack_start(t,False,False,0); return box
    def tile(self,name,detail,callback):
        b=Gtk.Button(); b.set_relief(Gtk.ReliefStyle.NONE); b.get_style_context().add_class("tile")
        v=Gtk.Box(orientation=Gtk.Orientation.VERTICAL,spacing=7); n=Gtk.Label(label=name); n.set_xalign(0); n.get_style_context().add_class("tile-title")
        d=Gtk.Label(label=detail); d.set_xalign(0); d.get_style_context().add_class("tile-detail")
        v.pack_start(n,False,False,0); v.pack_start(d,False,False,0); b.add(v); b.connect("clicked",callback); return b
    def build_home(self):
        page=Gtk.Box(orientation=Gtk.Orientation.VERTICAL,spacing=14)
        hero=self.panel("YOUR DESKTOP")
        h=Gtk.Label(label="Everything in its place."); h.set_xalign(0); h.get_style_context().add_class("hero")
        sub=Gtk.Label(label="A calm control surface for your Linux machine."); sub.set_xalign(0); sub.get_style_context().add_class("subtle")
        hero.pack_start(h,False,False,0); hero.pack_start(sub,False,False,0); page.pack_start(hero,False,False,0)
        row=Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL,spacing=10)
        self.metric_labels=[]
        for title in ("CPU TEMPERATURE","MEMORY IN USE","SYSTEM LOAD"):
            p=self.panel(title); val=Gtk.Label(label="—"); val.set_xalign(0); val.get_style_context().add_class("hero"); p.pack_start(val,False,False,0); row.pack_start(p,True,True,0); self.metric_labels.append(val)
        page.pack_start(row,False,False,0)
        services=self.panel("PINNED SERVICES")
        grid=Gtk.Grid(column_spacing=10,row_spacing=10); grid.set_column_homogeneous(True)
        items=self.cfg.get("services",[])[:6]
        for i,item in enumerate(items): grid.attach(self.tile(item.get("name","Service"),item.get("detail","Open service"),lambda _b,x=item:self.launch_service(x)),i%3,i//3,1,1)
        if not items:
            msg=Gtk.Label(label="Add service shortcuts in ~/.config/pishell/config.json"); msg.get_style_context().add_class("subtle"); services.pack_start(msg,False,False,0)
        else: services.pack_start(grid,False,False,0)
        page.pack_start(services,False,False,0); return page
    def build_services(self):
        page=Gtk.Box(orientation=Gtk.Orientation.VERTICAL,spacing=12); p=self.panel("SERVICE LIBRARY")
        grid=Gtk.Grid(column_spacing=10,row_spacing=10); grid.set_column_homogeneous(True)
        for i,item in enumerate(self.cfg.get("services",[])): grid.attach(self.tile(item.get("name","Service"),item.get("detail",""),lambda _b,x=item:self.launch_service(x)),i%3,i//3,1,1)
        p.pack_start(grid,False,False,0); page.pack_start(p,False,False,0); return page
    def build_apps(self):
        page=Gtk.Box(orientation=Gtk.Orientation.VERTICAL,spacing=12); p=self.panel("APPLICATION LAUNCHER")
        entry=Gtk.SearchEntry(); entry.set_placeholder_text("Search installed applications…"); entry.get_style_context().add_class("search")
        listing=Gtk.ListBox(); listing.set_selection_mode(Gtk.SelectionMode.NONE)
        apps=sorted(Gio.AppInfo.get_all(),key=lambda a:(a.get_display_name() or "").lower())
        rows=[]
        for app in apps:
            name=app.get_display_name() or app.get_name() or "Application"
            if not app.should_show(): continue
            row=Gtk.ListBoxRow(); btn=Gtk.Button(label="▣   "+name); btn.set_relief(Gtk.ReliefStyle.NONE); btn.set_halign(Gtk.Align.FILL)
            btn.connect("clicked",lambda _b,a=app:self.open_app(a)); row.add(btn); listing.add(row); rows.append((name.lower(),row))
        def filter_rows(search):
            q=search.get_text().lower()
            for name,row in rows: row.set_visible(q in name)
        entry.connect("search-changed",filter_rows); p.pack_start(entry,False,False,0)
        scroll=Gtk.ScrolledWindow(); scroll.set_min_content_height(300); scroll.add(listing); p.pack_start(scroll,True,True,0); page.pack_start(p,True,True,0); return page
    def open_app(self,app):
        try: app.launch([],None)
        except Exception as e: self.message(str(e))
    def dock_launch(self,_b,cmd):
        if cmd=="__apps__": self.stack.set_visible_child_name("apps"); return
        if shutil.which(cmd): subprocess.Popen([cmd],start_new_session=True)
        else: self.message(cmd+" is not installed on this system.")
    def launch_service(self,item):
        kind=item.get("kind"); target=item.get("target","").strip()
        if kind=="url" and target: webbrowser.open(target)
        elif kind=="command_or_url":
            if target and shutil.which(target): subprocess.Popen([target],start_new_session=True)
            elif item.get("fallback_url"): webbrowser.open(item["fallback_url"])
            else: self.message("Twingate client was not found. Install/sign in to its client first.")
        elif kind=="url": self.message("Set this service URL in ~/.config/pishell/config.json.")
        else: self.message("This shortcut is not configured yet.")
    def quick_settings(self,_b):
        wifi=run_output(["nmcli","-t","-f","WIFI","radio"]) or "Not available"
        volume=run_output(["pactl","get-sink-volume","@DEFAULT_SINK@"]) or "Audio service unavailable"
        dialog=Gtk.MessageDialog(transient_for=self,flags=0,message_type=Gtk.MessageType.INFO,buttons=Gtk.ButtonsType.CLOSE,text="QUICK SETTINGS")
        dialog.format_secondary_text("Wi-Fi radio: "+wifi+"\n"+volume+"\n\nUse your desktop system tray for changing network and audio settings."); dialog.run(); dialog.destroy()
    def refresh(self):
        self.clock.set_text(time.strftime("%a  %d %b   •   %I:%M %p"))
        if hasattr(self,"metric_labels"):
            for label,value in zip(self.metric_labels,stats()): label.set_text(value)
        return True
    def message(self,msg):
        d=Gtk.MessageDialog(transient_for=self,flags=0,message_type=Gtk.MessageType.INFO,buttons=Gtk.ButtonsType.OK,text=msg); d.run(); d.destroy()
    def on_key(self,_w,event):
        if event.keyval==Gdk.KEY_Escape: self.destroy(); return True
        if event.keyval==Gdk.KEY_F1: self.stack.set_visible_child_name("home")
        if event.keyval==Gdk.KEY_F2: self.stack.set_visible_child_name("apps")
        return False
if __name__=="__main__":
    window=PiShell()
    window.show_all()
    # Re-assert fullscreen once GTK has completed initial widget mapping.
    GLib.idle_add(window.fullscreen)
    Gtk.main()
