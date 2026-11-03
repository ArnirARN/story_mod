package com.arnir.storymod.sync;

import com.arnir.storymod.StoryMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the game's mods folder in step with the published mod pack, so a plain launcher (TLauncher etc.)
 * only needs NeoForge and this one jar. On start it reads the pack, downloads the mods this side needs
 * (client or server), updates changed ones, removes ones it installed that left the pack, and updates
 * this mod itself. New files load on the next start, so the player is asked to restart.
 * <p>
 * Runs only in a real (production) game. Turn it off with {@code enabled=false} in config/storymod-pack.properties.
 */
public final class PackSync {
    public static final String DEFAULT_PACK_URL = "https://raw.githubusercontent.com/ArnirARN/story_mod/pack/pack.toml";

    public enum Status { OFF, RUNNING, UP_TO_DATE, RESTART, NEEDS_NEOFORGE, FAILED }

    private static volatile Status status = Status.OFF;
    private static volatile String detail = "";
    private static volatile int done;
    private static volatile int total;

    private static final Pattern KV = Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*$");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();

    private PackSync() {
    }

    public static Status status() {
        return status;
    }

    public static String detail() {
        return detail;
    }

    public static int done() {
        return done;
    }

    public static int total() {
        return total;
    }

    /** Called from the mod constructor. The client syncs in the background; a dedicated server waits for it. */
    public static void start() {
        if (!FMLEnvironment.production) {
            return;
        }
        Settings settings = Settings.load();
        if (!settings.enabled) {
            StoryMod.LOGGER.info("Pack sync is turned off in config/storymod-pack.properties");
            return;
        }
        status = Status.RUNNING;
        boolean server = FMLEnvironment.dist == Dist.DEDICATED_SERVER;
        Runnable job = () -> {
            try {
                run(settings.packUrl, server ? "server" : "client");
            } catch (Exception e) {
                status = Status.FAILED;
                detail = e.getClass().getSimpleName() + ": " + e.getMessage();
                StoryMod.LOGGER.warn("Pack sync failed, playing with the mods already installed", e);
            }
        };
        if (server) {
            job.run();
            if (status == Status.RESTART) {
                StoryMod.LOGGER.warn("==================================================================");
                StoryMod.LOGGER.warn("Сборка обновлена: {}. Сервер остановлен, запустите его снова.", detail);
                StoryMod.LOGGER.warn("The mod pack was updated: {}. Start the server again.", detail);
                StoryMod.LOGGER.warn("==================================================================");
                try {
                    // The start scripts see this file and start the server again by themselves.
                    java.nio.file.Files.writeString(FMLPaths.GAMEDIR.get().resolve("storymod-restart.flag"), detail);
                } catch (java.io.IOException ignored) {
                    // Without the flag the server simply stays stopped.
                }
                System.exit(0);
            }
        } else {
            Thread thread = new Thread(job, "storymod-pack-sync");
            thread.setDaemon(true);
            thread.start();
        }
    }

    // ------------------------------------------------------------------ the sync itself

    record Entry(String path, String filename, String side, String url, String hashFormat, String hash) {
    }

    static void run(String packUrl, String side) throws Exception {
        String base = packUrl.substring(0, packUrl.lastIndexOf('/') + 1);
        Map<String, String> pack = parse(fetchText(packUrl));
        String neededNeo = pack.getOrDefault("neoforge", "");
        List<Map<String, String>> index = parseFiles(fetchText(base + pack.getOrDefault("file", "index.toml")));

        List<Entry> entries = new ArrayList<>();
        Entry self = null;
        for (Map<String, String> f : index) {
            String path = f.get("file");
            if ("true".equals(f.get("metafile"))) {
                Map<String, String> meta = parse(fetchText(base + path));
                entries.add(new Entry(path, meta.get("filename"), meta.getOrDefault("side", "both"), meta.get("url"),
                        meta.getOrDefault("hash-format", "sha512"), meta.get("hash")));
            } else if (path.endsWith("storymod.jar")) {
                self = new Entry(path, "storymod.jar", "both", base + path, "sha256", f.get("hash"));
            }
        }

        String installedNeo = FMLLoader.versionInfo().neoForgeVersion();
        if (!neededNeo.isEmpty() && compareVersions(installedNeo, neededNeo) < 0) {
            status = Status.NEEDS_NEOFORGE;
            detail = neededNeo;
            StoryMod.LOGGER.warn("The mod pack needs NeoForge {} or newer (installed {}); mods were not downloaded", neededNeo, installedNeo);
            return;
        }

        Path mods = FMLPaths.MODSDIR.get();
        Path manifestFile = FMLPaths.CONFIGDIR.get().resolve("storymod-pack-installed.json");
        Map<String, String> installed = readManifest(manifestFile);
        Map<String, String> nowInstalled = new LinkedHashMap<>();
        List<Entry> wanted = entries.stream().filter(e -> e.side().equals("both") || e.side().equals(side)).toList();
        total = wanted.size() + (self != null ? 1 : 0);
        int changed = 0;

        for (Entry e : wanted) {
            if (!safeName(e.filename()) || e.url() == null || e.hash() == null) {
                throw new IOException("bad pack entry " + e.path());
            }
            Path target = mods.resolve(e.filename());
            String previous = installed.get(e.path());
            if (previous != null && !previous.equals(e.filename()) && safeName(previous)) {
                deleteQuietly(mods.resolve(previous));
            }
            if (!(Files.exists(target) && e.hash().equalsIgnoreCase(hash(target, e.hashFormat())))) {
                download(e.url(), target, e.hashFormat(), e.hash());
                changed++;
            }
            nowInstalled.put(e.path(), e.filename());
            done++;
        }
        // Mods this sync installed earlier that are no longer in the pack (or not for this side).
        for (Map.Entry<String, String> old : installed.entrySet()) {
            if (!nowInstalled.containsKey(old.getKey()) && !nowInstalled.containsValue(old.getValue()) && safeName(old.getValue())) {
                if (deleteQuietly(mods.resolve(old.getValue()))) {
                    changed++;
                }
            }
        }
        writeManifest(manifestFile, nowInstalled);

        if (self != null) {
            if (updateSelf(mods, self)) {
                changed++;
            }
            done++;
        }
        detail = String.valueOf(changed);
        status = changed > 0 ? Status.RESTART : Status.UP_TO_DATE;
        StoryMod.LOGGER.info("Pack sync finished: {} file(s) changed", changed);
    }

