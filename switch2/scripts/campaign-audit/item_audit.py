# Item audit for Founding of Durotar: finds every item that can appear across the
# campaign maps (placed / dropped in war3mapUnits.doo, referenced in war3map.j,
# sold/made by placed/scripted shops via Sellitems/Makeitems/usei/umki, or in
# ChooseRandomItem* pools) and checks which of their ability base codes are
# implemented in OpenWCIII-nx (Java registrations + abilityBehaviors/*.json).
# usage: python -I item_audit.py <orcx dir>
import re, os, glob, struct, sys, collections

U = "C:/VSCode_projects/warsmash/wc3data/war3.w3mod/units/"
UL = "C:/VSCode_projects/warsmash/wc3data/war3.w3mod/_locales/enus.w3mod/units/"
E = "C:/VSCode_projects/warsmash/OpenWCIII-nx/"

def slk(path):
    rows, y = {}, None
    with open(path, encoding="latin-1") as f:
        for line in f:
            if not line.startswith("C;"):
                continue
            m = re.search(r";Y(\d+)", line)
            if m:
                y = int(m.group(1))
            xm = re.search(r";X(\d+)", line)
            if not xm:
                continue
            x = int(xm.group(1))
            k = re.search(r";K(.*)$", line.rstrip())
            rows.setdefault(y, {})[x] = k.group(1).strip('"') if k else ""
    hdr = rows[1]
    return {r[1]: {hdr.get(x, x): v for x, v in r.items()} for yy, r in rows.items() if yy > 1 and 1 in r}

def ini_files(paths):
    out = {}
    for p in paths:
        cur = None
        if not os.path.exists(p):
            continue
        with open(p, encoding="utf-8", errors="replace") as f:
            for line in f:
                line = line.strip()
                m = re.match(r"^\[(\w{4})\]$", line)
                if m:
                    cur = out.setdefault(m.group(1), {})
                    continue
                if cur is not None and "=" in line:
                    k, v = line.split("=", 1)
                    cur[k.strip()] = v.strip().strip('"')
    return out

def parse_wts(path):
    out = {}
    if not os.path.exists(path):
        return out
    txt = open(path, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r"STRING\s+(\d+).*?\{(.*?)\}", txt, re.S):
        val = m.group(2).strip().replace("\r", "").replace("\n", " ")
        out[f"TRIGSTR_{int(m.group(1)):03d}"] = val
        out[f"TRIGSTR_{int(m.group(1))}"] = val
    return out

def parse_obj_file(path, has_level_col=False):
    out = {}
    if not os.path.exists(path):
        return out
    d = open(path, "rb").read()
    if len(d) < 8:
        return out
    p = 4
    for _ in range(2):
        n = struct.unpack_from("<I", d, p)[0]; p += 4
        for _ in range(n):
            orig = d[p:p+4].decode("latin-1")
            new = d[p+4:p+8].decode("latin-1")
            p += 8
            nm = struct.unpack_from("<I", d, p)[0]; p += 4
            fields = {}
            for _ in range(nm):
                fid = d[p:p+4].decode("latin-1"); p += 4
                t = struct.unpack_from("<I", d, p)[0]; p += 4
                lvl = 0
                if has_level_col:
                    lvl, _ = struct.unpack_from("<II", d, p); p += 8
                if t == 0:
                    val = struct.unpack_from("<i", d, p)[0]; p += 4
                elif t in (1, 2):
                    val = struct.unpack_from("<f", d, p)[0]; p += 4
                elif t == 3:
                    e = d.index(b"\0", p)
                    val = d[p:e].decode("utf-8", errors="replace")
                    p = e + 1
                else:
                    val = None; p += 4
                p += 4
                fields[(fid, lvl) if has_level_col else fid] = val
            oid = new if new.strip("\0") else orig
            out[oid] = (orig, fields)
    return out

def doo_ids(path):
    if not os.path.exists(path):
        return set()
    d = open(path, "rb").read()
    return {m.decode("latin-1") for m in re.findall(rb"[A-Za-z0-9]{4}", d)}

def implemented_codes():
    impl = {"AIhe", "AIma"}
    java_files = [
        E + "core/src/com/etheller/warsmash/viewer5/handlers/w3x/simulation/data/CAbilityData.java",
    ] + glob.glob(E + "core/src/com/etheller/warsmash/viewer5/handlers/w3x/simulation/abilities/skills/nx/*.java")
    for f in java_files:
        if not os.path.exists(f):
            continue
        for line in open(f, encoding="utf-8", errors="replace"):
            s = line.strip()
            if s.startswith("//") or s.startswith("*"):
                continue
            impl |= set(re.findall(r'War3ID\.fromString\("(\w{4})"\)', line))
            impl |= set(re.findall(r'put\([^,]+,\s*"(\w{4})"', line))
    for f in glob.glob(E + "core/assets/abilityBehaviors/*.json"):
        impl |= set(re.findall(r'"id"\s*:\s*"(\w{4})"', open(f, encoding="utf-8").read()))
    return impl

