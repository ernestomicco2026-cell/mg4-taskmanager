#!/usr/bin/env python3
"""Impacchetta l'APK senza Android SDK/aapt: genera manifest e risorse in formato binario Android,
li unisce a classes.dex e all'icona. La firma si fa poi con jarsigner (schema v1, ok per Android 9)."""
import os
import struct
import sys
import zipfile

from PIL import Image, ImageDraw

NS = "http://schemas.android.com/apk/res/android"
PKG = "it.mg4.taskmanager"

# ID degli attributi android:* (presi da android.R.attr di android-28)
ATTR = {
    "theme": 16842752, "label": 16842753, "icon": 16842754, "name": 16842755, "permission": 16842758,
    "enabled": 16842766, "exported": 16842768, "taskAffinity": 16842770, "excludeFromRecents": 16842775,
    "launchMode": 16842781, "configChanges": 16842783, "description": 16842784, "value": 16842788,
    "resource": 16842789, "versionCode": 16843291, "versionName": 16843292, "minSdkVersion": 16843276,
    "targetSdkVersion": 16843376, "accessibilityEventTypes": 16843648, "accessibilityFeedbackType": 16843650,
    "notificationTimeout": 16843651, "accessibilityFlags": 16843652, "canRequestFilterKeyEvents": 16843737,
    "canRetrieveWindowContent": 16843653,
}

T_REF, T_STRING, T_INT_DEC, T_INT_HEX, T_BOOL = 0x01, 0x03, 0x10, 0x11, 0x12

ID_DRAWABLE_IC = 0x7F010000
ID_XML_KEYS = 0x7F020000
THEME_DEVICE_DEFAULT_NO_ACTION_BAR = 0x01030129


class Ref(int):
    pass


class Hex(int):
    pass


class E:
    def __init__(self, tag, attrs=(), children=()):
        self.tag, self.attrs, self.children = tag, list(attrs), list(children)


# ------------------------------------------------------------------ string pool

def string_pool(strings, utf8):
    count = len(strings)
    data = b""
    offsets = []
    for s in strings:
        offsets.append(len(data))
        if utf8:
            b = s.encode("utf-8")
            assert len(s) < 128 and len(b) < 128
            data += bytes([len(s), len(b)]) + b + b"\x00"
        else:
            u = s.encode("utf-16-le")
            assert len(s) < 0x8000
            data += struct.pack("<H", len(s)) + u + b"\x00\x00"
    while len(data) % 4:
        data += b"\x00"
    header_size = 28
    strings_start = header_size + 4 * count
    size = strings_start + len(data)
    out = struct.pack("<HHIIIIII", 0x0001, header_size, size, count, 0, 0x100 if utf8 else 0, strings_start, 0)
    out += b"".join(struct.pack("<I", o) for o in offsets) + data
    assert len(out) == size
    return out


# ------------------------------------------------------------------ XML binario (AXML)

def axml(root):
    # 1) raccolta stringhe: prima i nomi di attributo con ID (allineati alla resource map)
    id_names, other = [], []

    def walk(e):
        for k, v in e.attrs:
            n = k.split(":", 1)[1] if k.startswith("android:") else k
            if k.startswith("android:"):
                if n not in id_names:
                    id_names.append(n)
            elif n not in other:
                other.append(n)
            if isinstance(v, str) and v not in other:
                other.append(v)
        if e.tag not in other:
            other.append(e.tag)
        for c in e.children:
            walk(c)

    walk(root)
    for s in ("android", NS):
        if s not in other:
            other.append(s)
    other = [s for s in other if s not in id_names]
    strings = id_names + other
    idx = {s: i for i, s in enumerate(strings)}

    pool = string_pool(strings, utf8=False)
    resmap = struct.pack("<HHI", 0x0180, 8, 8 + 4 * len(id_names)) + b"".join(
        struct.pack("<I", ATTR[n]) for n in id_names)

    body = b""
    line = [1]

    def node_hdr(t, size):
        line[0] += 1
        return struct.pack("<HHIII", t, 16, size, line[0], 0xFFFFFFFF)

    body += node_hdr(0x0100, 24) + struct.pack("<II", idx["android"], idx[NS])

    def emit(e):
        nonlocal body
        attrs = []
        for k, v in e.attrs:
            if k.startswith("android:"):
                n = k.split(":", 1)[1]
                ns, rid = idx[NS], ATTR[n]
            else:
                n, ns, rid = k, 0xFFFFFFFF, 0
            if isinstance(v, bool):
                raw, typ, data = 0xFFFFFFFF, T_BOOL, 0xFFFFFFFF if v else 0
            elif isinstance(v, Ref):
                raw, typ, data = 0xFFFFFFFF, T_REF, int(v)
            elif isinstance(v, Hex):
                raw, typ, data = 0xFFFFFFFF, T_INT_HEX, int(v)
            elif isinstance(v, int):
                raw, typ, data = 0xFFFFFFFF, T_INT_DEC, v
            else:
                raw, typ, data = idx[v], T_STRING, idx[v]
            attrs.append((rid, ns, idx[n], raw, typ, data))
        attrs.sort(key=lambda a: a[0])  # il framework richiede attributi ordinati per ID risorsa
        size = 16 + 20 + 20 * len(attrs)
        body += node_hdr(0x0102, size) + struct.pack(
            "<IIHHHHHH", 0xFFFFFFFF, idx[e.tag], 20, 20, len(attrs), 0, 0, 0)
        for rid, ns, name, raw, typ, data in attrs:
            body += struct.pack("<IIIHBBI", ns, name, raw, 8, 0, typ, data)
        for c in e.children:
            emit(c)
        body += node_hdr(0x0103, 24) + struct.pack("<II", 0xFFFFFFFF, idx[e.tag])

    emit(root)
    body += node_hdr(0x0101, 24) + struct.pack("<II", idx["android"], idx[NS])

    content = pool + resmap + body
    return struct.pack("<HHI", 0x0003, 8, 8 + len(content)) + content


