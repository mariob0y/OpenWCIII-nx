# Stricter audit: abilities of units that can actually appear in the campaign maps.
#   units = placed (war3mapUnits.doo) + created/restored by the script (4-char literals on lines that are
#           not tech/ability availability lines) + what those buildings train/sell/upgrade to (transitive)
#           + ability summons (UnitID fields)
#   abilities = their abilList/heroAbilList (map w3u edits of uabi/uhab override) + abilities the script
#               adds (UnitAddAbility)
# usage: python -I campaign_audit.py <orcx dir>
import re, os, glob, struct, sys, collections
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "ability_audit.py")).read().split("missing = collections")[0])
U = "C:/VSCode_projects/warsmash/wc3data/war3.w3mod/units/"

def profile(files):
    out, cur = {}, None
    for f in files:
        for line in open(U + f, encoding="latin-1"):
            line = line.strip()
            m = re.match(r"^\[(\w{4})\]$", line)
            if m:
                cur = out.setdefault(m.group(1), {}); continue
            if cur is not None and "=" in line:
                k, v = line.split("=", 1); cur[k] = v
    return out
prof = profile([os.path.basename(p) for p in glob.glob(U + "*unitfunc.txt")])

def w3u_mods(path):
    """custom/modified units -> (base id, {field: string value}) for ability list fields."""
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
            fields = {}
            for _ in range(nm):
                fid = d[p:p+4].decode("latin-1"); p += 4
                t = struct.unpack_from("<I", d, p)[0]; p += 4
                if t == 3:
                    e = d.index(b"\0", p); val = d[p:e].decode("latin-1"); p = e + 1
                else:
                    val = None; p += 4
                p += 4
                fields[fid] = val
            uid = new if new.strip("\0") else orig
            out[uid] = (orig, fields)
    return out

def doo_units(path):
    try: d = open(path, "rb").read()
    except OSError: return set()
    return {m.decode("latin-1") for m in re.findall(rb"[A-Za-z][A-Za-z0-9]{3}", d)}

missing = collections.defaultdict(lambda: [set(), set(), set()])  # code -> aliases, maps, units
for mp in sorted(glob.glob(sys.argv[1] + "/*/war3map.j")):
    d = os.path.dirname(mp); name = os.path.basename(d)
    src = open(mp, encoding="latin-1").read()
    lines = [l for l in src.splitlines() if re.search(r"Create(?:N)?Unit|ReplaceUnit|UnitToStock|UnitToAllStock|CreateCorpse|CreatePermanentCorpse|SetUnitTypeId|ChangeUnit", l)]
    lit = set(re.findall(r"'(\w{4})'", "\n".join(lines)))
    custom_ab = w3a(d + "/war3map.w3a")
    umods = w3u_mods(d + "/war3map.w3u")
    known = set(uab) | set(umods)
    units = {u for u in doo_units(d + "/war3mapUnits.doo") if u in known} | {u for u in lit if u in known}
    added = set(re.findall(r"UnitAddAbility(?:BJ)?\s*\(\s*'(\w{4})'", src)) | set(re.findall(r"UnitAddAbility\s*\([^,]+,\s*'(\w{4})'", src))
    # transitive: trains / sells / upgrades / summons
    todo = list(units)
    while todo:
        u = todo.pop()
        base = umods.get(u, (u, {}))[0]
        p = prof.get(base, {})
        nxt = set()
        for k in ("Trains", "Sellunits", "Upgrade", "Builds"):
            nxt |= {x for x in p.get(k, "").split(",") if len(x) == 4}
        for k, v in umods.get(u, (u, {}))[1].items():
            if k in ("utra", "useu", "uupt", "ubui") and v:
                nxt |= {x for x in v.split(",") if len(x) == 4}
        for x in nxt - units:
            if x in known:
                units.add(x); todo.append(x)
    abils = collections.defaultdict(set)
    for u in units:
        base, fields = umods.get(u, (u, {}))
        lst = set()
        for f, key in (("abilList", "uabi"), ("heroAbilList", "uhab")):
            v = fields.get(key)
            if v is None: v = uab.get(base, {}).get(f, "")
            lst |= {a for a in v.split(",") if len(a) == 4}
        for a in lst: abils[a].add(u)
    for a in added: abils[a].add("(script)")
    # summons of those abilities
    for a in list(abils):
        b = custom_ab.get(a, a)
        for lv in range(1, 5):
            s = abil.get(b, {}).get(f"UnitID{lv}") or ""
            for x in s.split(","):
                if x in known and x not in units:
                    units.add(x)
                    base = umods.get(x, (x, {}))[0]
                    for f in ("abilList", "heroAbilList"):
                        for aa in uab.get(base, {}).get(f, "").split(","):
                            if len(aa) == 4: abils[aa].add(x)
    for a, who in abils.items():
        base = custom_ab.get(a, a)
        code = abil.get(base, {}).get("code", base)
        if code not in impl:
            missing[code][0].add(a); missing[code][1].add(name); missing[code][2] |= who
names = {}
for u in set().union(*[v[2] for v in missing.values()]) if missing else []:
    names[u] = ui.get(u, {}).get("name", "") if u in ui else ""
for code, (aliases, maps, who) in sorted(missing.items(), key=lambda kv: (-len(kv[1][1]), kv[0])):
    comment = abil.get(code, {}).get("comments", "")
    print(f"{len(maps):2d} maps  {code}  {comment[:34]:34s} on: {', '.join(sorted(who))[:70]}")