def main():
    orcx_dir = sys.argv[1]
    abil_slk = slk(U + "abilitydata.slk")
    item_slk = slk(U + "itemdata.slk")
    uab_slk = slk(U + "unitabilities.slk")
    item_strings = ini_files([UL + "itemstrings.txt", U + "itemfunc.txt"])
    abil_strings = ini_files(glob.glob(UL + "*abilitystrings.txt") + glob.glob(U + "*abilityfunc.txt"))
    unit_func = ini_files(glob.glob(U + "*unitfunc.txt"))
    impl = implemented_codes()

    entries = {}
    for mp in sorted(glob.glob(os.path.join(orcx_dir, "*", "war3map.j"))):
        d = os.path.dirname(mp)
        mname = os.path.basename(d)
        src = open(mp, encoding="utf-8", errors="replace").read()
        wts = parse_wts(os.path.join(d, "war3map.wts"))
        tmods = parse_obj_file(os.path.join(d, "war3map.w3t"), False)
        umods = parse_obj_file(os.path.join(d, "war3map.w3u"), False)
        amods = parse_obj_file(os.path.join(d, "war3map.w3a"), True)

        known_items = set(item_slk) | set(tmods)
        known_units = set(uab_slk) | set(umods)

        def rstr(s):
            return wts.get(s, s) if (s and s.startswith("TRIGSTR_")) else s

        found = collections.defaultdict(set)
        doo = doo_ids(os.path.join(d, "war3mapUnits.doo"))
        for iid in doo & known_items:
            found[iid].add("doo")
        script_lits = set(re.findall(r"'(\w{4})'", src))
        for iid in script_lits & known_items:
            found[iid].add("j")
        for u in (doo | script_lits) & known_units:
            base_u, uf = umods.get(u, (u, {}))
            p = unit_func.get(base_u, {})
            for kp, km in (("Sellitems", "usei"), ("Makeitems", "umki")):
                val = uf.get(km)
                if val is None:
                    val = p.get(kp, "")
                if isinstance(val, str):
                    for iid in val.split(","):
                        iid = iid.strip()
                        if len(iid) == 4 and iid in known_items:
                            found[iid].add(f"shop({u})")

        for iid, srcs in found.items():
            base_i, ifields = tmods.get(iid, (iid, {}))
            row = item_slk.get(base_i, {})
            abil_raw = ifields.get("iabi")
            if abil_raw is None:
                abil_raw = row.get("abilList", "")
            abils = [a.strip() for a in str(abil_raw).split(",") if len(a.strip()) == 4]
            name = rstr(ifields.get("unam")) or item_strings.get(base_i, {}).get("Name", base_i)
            usable = bool(ifields["iusa"]) if "iusa" in ifields else row.get("usable", "0") == "1"
            powerup = bool(ifields["ipow"]) if "ipow" in ifields else row.get("powerup", "0") == "1"
            perish = bool(ifields["iper"]) if "iper" in ifields else row.get("perishable", "0") == "1"

            resolved = []
            for a in abils:
                abase = amods.get(a, (a, {}))[0]
                acode = abil_slk.get(abase, {}).get("code", abase)
                aname = rstr(amods.get(a, (a, {}))[1].get(("anam", 0))) or abil_strings.get(abase, {}).get("Name", "") or abil_slk.get(abase, {}).get("comments", "")
                resolved.append((a, abase, acode, aname))

            key = (iid, name, tuple((x[0], x[2]) for x in resolved))
            if key not in entries:
                entries[key] = {
                    "iid": iid, "name": name, "abils": resolved,
                    "usable": usable, "powerup": powerup, "perish": perish,
                    "maps": set(), "srcs": set(),
                }
            entries[key]["maps"].add(mname)
            entries[key]["srcs"] |= srcs

    missing = collections.defaultdict(list)
    for info in entries.values():
        for a, abase, acode, aname in info["abils"]:
            if acode not in impl:
                missing[acode].append((info, a, abase, aname))

    for code, recs in sorted(missing.items(), key=lambda kv: (-len(set().union(*[r[0]["maps"] for r in kv[1]])), kv[0])):
        maps = sorted(set().union(*[r[0]["maps"] for r in recs]))
        comment = abil_slk.get(code, {}).get("comments", "").strip()
        items_s = ", ".join(f"{r[0]['iid']}({r[0]['name']})" for r in sorted(recs, key=lambda r: r[0]["iid"]))
        print(f"{len(maps):2d} maps  {code}  {comment[:30]:30s} items: {items_s}")

if __name__ == "__main__":
    main()
