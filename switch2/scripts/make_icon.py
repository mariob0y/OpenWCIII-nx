"""warsmash-nx: the NRO's 256x256 icon, made from a local Warcraft III installation.

    python -I scripts/make_icon.py

warsmash/icon.jpg (the one the NRO uses): The Frozen Throne logo (war3xlogo.blp:
BLP1 palettized with an 8-bit alpha plane, decoded here because Pillow reads that alpha as 0) on a
dark icy background, no text.

warsmash/icon-exe.jpg (the alternative, not used): the icon Windows shows for Warcraft III's shortcut
and .exe, the orc face (x86_64/Warcraft III.exe, first RT_GROUP_ICON, its 48x48 24-bit image: the
largest the .exe has), upscaled bicubic + a light unsharp mask, so it is soft at 256x256.
"""
import io
import struct
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

GAME = Path("C:/VSCode_projects/warsmash/Warcraft III")
DATA = Path("C:/VSCode_projects/warsmash/wc3data/war3.w3mod")
OUT = Path(__file__).resolve().parent.parent / "warsmash"
SIZE = 256


def exe_icon(exe):
    """The largest, deepest image of the first icon group (what Windows shows) of a PE file."""
    d = exe.read_bytes()
    pe = struct.unpack_from("<I", d, 0x3C)[0]
    nsec = struct.unpack_from("<H", d, pe + 6)[0]
    sec = pe + 24 + struct.unpack_from("<H", d, pe + 20)[0]
    for i in range(nsec):
        name, _, va, _, raw = struct.unpack_from("<8sIIII", d, sec + i * 40)
        if name.rstrip(b"\0") == b".rsrc":
            break
    else:
        raise SystemExit(f"{exe}: no .rsrc")

    def entries(off):
        named, ids = struct.unpack_from("<HH", d, raw + off + 12)
        for k in range(named + ids):
            yield struct.unpack_from("<II", d, raw + off + 16 + k * 8)

    def resources(kind):
        found = []
        for tid, child in entries(0):
            if tid == kind:
                for nid, c2 in entries(child & 0x7FFFFFFF):
                    for _, c3 in entries(c2 & 0x7FFFFFFF):
                        rva, size = struct.unpack_from("<II", d, raw + c3)
                        found.append((nid, d[rva - va + raw:rva - va + raw + size]))
        return found

    icons = dict(resources(3))
    group = resources(14)[0][1]
    count = struct.unpack_from("<H", group, 4)[0]
    best = None
    for k in range(count):
        w, _, _, _, _, bpp, _, nid = struct.unpack_from("<BBBBHHIH", group, 6 + k * 14)
        key = (w or 256, bpp)
        if best is None or key > best[0]:
            best = (key, nid)
    blob = icons[best[1]]
    if blob[:8] == b"\x89PNG\r\n\x1a\n":
        return Image.open(io.BytesIO(blob)).convert("RGBA")
    w, h2, _, bpp = struct.unpack_from("<iiHH", blob, 4)
    ico = struct.pack("<HHH", 0, 1, 1) + struct.pack("<BBBBHHII", w % 256, (h2 // 2) % 256, 0, 0, 1, bpp,
                                                     len(blob), 22) + blob
    return Image.open(io.BytesIO(ico)).convert("RGBA")


def blp1_alpha(path):
    """BLP1, palettized, 8-bit alpha plane: mipmap 0 as RGBA."""
    d = path.read_bytes()
    assert d[:4] == b"BLP1"
    comp, alpha_bits, w, h = struct.unpack_from("<4I", d, 4)
    assert comp == 1 and alpha_bits == 8, (comp, alpha_bits)
    off, size = struct.unpack_from("<I", d, 28)[0], struct.unpack_from("<I", d, 92)[0]
    pal, mip = d[156:156 + 1024], d[off:off + size]
    px = bytearray(w * h * 4)
    for i in range(w * h):
        p = mip[i] * 4
        px[i * 4:i * 4 + 4] = bytes((pal[p + 2], pal[p + 1], pal[p], mip[w * h + i]))
    return Image.frombytes("RGBA", (w, h), bytes(px))


def exe_variant():
    icon = exe_icon(GAME / "x86_64/Warcraft III.exe")
    flat = Image.new("RGBA", icon.size, (0, 0, 0, 255))
    flat.alpha_composite(icon)
    big = flat.convert("RGB").resize((SIZE, SIZE), Image.BICUBIC).filter(ImageFilter.UnsharpMask(2, 60, 2))
    big.save(OUT / "icon-exe.jpg", quality=95)
    print("icon-exe.jpg: Warcraft III.exe icon", icon.size, "->", big.size)


def tft_variant():
    logo = blp1_alpha(DATA / "ui/glues/mainmenu/warcraftiiilogo_exp/war3xlogo.blp")
    logo = logo.crop(logo.getchannel("A").getbbox())
    glow = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(glow).ellipse((-40, -10, SIZE + 40, SIZE + 30), fill=255)
    glow = glow.filter(ImageFilter.GaussianBlur(50))
    icon = Image.composite(Image.new("RGB", (SIZE, SIZE), (16, 34, 66)),
                           Image.new("RGB", (SIZE, SIZE), (2, 4, 10)), glow)
    lw = 244
    lh = round(logo.height * lw / logo.width)
    lg = logo.resize((lw, lh), Image.LANCZOS)
    icon.paste(lg, ((SIZE - lw) // 2, (SIZE - lh) // 2), lg)
    icon.save(OUT / "icon.jpg", quality=95)
    print("icon.jpg: The Frozen Throne logo", logo.size, "->", (lw, lh))


exe_variant()
tft_variant()
