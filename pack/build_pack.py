"""Builds storymod.mrpack and a server mod folder from pack/mods.json using the Modrinth API.

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
    files, lines = [], []
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
    print("missing:", missing)
    print(len(files), "mods in the pack")


if __name__ == "__main__":
    main()
