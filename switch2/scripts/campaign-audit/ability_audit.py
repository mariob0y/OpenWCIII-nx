import re, os, glob, struct, sys, collections
D = "C:/VSCode_projects/warsmash/wc3data/war3.w3mod/units/"
E = "C:/VSCode_projects/warsmash/OpenWCIII-nx/"
def slk(path):
    rows, y = {}, None
    for line in open(path, encoding="latin-1"):
        if not line.startswith("C;"): continue
        m = re.search(r";Y(\d+)", line)
        if m: y = int(m.group(1))
        x = int(re.search(r";X(\d+)", line).group(1))
        k = re.search(r";K(.*)$", line.rstrip())
        rows.setdefault(y, {})[x] = k.group(1).strip('"') if k else ""
    hdr = rows[1]
    return {r[1]: {hdr.get(x, x): v for x, v in r.items()} for yy, r in rows.items() if yy > 1 and 1 in r}
abil = slk(D + "abilitydata.slk")
uab = slk(D + "unitabilities.slk")
ui = slk(D + "unitui.slk")
# implemented ability codes
impl = set()
for root, _, files in os.walk(E + "core/src"):
    for f in files:
        if f.endswith(".java"):
            src = open(os.path.join(root, f), encoding="utf-8", errors="replace").read()
            for line in src.splitlines():
                if line.strip().startswith("//"): continue
                impl |= set(re.findall(r'War3ID\.fromString\("(\w{4})"\)', line))
                impl |= set(re.findall(r'put\(map, "(\w{4})"', line))
for f in glob.glob(E + "core/assets/abilityBehaviors/*.json"):
    impl |= set(re.findall(r'"id"\s*:\s*"(\w{4})"', open(f, encoding="utf-8").read()))
def w3a(path):
    out = {}
    try: d = open(path, "rb").read()
    except OSError: return out
    if len(d) < 8: return out
    p = 4
    for table in range(2):
        n = struct.unpack_from("<I", d, p)[0]; p += 4
        for _ in range(n):
            orig, new = d[p:p+4].decode("latin-1"), d[p+4:p+8].decode("latin-1"); p += 8
            nm = struct.unpack_from("<I", d, p)[0]; p += 4
            for _ in range(nm):
                p += 4; t = struct.unpack_from("<I", d, p)[0]; p += 12
                if t == 3:
                    p = d.index(b"\0", p) + 1
                else: p += 4
                p += 4
            if new.strip("\0"): out[new] = orig
    return out
missing = collections.defaultdict(lambda: [set(), set()])  # code -> (aliases, maps)
for mp in sorted(glob.glob(sys.argv[1] + "/*/war3map.j")):
    d = os.path.dirname(mp); name = os.path.basename(d)
    src = open(mp, encoding="latin-1").read()
    codes = set(re.findall(r"'(\w{4})'", src))
    custom = w3a(d + "/war3map.w3a")
    abils = set()
    for c in codes:
        if c in uab:
            for f in ("abilList", "heroAbilList"):
                abils |= {a for a in uab[c].get(f, "").split(",") if len(a) == 4}
        if c in abil or c in custom: abils.add(c)
    for a in abils:
        base = custom.get(a, a)
        code = abil.get(base, {}).get("code", base)
        if code not in impl:
            missing[code][0].add(a); missing[code][1].add(name)
for code, (aliases, maps) in sorted(missing.items(), key=lambda kv: -len(kv[1][1])):
    comment = abil.get(code, {}).get("comments", "")
    print(f"{len(maps):2d} maps  {code}  {comment[:38]:38s} aliases {','.join(sorted(aliases))[:60]}")