# ------------------------------------------------------------------ resources.arsc

def arsc(entries):
    """entries: lista di (type_name, key, file_path) -> type id 1.. in ordine, entry 0.."""
    types = []
    for t, k, f in entries:
        if t not in types:
            types.append(t)
    keys = [k for _, k, _ in entries]
    values = [f for _, _, f in entries]
    gpool = string_pool(values, utf8=True)
    tpool = string_pool(types, utf8=True)
    kpool = string_pool(keys, utf8=True)

    chunks = b""
    for ti, t in enumerate(types):
        ents = [(keys.index(k), values.index(f)) for tt, k, f in entries if tt == t]
        n = len(ents)
        chunks += struct.pack("<HHIBBHI", 0x0202, 16, 16 + 4 * n, ti + 1, 0, 0, n) + b"\x00" * (4 * n)
        config = struct.pack("<I", 64) + b"\x00" * 60
        header_size = 20 + len(config)
        entries_start = header_size + 4 * n
        offs, data = b"", b""
        for key_i, val_i in ents:
            offs += struct.pack("<I", len(data))
            data += struct.pack("<HHI", 8, 0, key_i) + struct.pack("<HBBI", 8, 0, T_STRING, val_i)
        size = entries_start + len(data)
        chunks += struct.pack("<HHIBBHII", 0x0201, header_size, size, ti + 1, 0, 0, n, entries_start) + config
        chunks += offs + data

    name = PKG.encode("utf-16-le").ljust(256, b"\x00")
    pkg_header_size = 288
    type_strings_off = pkg_header_size
    key_strings_off = pkg_header_size + len(tpool)
    pkg_body = tpool + kpool + chunks
    pkg = struct.pack("<HHII", 0x0200, pkg_header_size, pkg_header_size + len(pkg_body), 0x7F) + name
    pkg += struct.pack("<IIIII", type_strings_off, len(types), key_strings_off, len(keys), 0)
    assert len(pkg) == pkg_header_size
    pkg += pkg_body

    table_body = gpool + pkg
    return struct.pack("<HHII", 0x0002, 12, 12 + len(table_body), 1) + table_body


# ------------------------------------------------------------------ icona

def icon(path, size=192):
    s = 4  # supersampling
    S = size * s
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.22), fill=(22, 30, 38, 255))
    # tre "righe" di app
    bar_x0, bar_x1 = int(S * 0.18), int(S * 0.62)
    for i, col in enumerate([(43, 182, 115), (90, 160, 230), (240, 180, 60)]):
        y = int(S * (0.24 + i * 0.19))
        h = int(S * 0.12)
        d.rounded_rectangle((bar_x0, y, bar_x0 + h, y + h), radius=int(h * 0.25), fill=col + (255,))
        d.rounded_rectangle((bar_x0 + int(h * 1.35), y + int(h * 0.3), bar_x1, y + int(h * 0.7)),
                            radius=int(h * 0.2), fill=(200, 210, 220, 255))
    # badge "chiudi"
    cx, cy, r = int(S * 0.74), int(S * 0.70), int(S * 0.16)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(229, 72, 77, 255))
    w = int(S * 0.035)
    k = int(r * 0.45)
    d.line((cx - k, cy - k, cx + k, cy + k), fill=(255, 255, 255, 255), width=w)
    d.line((cx - k, cy + k, cx + k, cy - k), fill=(255, 255, 255, 255), width=w)
    im.resize((size, size), Image.LANCZOS).save(path, optimize=True)


# ------------------------------------------------------------------ main

def version_code(name):
    """"1.2.3" -> 10203: stessa formula di Updater.codeFromTag() nell'app."""
    parts = (name.split("-")[0].split(".") + ["0", "0", "0"])[:3]
    code = 0
    for p in parts:
        digits = "".join(ch for ch in p if ch.isdigit()) or "0"
        code = code * 100 + min(int(digits), 99)
    return code