    /** Replaces this mod's own jar when the pack has a different build. */
    private static boolean updateSelf(Path mods, Entry self) throws Exception {
        var file = ModList.get().getModFileById(StoryMod.MODID);
        if (file == null) {
            return false;
        }
        Path own = file.getFile().getFilePath();
        if (!Files.isRegularFile(own) || !own.toAbsolutePath().getParent().equals(mods.toAbsolutePath())) {
            return false;
        }
        if (self.hash().equalsIgnoreCase(hash(own, "sha256"))) {
            return false;
        }
        Path fresh = mods.resolve("storymod-" + self.hash().substring(0, 10) + ".jar");
        download(self.url(), fresh, "sha256", self.hash());
        try {
            Files.delete(own);
        } catch (IOException e) {
            // Two copies would stop the game from starting: keep the old one.
            deleteQuietly(fresh);
            StoryMod.LOGGER.warn("Could not replace {}, update storymod.jar by hand", own, e);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    public static boolean safeName(String name) {
        return name != null && name.endsWith(".jar") && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }

    private static String fetchText(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "storymod-pack-sync").build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode() + " for " + url);
        }
        return res.body();
    }

    private static void download(String url, Path target, String hashFormat, String expected) throws Exception {
        if (!url.startsWith("https://")) {
            throw new IOException("refusing non-https download " + url);
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10))
                .header("User-Agent", "storymod-pack-sync").build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode() + " for " + url);
        }
        try (InputStream in = res.body()) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }
        String actual = hash(part, hashFormat);
        if (!actual.equalsIgnoreCase(expected)) {
            deleteQuietly(part);
            throw new IOException("checksum mismatch for " + target.getFileName());
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        StoryMod.LOGGER.info("Pack sync: installed {}", target.getFileName());
    }

    static String hash(Path file, String format) throws Exception {
        String algo = switch (format) {
            case "sha1" -> "SHA-1";
            case "sha256" -> "SHA-256";
            case "sha512" -> "SHA-512";
            default -> throw new IOException("unknown hash format " + format);
        };
        MessageDigest md = MessageDigest.getInstance(algo);
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static boolean deleteQuietly(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            StoryMod.LOGGER.warn("Could not delete {}", file, e);
            return false;
        }
    }

    /** Reads the simple key = "value" lines used by packwiz files (tables are flattened, later keys win). */
    public static Map<String, String> parse(String toml) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : toml.split("\\R")) {
            Matcher m = KV.matcher(line);
            if (m.matches()) {
                out.put(m.group(1), m.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
            } else if (line.trim().matches("[A-Za-z0-9_-]+\\s*=\\s*(true|false)")) {
                String[] kv = line.split("=");
                out.put(kv[0].trim(), kv[1].trim());
            }
        }
        return out;
    }

    /** Splits index.toml into its [[files]] tables. */
    public static List<Map<String, String>> parseFiles(String toml) {
        List<Map<String, String>> out = new ArrayList<>();
        for (String chunk : toml.split("\\[\\[files]]")) {
            Map<String, String> m = parse(chunk);
            if (m.containsKey("file")) {
                out.add(m);
            }
        }
        return out;
    }

    public static int compareVersions(String a, String b) {
        String[] x = a.split("[.-]"), y = b.split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? number(x[i]) : 0, yi = i < y.length ? number(y[i]) : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Map<String, String> readManifest(Path file) {
        try {
            if (Files.exists(file)) {
                JsonObject obj = new Gson().fromJson(Files.readString(file), JsonObject.class);
                Map<String, String> out = new LinkedHashMap<>();
                obj.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
                return out;
            }
        } catch (Exception e) {
            StoryMod.LOGGER.warn("Could not read {}", file, e);
        }
        return new LinkedHashMap<>();
    }

    private static void writeManifest(Path file, Map<String, String> map) throws IOException {
        JsonObject obj = new JsonObject();
        map.forEach(obj::addProperty);
        Files.createDirectories(file.getParent());
        Files.writeString(file, new Gson().toJson(obj));
    }

    private record Settings(boolean enabled, String packUrl) {
        static Settings load() {
            Path file = FMLPaths.CONFIGDIR.get().resolve("storymod-pack.properties");
            java.util.Properties p = new java.util.Properties();
            try {
                if (Files.exists(file)) {
                    try (var r = Files.newBufferedReader(file)) {
                        p.load(r);
                    }
                } else {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, "# Автозагрузка модов сборки при запуске игры / Download the mod pack on start\n"
                            + "enabled=true\npack_url=" + DEFAULT_PACK_URL + "\n");
                }
            } catch (IOException e) {
                StoryMod.LOGGER.warn("Could not read {}", file, e);
            }
            return new Settings(!"false".equalsIgnoreCase(p.getProperty("enabled", "true").trim()),
                    p.getProperty("pack_url", DEFAULT_PACK_URL).trim());
        }
    }
}
