import re, sys, glob, collections, os
W = "C:/VSCode_projects/warsmash/wc3data/war3.w3mod/scripts/"
E = "C:/VSCode_projects/warsmash/OpenWCIII-nx/core/src"
common = open(W + "common.j", encoding="latin-1").read()
natives = set(re.findall(r"^\s*(?:constant\s+)?native\s+(\w+)", common, re.M))
impl = set()
for root, _, files in os.walk(E):
    for f in files:
        if f.endswith(".java"):
            impl |= set(re.findall(r'(?:createNative|define|forward)\("(\w+)"', open(os.path.join(root, f), encoding="utf-8", errors="replace").read()))
def funcs(src):
    out = {}
    for m in re.finditer(r"^function\s+(\w+)(.*?)^endfunction", src, re.M | re.S):
        out[m.group(1)] = set(re.findall(r"\b(\w+)\s*\(", m.group(2))) | set(re.findall(r"function\s+(\w+)", m.group(2)))
    return out
bj = funcs(open(W + "blizzard.j", encoding="latin-1").read())
missing = collections.defaultdict(set)
for mp in sorted(glob.glob(sys.argv[1] + "/*/war3map.j")):
    name = mp.replace("\\", "/").split("/")[-2]
    mf = funcs(open(mp, encoding="latin-1").read())
    seen, todo = set(), set().union(*mf.values())
    while todo:
        f = todo.pop()
        if f in seen: continue
        seen.add(f)
        if f in bj: todo |= bj[f]
    for n in seen & natives - impl:
        missing[n].add(name)
for n, maps in sorted(missing.items(), key=lambda kv: (-len(kv[1]), kv[0])):
    print(f"{len(maps):2d} {n}")
