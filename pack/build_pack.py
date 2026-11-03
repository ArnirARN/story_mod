"""Builds storymod.mrpack, a packwiz pack (auto-updating clients and servers) and a server mod folder from pack/mods.json using the Modrinth API.

usage: build_pack.py <storymod.jar> <neoforge version> <out dir>
Writes <out>/storymod.mrpack, <out>/server-mods/*.jar and <out>/mods.md (a readable list).
Mods that have no NeoForge 1.21.1 build are reported and skipped.
"""
import hashlib, json, os, sys, urllib.parse, urllib.request, zipfile

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "ArnirARN/story_mod pack builder (github.com/ArnirARN/story_mod)"}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def download(url, path):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=300) as r, open(path, "wb") as f:
        f.write(r.read())


def main():
    jar, neo, out = sys.argv[1], sys.argv[2], sys.argv[3]
    cfg = json.load(open(os.path.join(os.path.dirname(__file__), "mods.json"), encoding="utf-8"))
    mc, loader = cfg["minecraft"], cfg["loader"]
    q = "?loaders=" + urllib.parse.quote(json.dumps([loader])) + "&game_versions=" + urllib.parse.quote(json.dumps([mc]))

    resolved = {}   # project id -> (project, version, file, reason)
    missing = []

    def resolve(ref, why, wanted_by=None):
        try:
            project = get(f"{API}/project/{ref}")
        except Exception as e:  # noqa: BLE001
            missing.append(f"{ref} (нет на Modrinth: {e})")
            return
        if project["id"] in resolved:
            return
        versions = get(f"{API}/project/{project['id']}/version{q}")
        if not versions:
            missing.append(f"{project['title']} (нет версии для {loader} {mc})")
            return
        releases = [v for v in versions if v["version_type"] == "release"] or versions
        version = releases[0]
        file = next((f for f in version["files"] if f["primary"]), version["files"][0])
        reason = why if not wanted_by else f"нужен для {wanted_by}"
        resolved[project["id"]] = (project, version, file, reason)
        print(f"+ {project['title']} {version['version_number']} ({reason})")
        for dep in version["dependencies"]:
            if dep["dependency_type"] == "required" and dep.get("project_id"):
                resolve(dep["project_id"], None, project["title"])

    for m in cfg["mods"]:
        resolve(m["slug"], m["why"])

    os.makedirs(os.path.join(out, "server-mods"), exist_ok=True)
    files, lines, pw = [], [], []
    for project, version, file, reason in resolved.values():
        env = {"client": "required" if project["client_side"] != "unsupported" else "unsupported",
               "server": "required" if project["server_side"] != "unsupported" else "unsupported"}
        files.append({"path": f"mods/{file['filename']}", "hashes": {"sha1": file["hashes"]["sha1"], "sha512": file["hashes"]["sha512"]},
                      "env": env, "downloads": [file["url"]], "fileSize": file["size"]})
        if env["server"] == "required":
            target = os.path.join(out, "server-mods", file["filename"])
            download(file["url"], target)
            if hashlib.sha1(open(target, "rb").read()).hexdigest() != file["hashes"]["sha1"]:
                sys.exit(f"hash mismatch for {file['filename']}")
        pw_side = "both" if env["client"] == env["server"] == "required" else ("client" if env["server"] != "required" else "server")
        pw.append((project, version, file, pw_side))
        side = "клиент и сервер" if env["client"] == env["server"] == "required" else ("только клиент" if env["server"] != "required" else "только сервер")
        lines.append(f"| [{project['title']}](https://modrinth.com/mod/{project['slug']}) | {version['version_number']} | {reason} | {side} |")

    index = {"formatVersion": 1, "game": "minecraft", "versionId": os.environ.get("PACK_VERSION", "dev"), "name": "Story Mod",
             "summary": "Сюжетный мод и моды, с которыми он дружит", "files": files,
             "dependencies": {"minecraft": mc, "neoforge": neo}}
    with zipfile.ZipFile(os.path.join(out, "storymod.mrpack"), "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, ensure_ascii=False, indent=1))
        z.write(jar, "overrides/mods/storymod.jar")
    with open(os.path.join(out, "mods.md"), "w", encoding="utf-8") as f:
        f.write("| Мод | Версия | Зачем | Где нужен |\n| --- | --- | --- | --- |\n" + "\n".join(sorted(lines)) + "\n")
        if missing:
            f.write("\nПропущены: " + "; ".join(missing) + "\n")
    write_packwiz(os.path.join(out, "packwiz"), jar, mc, neo, pw)
    print("missing:", missing)
    print(len(files), "mods in the pack")


def toml_str(v):
    return json.dumps(v, ensure_ascii=False)


def write_packwiz(root, jar, mc, neo, mods):
    """packwiz format: pack.toml -> index.toml -> mods/*.pw.toml (download links) + mods/storymod.jar (hosted with the pack)."""
    os.makedirs(os.path.join(root, "mods"), exist_ok=True)
    index = []
    for project, version, file, side in mods:
        name = f"mods/{project['slug']}.pw.toml"
        body = (f"name = {toml_str(project['title'])}\nfilename = {toml_str(file['filename'])}\nside = {toml_str(side)}\n\n"
                f"[download]\nurl = {toml_str(file['url'])}\nhash-format = \"sha512\"\nhash = {toml_str(file['hashes']['sha512'])}\n\n"
                f"[update.modrinth]\nmod-id = {toml_str(project['id'])}\nversion = {toml_str(version['id'])}\n")
        open(os.path.join(root, name), "w", encoding="utf-8").write(body)
        index.append((name, hashlib.sha256(body.encode()).hexdigest(), True))
    data = open(jar, "rb").read()
    open(os.path.join(root, "mods/storymod.jar"), "wb").write(data)
    index.append(("mods/storymod.jar", hashlib.sha256(data).hexdigest(), False))
    idx = 'hash-format = "sha256"\n'
    for name, h, meta in sorted(index):
        idx += f"\n[[files]]\nfile = {toml_str(name)}\nhash = {toml_str(h)}\n" + ("metafile = true\n" if meta else "")
    open(os.path.join(root, "index.toml"), "w", encoding="utf-8").write(idx)
    pack = (f'name = "Story Mod"\nversion = {toml_str(os.environ.get("PACK_VERSION", "dev"))}\npack-format = "packwiz:1.1.0"\n\n'
            f'[index]\nfile = "index.toml"\nhash-format = "sha256"\nhash = {toml_str(hashlib.sha256(idx.encode()).hexdigest())}\n\n'
            f'[versions]\nminecraft = {toml_str(mc)}\nneoforge = {toml_str(neo)}\n')
    open(os.path.join(root, "pack.toml"), "w", encoding="utf-8").write(pack)


if __name__ == "__main__":
    main()
