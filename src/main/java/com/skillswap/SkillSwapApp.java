package com.skillswap;

import com.skillswap.model.PlatformMessage;
import com.skillswap.model.SwapRequest;
import com.skillswap.model.User;
import com.skillswap.web.Json;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkillSwapApp {
    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_PATH = 500;
    private static final long AUTH_WINDOW_MS = 10 * 60 * 1000L;
    private static final int AUTH_MAX_ATTEMPTS = 15;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());
    private final SkillSwapPlatform platform = new SkillSwapPlatform();
    private final Path staticRoot;
    private final Map<String, ArrayDeque<Long>> authAttempts = new ConcurrentHashMap<>();

    public SkillSwapApp(Path staticRoot) {
        this.staticRoot = staticRoot;
        platform.seed();
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            port = Integer.parseInt(portEnv);
        }
        Path root = resolveStatic();
        SkillSwapApp app = new SkillSwapApp(root);
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", app::handle);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        System.out.println("SkillSwap running on http://0.0.0.0:" + port);
        System.out.println("Static files: " + root.toAbsolutePath());
        System.out.println("Demo admin: admin@skillswap.local / admin123");
        server.start();
    }

    private static Path resolveStatic() {
        String env = System.getenv("SKILLSWAP_STATIC");
        List<Path> candidates = new ArrayList<>();
        if (env != null && !env.isBlank()) {
            candidates.add(Path.of(env));
        }
        candidates.add(Path.of("src/main/resources/static"));
        candidates.add(Path.of("static"));
        candidates.add(Path.of("/app/static"));
        for (Path path : candidates) {
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return Path.of("src/main/resources/static");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                applyCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            String path = exchange.getRequestURI() == null ? "/" : exchange.getRequestURI().getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (path.length() > MAX_PATH) {
                safeError(exchange, 414, "Path too long");
                return;
            }
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else {
                serveStatic(exchange, path);
            }
        } catch (IllegalArgumentException e) {
            safeError(exchange, 400, e.getMessage() == null ? "Bad request" : e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            safeError(exchange, 500, "Server error");
        }
    }

    private static void safeError(HttpExchange exchange, int status, String message) {
        try {
            if (exchange.getResponseCode() == -1) {
                writeJson(exchange, status, Map.of("error", message));
            }
        } catch (Exception ignored) {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if ((method.equals("POST") || method.equals("PUT")) && !hasJsonContentType(exchange)) {
            writeJson(exchange, 415, Map.of("error", "Content-Type must be application/json"));
            return;
        }
        User me = platform.userForSession(cookie(exchange, "SKILLSWAP_SID"));
        Map<String, String> query = query(exchange);

        if (path.equals("/api/health") && method.equals("GET")) {
            writeJson(exchange, 200, Map.of("ok", true));
            return;
        }
        if (path.equals("/api/stats") && method.equals("GET")) {
            writeJson(exchange, 200, platform.stats());
            return;
        }
        if (path.equals("/api/messages") && method.equals("GET")) {
            writeJson(exchange, 200, platform.messages().stream().map(this::messageJson).toList());
            return;
        }
        if (path.equals("/api/register") && method.equals("POST")) {
            if (authRateLimited(exchange)) {
                writeJson(exchange, 429, Map.of("error", "Too many attempts. Try again in a few minutes."));
                return;
            }
            Map<String, Object> body = readJson(exchange);
            try {
                User user = platform.register(
                        Json.str(body, "name"),
                        Json.str(body, "email"),
                        Json.str(body, "password"),
                        Json.str(body, "location"),
                        Json.str(body, "profilePhoto"),
                        Json.str(body, "availability"),
                        Json.str(body, "bio"),
                        Json.stringList(body, "skillsOffered"),
                        Json.stringList(body, "skillsWanted"),
                        Json.bool(body, "isPublic", true));
                setSession(exchange, platform.createSession(user));
                writeJson(exchange, 200, userJson(user, user, true));
            } catch (IllegalArgumentException ex) {
                writeJson(exchange, 400, Map.of("error", ex.getMessage()));
            }
            return;
        }
        if (path.equals("/api/login") && method.equals("POST")) {
            if (authRateLimited(exchange)) {
                writeJson(exchange, 429, Map.of("error", "Too many attempts. Try again in a few minutes."));
                return;
            }
            Map<String, Object> body = readJson(exchange);
            try {
                User user = platform.login(Json.str(body, "email"), Json.str(body, "password"));
                setSession(exchange, platform.createSession(user));
                writeJson(exchange, 200, userJson(user, user, true));
            } catch (IllegalArgumentException ex) {
                writeJson(exchange, 400, Map.of("error", ex.getMessage()));
            }
            return;
        }
        if (path.equals("/api/logout") && method.equals("POST")) {
            platform.destroySession(cookie(exchange, "SKILLSWAP_SID"));
            Headers headers = exchange.getResponseHeaders();
            headers.add("Set-Cookie", "SKILLSWAP_SID=; " + cookieFlags(exchange).replace("Max-Age=604800", "Max-Age=0"));
            writeJson(exchange, 200, Map.of("ok", true));
            return;
        }
        if (path.equals("/api/me") && method.equals("GET")) {
            if (me == null) {
                writeJson(exchange, 401, Map.of("error", "Not signed in"));
                return;
            }
            writeJson(exchange, 200, userJson(me, me, true));
            return;
        }
        if (path.equals("/api/me") && method.equals("PUT")) {
            if (me == null) {
                writeJson(exchange, 401, Map.of("error", "Not signed in"));
                return;
            }
            Map<String, Object> body = readJson(exchange);
            User updated = platform.updateProfile(me,
                    Json.str(body, "name"),
                    Json.str(body, "location"),
                    Json.str(body, "profilePhoto"),
                    Json.str(body, "availability"),
                    Json.str(body, "bio"),
                    body.containsKey("skillsOffered") ? Json.stringList(body, "skillsOffered") : null,
                    body.containsKey("skillsWanted") ? Json.stringList(body, "skillsWanted") : null,
                    body.containsKey("isPublic") ? Json.bool(body, "isPublic", true) : null);
            writeJson(exchange, 200, userJson(updated, me, true));
            return;
        }
        if (path.equals("/api/users") && method.equals("GET")) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (User user : platform.publicUsers(me, query.get("q"), query.get("skill"))) {
                list.add(userJson(user, me, false));
            }
            writeJson(exchange, 200, list);
            return;
        }
        if (path.matches("/api/users/[^/]+") && method.equals("GET")) {
            String id = path.substring("/api/users/".length());
            User user = platform.getUser(id);
            if (user == null || user.isBanned) {
                writeJson(exchange, 404, Map.of("error", "User not found"));
                return;
            }
            boolean self = me != null && me.id.equals(user.id);
            if (!user.isPublic && !self && (me == null || !me.isAdmin)) {
                writeJson(exchange, 404, Map.of("error", "User not found"));
                return;
            }
            writeJson(exchange, 200, userJson(user, me, self || (me != null && me.isAdmin)));
            return;
        }
        if (path.equals("/api/swaps") && method.equals("GET")) {
            if (me == null) {
                writeJson(exchange, 401, Map.of("error", "Not signed in"));
                return;
            }
            writeJson(exchange, 200, platform.swapsFor(me).stream().map(this::swapJson).toList());
            return;
        }
        if (path.equals("/api/swaps") && method.equals("POST")) {
            if (me == null) {
                writeJson(exchange, 401, Map.of("error", "Not signed in"));
                return;
            }
            Map<String, Object> body = readJson(exchange);
            User to = platform.getUser(Json.str(body, "toUserId"));
            if (to == null) {
                writeJson(exchange, 404, Map.of("error", "User not found"));
                return;
            }
            try {
                SwapRequest created = platform.sendSwapRequest(me, to,
                        Json.str(body, "skillOffered"),
                        Json.str(body, "skillWanted"),
                        Json.str(body, "message"));
                writeJson(exchange, 200, swapJson(created));
            } catch (IllegalArgumentException ex) {
                writeJson(exchange, 400, Map.of("error", ex.getMessage()));
            }
            return;
        }
        if (path.matches("/api/swaps/[^/]+/accept") && method.equals("POST")) {
            mutateSwap(exchange, me, path, "accept");
            return;
        }
        if (path.matches("/api/swaps/[^/]+/reject") && method.equals("POST")) {
            mutateSwap(exchange, me, path, "reject");
            return;
        }
        if (path.matches("/api/swaps/[^/]+/finish") && method.equals("POST")) {
            if (me == null) {
                writeJson(exchange, 401, Map.of("error", "Not signed in"));
                return;
            }
            String id = path.split("/")[3];
            try {
                writeJson(exchange, 200, swapJson(platform.finish(me, id)));
            } catch (IllegalArgumentException ex) {
                writeJson(exchange, 400, Map.of("error", ex.getMessage()));
            }
            return;
        }
        if (path.matches("/api/swaps/[^/]+/feedback") && method.equals("POST")) {
            if (me == null) {
                writeJson(exchange, 401, Map.of("error", "Not signed in"));
                return;
            }
            String id = path.split("/")[3];
            Map<String, Object> body = readJson(exchange);
            try {
                writeJson(exchange, 200, swapJson(platform.addFeedback(me, id,
                        Json.num(body, "rating", 0), Json.str(body, "comment"))));
            } catch (IllegalArgumentException ex) {
                writeJson(exchange, 400, Map.of("error", ex.getMessage()));
            }
            return;
        }
        if (path.matches("/api/swaps/[^/]+") && method.equals("DELETE")) {
            if (me == null) {
                writeJson(exchange, 401, Map.of("error", "Not signed in"));
                return;
            }
            String id = path.substring("/api/swaps/".length());
            try {
                platform.deletePending(me, id);
                writeJson(exchange, 200, Map.of("ok", true));
            } catch (IllegalArgumentException ex) {
                writeJson(exchange, 400, Map.of("error", ex.getMessage()));
            }
            return;
        }

        if (path.startsWith("/api/admin/")) {
            if (me == null || !me.isAdmin) {
                writeJson(exchange, 403, Map.of("error", "Admin access required"));
                return;
            }
            if (path.equals("/api/admin/users") && method.equals("GET")) {
                writeJson(exchange, 200, platform.allUsers().stream().map(u -> userJson(u, me, true)).toList());
                return;
            }
            if (path.matches("/api/admin/users/[^/]+/ban") && method.equals("POST")) {
                try {
                    platform.ban(me, path.split("/")[4]);
                    writeJson(exchange, 200, Map.of("ok", true));
                } catch (IllegalArgumentException ex) {
                    writeJson(exchange, 400, Map.of("error", ex.getMessage()));
                }
                return;
            }
            if (path.matches("/api/admin/users/[^/]+/unban") && method.equals("POST")) {
                try {
                    platform.unban(me, path.split("/")[4]);
                    writeJson(exchange, 200, Map.of("ok", true));
                } catch (IllegalArgumentException ex) {
                    writeJson(exchange, 400, Map.of("error", ex.getMessage()));
                }
                return;
            }
            if (path.matches("/api/admin/users/[^/]+/remove-skill") && method.equals("POST")) {
                Map<String, Object> body = readJson(exchange);
                try {
                    platform.removeSkill(me, path.split("/")[4], Json.str(body, "skill"));
                    writeJson(exchange, 200, Map.of("ok", true));
                } catch (IllegalArgumentException ex) {
                    writeJson(exchange, 400, Map.of("error", ex.getMessage()));
                }
                return;
            }
            if (path.equals("/api/admin/swaps") && method.equals("GET")) {
                writeJson(exchange, 200, platform.allSwaps().stream().map(this::swapJson).toList());
                return;
            }
            if (path.equals("/api/admin/broadcast") && method.equals("POST")) {
                Map<String, Object> body = readJson(exchange);
                try {
                    writeJson(exchange, 200, messageJson(platform.broadcast(me, Json.str(body, "message"))));
                } catch (IllegalArgumentException ex) {
                    writeJson(exchange, 400, Map.of("error", ex.getMessage()));
                }
                return;
            }
            if (path.equals("/api/admin/reports/users.csv") && method.equals("GET")) {
                writeCsv(exchange, "users.csv", usersCsv());
                return;
            }
            if (path.equals("/api/admin/reports/swaps.csv") && method.equals("GET")) {
                writeCsv(exchange, "swaps.csv", swapsCsv());
                return;
            }
            if (path.equals("/api/admin/reports/feedback.csv") && method.equals("GET")) {
                writeCsv(exchange, "feedback.csv", feedbackCsv());
                return;
            }
        }

        writeJson(exchange, 404, Map.of("error", "Not found"));
    }

    private void mutateSwap(HttpExchange exchange, User me, String path, String action) throws IOException {
        if (me == null) {
            writeJson(exchange, 401, Map.of("error", "Not signed in"));
            return;
        }
        String id = path.split("/")[3];
        try {
            SwapRequest result = "accept".equals(action) ? platform.accept(me, id) : platform.reject(me, id);
            writeJson(exchange, 200, swapJson(result));
        } catch (IllegalArgumentException ex) {
            writeJson(exchange, 400, Map.of("error", ex.getMessage()));
        }
    }

    private Map<String, Object> userJson(User user, User viewer, boolean privateFields) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.id);
        map.put("name", user.name);
        map.put("initials", user.initials());
        map.put("location", user.location);
        map.put("profilePhoto", user.profilePhoto);
        map.put("availability", user.availability);
        map.put("bio", user.bio);
        map.put("skillsOffered", new ArrayList<>(user.skillsOffered));
        map.put("skillsWanted", new ArrayList<>(user.skillsWanted));
        map.put("isPublic", user.isPublic);
        map.put("rating", Math.round(platform.averageRating(user) * 10.0) / 10.0);
        map.put("ratingCount", platform.ratingCount(user));
        map.put("feedback", List.copyOf(user.feedbackList));
        if (privateFields) {
            map.put("email", user.email);
            map.put("isAdmin", user.isAdmin);
            map.put("isBanned", user.isBanned);
        }
        return map;
    }

    private Map<String, Object> swapJson(SwapRequest swap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", swap.id);
        map.put("fromId", swap.fromId);
        map.put("toId", swap.toId);
        map.put("fromName", swap.from != null ? swap.from.name : "");
        map.put("toName", swap.to != null ? swap.to.name : "");
        map.put("skillOffered", swap.skillOffered);
        map.put("skillWanted", swap.skillWanted);
        map.put("message", swap.message);
        map.put("status", swap.status);
        map.put("createdAt", TIME.format(swap.createdAt));
        map.put("fromRating", swap.fromRating);
        map.put("fromComment", swap.fromComment);
        map.put("toRating", swap.toRating);
        map.put("toComment", swap.toComment);
        if (swap.contactUnlocked()) {
            map.put("fromEmail", swap.from != null ? swap.from.email : "");
            map.put("toEmail", swap.to != null ? swap.to.email : "");
        }
        return map;
    }

    private Map<String, Object> messageJson(PlatformMessage message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", message.id);
        map.put("text", message.text);
        map.put("createdAt", TIME.format(message.createdAt));
        return map;
    }

    private String usersCsv() {
        StringBuilder out = new StringBuilder("name,email,location,public,banned,skills_offered,skills_wanted,feedbacks\n");
        for (User user : platform.allUsers()) {
            out.append(csv(user.name)).append(',')
                    .append(csv(user.email)).append(',')
                    .append(csv(user.location)).append(',')
                    .append(user.isPublic).append(',')
                    .append(user.isBanned).append(',')
                    .append(csv(String.join("|", user.skillsOffered))).append(',')
                    .append(csv(String.join("|", user.skillsWanted))).append(',')
                    .append(csv(String.join("|", user.feedbackList))).append('\n');
        }
        return out.toString();
    }

    private String swapsCsv() {
        StringBuilder out = new StringBuilder("from,to,offered,wanted,status,created\n");
        for (SwapRequest swap : platform.allSwaps()) {
            out.append(csv(swap.from != null ? swap.from.name : "")).append(',')
                    .append(csv(swap.to != null ? swap.to.name : "")).append(',')
                    .append(csv(swap.skillOffered)).append(',')
                    .append(csv(swap.skillWanted)).append(',')
                    .append(csv(swap.status)).append(',')
                    .append(csv(TIME.format(swap.createdAt))).append('\n');
        }
        return out.toString();
    }

    private String feedbackCsv() {
        StringBuilder out = new StringBuilder("swap,author,about,rating,comment\n");
        for (SwapRequest swap : platform.allSwaps()) {
            if (swap.fromRating != null) {
                out.append(csv(swap.id)).append(',').append(csv(swap.fromNameSafe())).append(',')
                        .append(csv(swap.toNameSafe())).append(',').append(swap.fromRating).append(',')
                        .append(csv(swap.fromComment)).append('\n');
            }
            if (swap.toRating != null) {
                out.append(csv(swap.id)).append(',').append(csv(swap.toNameSafe())).append(',')
                        .append(csv(swap.fromNameSafe())).append(',').append(swap.toRating).append(',')
                        .append(csv(swap.toComment)).append('\n');
            }
        }
        return out.toString();
    }

    private static String csv(String value) {
        String v = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/")) {
            path = "/index.html";
        }
        if (path.indexOf('\0') >= 0 || path.contains("..")) {
            writeJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }
        Path root = staticRoot.toAbsolutePath().normalize();
        String relative = path.startsWith("/") ? path.substring(1) : path;
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) {
            writeJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }
        if (!Files.isRegularFile(file)) {
            Path index = staticRoot.resolve("index.html");
            if (Files.isRegularFile(index) && !path.contains(".")) {
                file = index;
            } else {
                writeJson(exchange, 404, Map.of("error", "Not found"));
                return;
            }
        }
        byte[] bytes = Files.readAllBytes(file);
        String type = contentType(file.getFileName().toString());
        Headers headers = exchange.getResponseHeaders();
        applyCors(exchange);
        applySecurityHeaders(headers);
        applyTransportHeaders(exchange);
        headers.set("Content-Type", type);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String contentType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static void applyCors(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (originAllowed(exchange, origin)) {
            if (origin != null && !origin.isBlank()) {
                headers.set("Access-Control-Allow-Origin", origin);
                headers.set("Vary", "Origin");
                headers.set("Access-Control-Allow-Credentials", "true");
            }
            headers.set("Access-Control-Allow-Headers", "Content-Type");
            headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        }
    }

    private static boolean originAllowed(HttpExchange exchange, String origin) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (origin.length() > 200 || origin.contains("\r") || origin.contains("\n")) {
            return false;
        }
        String host = exchange.getRequestHeaders().getFirst("Host");
        try {
            URI uri = URI.create(origin);
            String originHost = uri.getHost();
            if (originHost != null && host != null) {
                String authority = uri.getPort() > 0 ? originHost + ":" + uri.getPort() : originHost;
                if (host.equalsIgnoreCase(originHost) || host.equalsIgnoreCase(authority)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        String extra = System.getenv("SKILLSWAP_CORS");
        if (extra != null) {
            for (String allowed : extra.split(",")) {
                if (origin.equals(allowed.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void applySecurityHeaders(Headers headers) {
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "same-origin");
        headers.set("Cache-Control", "no-store");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        headers.set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' https: data:; connect-src 'self' https:; "
                        + "object-src 'none'; base-uri 'self'; form-action 'self'");
    }

    private static void applyTransportHeaders(HttpExchange exchange) {
        String proto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
        if ("https".equalsIgnoreCase(proto)) {
            exchange.getResponseHeaders().set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }

    private static boolean hasJsonContentType(HttpExchange exchange) {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        return type != null && type.toLowerCase(Locale.ROOT).contains("application/json");
    }

    private boolean authRateLimited(HttpExchange exchange) {
        String ip = clientIp(exchange);
        long now = System.currentTimeMillis();
        if (authAttempts.size() > 10_000) {
            authAttempts.clear();
        }
        ArrayDeque<Long> hits = authAttempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && now - hits.peekFirst() > AUTH_WINDOW_MS) {
                hits.pollFirst();
            }
            if (hits.size() >= AUTH_MAX_ATTEMPTS) {
                return true;
            }
            hits.addLast(now);
            return false;
        }
    }

    private static String clientIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && first.length() <= 64) {
                return first;
            }
        }
        return exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    private static String cookieFlags(HttpExchange exchange) {
        String proto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
        boolean secure = "https".equalsIgnoreCase(proto)
                || "true".equalsIgnoreCase(System.getenv("COOKIE_SECURE"));
        String sameSite = System.getenv("COOKIE_SAMESITE");
        if (sameSite == null || sameSite.isBlank()) {
            sameSite = "Lax";
        } else if (sameSite.equalsIgnoreCase("None")) {
            sameSite = "None";
            secure = true;
        } else if (sameSite.equalsIgnoreCase("Strict")) {
            sameSite = "Strict";
        } else {
            sameSite = "Lax";
        }
        String flags = "Path=/; HttpOnly; SameSite=" + sameSite + "; Max-Age=604800";
        if (secure) {
            flags += "; Secure";
        }
        return flags;
    }

    private static void setSession(HttpExchange exchange, String sid) {
        exchange.getResponseHeaders().add("Set-Cookie",
                "SKILLSWAP_SID=" + sid + "; " + cookieFlags(exchange));
    }

    private static String cookie(HttpExchange exchange, String name) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String header : cookies) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> map = new LinkedHashMap<>();
        String raw = exchange.getRequestURI() == null ? null : exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        if (raw.length() > 2048) {
            throw new IllegalArgumentException("Query too long");
        }
        for (String part : raw.split("&")) {
            String[] kv = part.split("=", 2);
            String key = urlDecode(kv[0]);
            String value = kv.length > 1 ? urlDecode(kv[1]) : "";
            map.put(key, value);
        }
        return map;
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid query string");
        }
    }

    private static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] raw = in.readNBytes(MAX_BODY + 1);
            if (raw.length > MAX_BODY) {
                throw new IllegalArgumentException("Request too large");
            }
            String text = new String(raw, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return new LinkedHashMap<>();
            }
            try {
                return Json.object(text);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid JSON");
            }
        }
    }

    private static void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        applyCors(exchange);
        Headers headers = exchange.getResponseHeaders();
        applySecurityHeaders(headers);
        applyTransportHeaders(exchange);
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void writeCsv(HttpExchange exchange, String filename, String csv) throws IOException {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        applyCors(exchange);
        Headers headers = exchange.getResponseHeaders();
        applySecurityHeaders(headers);
        applyTransportHeaders(exchange);
        headers.set("Content-Type", "text/csv; charset=utf-8");
        headers.set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