def main(build):
    config_changes = 0x4 | 0x10 | 0x20 | 0x40 | 0x80 | 0x100 | 0x200 | 0x400 | 0x800 | 0x1000 | 0x2000
    version_name = os.environ.get("VERSION_NAME", "").lstrip("vV") or open("VERSION").read().strip()
    manifest = E("manifest", [("package", PKG), ("android:versionCode", version_code(version_name)),
                              ("android:versionName", version_name)], [
        E("uses-sdk", [("android:minSdkVersion", 28), ("android:targetSdkVersion", 28)]),
        E("uses-permission", [("android:name", "android.permission.INTERNET")]),
        E("uses-permission", [("android:name", "android.permission.FOREGROUND_SERVICE")]),
        E("uses-permission", [("android:name", "android.permission.RECEIVE_BOOT_COMPLETED")]),
        E("uses-permission", [("android:name", "android.permission.ACCESS_NETWORK_STATE")]),
        E("uses-permission", [("android:name", "android.permission.KILL_BACKGROUND_PROCESSES")]),
        E("uses-permission", [("android:name", "android.permission.PACKAGE_USAGE_STATS")]),
        E("uses-permission", [("android:name", "android.permission.REQUEST_INSTALL_PACKAGES")]),
        E("application", [("android:label", "Task Manager"), ("android:icon", Ref(ID_DRAWABLE_IC)),
                          ("android:theme", Ref(THEME_DEVICE_DEFAULT_NO_ACTION_BAR))], [
            E("activity", [("android:name", PKG + ".MainActivity"), ("android:label", "Task Manager"),
                           ("android:exported", True), ("android:launchMode", 2),
                           ("android:excludeFromRecents", True), ("android:configChanges", Hex(config_changes))], [
                E("meta-data", [("android:name", "distractionOptimized"), ("android:value", True)]),
                E("intent-filter", [], [
                    E("action", [("android:name", "android.intent.action.MAIN")]),
                    E("category", [("android:name", "android.intent.category.LAUNCHER")]),
                ]),
            ]),
            E("service", [("android:name", PKG + ".KeyService"),
                          ("android:label", "Task Manager - tasto al volante"),
                          ("android:permission", "android.permission.BIND_ACCESSIBILITY_SERVICE"),
                          ("android:exported", True)], [
                E("intent-filter", [], [
                    E("action", [("android:name", "android.accessibilityservice.AccessibilityService")]),
                ]),
                E("meta-data", [("android:name", "android.accessibilityservice"),
                                ("android:resource", Ref(ID_XML_KEYS))]),
            ]),
            E("service", [("android:name", PKG + ".KeyMonitorService"), ("android:exported", False)]),
            E("service", [("android:name", PKG + ".UpdateJobService"),
                          ("android:permission", "android.permission.BIND_JOB_SERVICE"),
                          ("android:exported", True)]),
            E("receiver", [("android:name", PKG + ".InstallReceiver"), ("android:exported", False)]),
            E("receiver", [("android:name", PKG + ".BootReceiver"), ("android:exported", True)], [
                E("intent-filter", [], [
                    E("action", [("android:name", "android.intent.action.BOOT_COMPLETED")]),
                    E("action", [("android:name", "android.intent.action.MY_PACKAGE_REPLACED")]),
                ]),
            ]),
        ]),
    ])

    a11y = E("accessibility-service", [
        ("android:description", "Permette al Task Manager di vedere le app aperte, chiuderle premendo «Forza interruzione» e aprirsi con un tasto del volante. Non legge né invia i tuoi dati."),
        ("android:accessibilityEventTypes", Hex(0x20)),       # typeWindowStateChanged
        ("android:accessibilityFeedbackType", Hex(0x10)),     # feedbackGeneric
        ("android:notificationTimeout", 100),
        ("android:accessibilityFlags", Hex(0x30)),            # flagRequestFilterKeyEvents | flagReportViewIds
        ("android:canRequestFilterKeyEvents", True),
        ("android:canRetrieveWindowContent", True),           # serve per premere "Forza interruzione"
    ])

    files = {
        "AndroidManifest.xml": axml(manifest),
        "res/xml/tm_keys.xml": axml(a11y),
        "resources.arsc": arsc([("drawable", "ic", "res/drawable/ic.png"),
                                ("xml", "tm_keys", "res/xml/tm_keys.xml")]),
    }
    icon_path = os.path.join(build, "ic.png")
    icon(icon_path)
    files["res/drawable/ic.png"] = open(icon_path, "rb").read()
    files["classes.dex"] = open(os.path.join(build, "classes.dex"), "rb").read()

    out = os.path.join(build, "unsigned.apk")
    with zipfile.ZipFile(out, "w") as z:
        for name in ["AndroidManifest.xml", "classes.dex", "resources.arsc", "res/drawable/ic.png",
                     "res/xml/tm_keys.xml"]:
            stored = name in ("resources.arsc", "res/drawable/ic.png")
            z.writestr(zipfile.ZipInfo(name, (2026, 10, 10, 12, 0, 0)), files[name],
                       compress_type=zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED)
    for name in ("AndroidManifest.xml", "resources.arsc", "res/xml/tm_keys.xml"):
        with open(os.path.join(build, os.path.basename(name) + ".bin"), "wb") as f:
            f.write(files[name])
    print("ok", out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "build")
