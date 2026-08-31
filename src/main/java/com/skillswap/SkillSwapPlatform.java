package com.skillswap;

import com.skillswap.model.PlatformMessage;
import com.skillswap.model.SwapRequest;
import com.skillswap.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SkillSwapPlatform {
    public final Admin admin = new Admin();
    private final Map<String, User> usersById = new LinkedHashMap<>();
    private final Map<String, User> usersByEmail = new LinkedHashMap<>();
    private final Map<String, SwapRequest> swaps = new LinkedHashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public synchronized User register(String name, String email, String password, String location,
                                      String photo, String availability, String bio,
                                      List<String> offered, List<String> wanted, boolean isPublic) {
        name = requireLength("Name", name, 1, 80);
        email = normalizeEmail(email);
        if (usersByEmail.containsKey(email)) {
            throw new IllegalArgumentException("An account with that email already exists.");
        }
        requirePassword(password);
        User user = new User(name);
        user.email = email;
        user.salt = randomSalt();
        user.passwordHash = hash(password, user.salt);
        user.location = clip(location, 80);
        user.profilePhoto = sanitizeUrl(photo);
        user.availability = clip(availability, 80);
        user.bio = clip(bio, 2000);
        user.isPublic = isPublic;
        addSkills(user.skillsOffered, offered);
        addSkills(user.skillsWanted, wanted);
        usersById.put(user.id, user);
        usersByEmail.put(user.email, user);
        return user;
    }

    public synchronized User login(String email, String password) {
        if (email == null || password == null) {
            throw new IllegalArgumentException("Invalid email or password.");
        }
        User user = usersByEmail.get(email.trim().toLowerCase(Locale.ROOT));
        String salt = user != null ? user.salt : "0000000000000000";
        String expected = user != null ? user.passwordHash : hash("invalid", salt);
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                hash(password, salt).getBytes(StandardCharsets.UTF_8));
        if (user == null || !matches) {
            throw new IllegalArgumentException("Invalid email or password.");
        }
        if (user.isBanned) {
            throw new IllegalArgumentException("This account has been banned.");
        }
        return user;
    }

    public String createSession(User user) {
        String sid = UUID.randomUUID().toString();
        sessions.put(sid, user.id);
        return sid;
    }

    public void destroySession(String sid) {
        if (sid != null) {
            sessions.remove(sid);
        }
    }

    public synchronized User userForSession(String sid) {
        if (sid == null || sid.length() > 80) {
            return null;
        }
        String id = sessions.get(sid);
        if (id == null) {
            return null;
        }
        User user = usersById.get(id);
        if (user == null || user.isBanned) {
            sessions.remove(sid);
            return null;
        }
        return user;
    }

    public synchronized User updateProfile(User user, String name, String location, String photo,
                                           String availability, String bio, List<String> offered,
                                           List<String> wanted, Boolean isPublic) {
        if (name != null && !name.isBlank()) {
            user.name = requireLength("Name", name, 1, 80);
        }
        if (location != null) {
            user.location = clip(location, 80);
        }
        if (photo != null) {
            user.profilePhoto = sanitizeUrl(photo);
        }
        if (availability != null) {
            user.availability = clip(availability, 80);
        }
        if (bio != null) {
            user.bio = clip(bio, 2000);
        }
        if (offered != null) {
            user.skillsOffered.clear();
            addSkills(user.skillsOffered, offered);
        }
        if (wanted != null) {
            user.skillsWanted.clear();
            addSkills(user.skillsWanted, wanted);
        }
        if (isPublic != null) {
            user.isPublic = isPublic;
        }
        return user;
    }

    public synchronized User getUser(String id) {
        if (id == null || id.length() > 80) {
            return null;
        }
        return usersById.get(id);
    }

    public synchronized List<User> publicUsers(User viewer, String query, String skill) {
        String q = clip(query, 80).toLowerCase(Locale.ROOT);
        String sk = clip(skill, 80).toLowerCase(Locale.ROOT);
        List<User> result = new ArrayList<>();
        for (User user : usersById.values()) {
            if (user.isBanned) {
                continue;
            }
            if (viewer != null && viewer.id.equals(user.id)) {
                continue;
            }
            if (!user.isPublic && (viewer == null || !viewer.isAdmin)) {
                continue;
            }
            if (!q.isEmpty()) {
                String hay = (user.name + " " + user.location + " " + user.bio + " "
                        + user.skillsOffered + " " + user.skillsWanted).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) {
                    continue;
                }
            }
            if (!sk.isEmpty()) {
                boolean match = user.skillsOffered.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(sk))
                        || user.skillsWanted.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(sk));
                if (!match) {
                    continue;
                }
            }
            result.add(user);
        }
        result.sort(Comparator.comparing(u -> (u.name == null ? "" : u.name.toLowerCase(Locale.ROOT))));
        return result;
    }

    public synchronized List<User> allUsers() {
        return new ArrayList<>(usersById.values());
    }

    public static void sendSwapRequest(User from, User to) {
        SwapRequest request = new SwapRequest(from, to);
        to.swapRequests.add(request);
        from.swapRequests.add(request);
        System.out.println(from.name + " sent a swap request to " + to.name);
    }

    public static void acceptSwapRequest(User user, int index) {
        if (index >= 0 && index < user.swapRequests.size()) {
            user.swapRequests.get(index).status = "accepted";
            user.swapRequests.get(index).resolvedAt = Instant.now();
            System.out.println(user.name + " accepted swap request from "
                    + user.swapRequests.get(index).from.name);
        }
    }

    public synchronized SwapRequest sendSwapRequest(User from, User to, String skillOffered,
                                                    String skillWanted, String message) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("User not found.");
        }
        if (from.isBanned) {
            throw new IllegalArgumentException("This account has been banned.");
        }
        if (from.id.equals(to.id)) {
            throw new IllegalArgumentException("You cannot swap with yourself.");
        }
        if (to.isBanned) {
            throw new IllegalArgumentException("That member is no longer available.");
        }
        if (!to.isPublic) {
            throw new IllegalArgumentException("That profile is private.");
        }
        skillOffered = clip(skillOffered, 40);
        skillWanted = clip(skillWanted, 40);
        message = clip(message, 500);
        if (skillOffered.isEmpty() || skillWanted.isEmpty()) {
            throw new IllegalArgumentException("Pick a skill to offer and a skill to learn.");
        }
        final String finalSkillOffered = skillOffered;
        final String finalSkillWanted = skillWanted;
        boolean duplicate = swaps.values().stream().anyMatch(s ->
                s.isPending() && s.fromId.equals(from.id) && s.toId.equals(to.id)
                        && finalSkillOffered.equalsIgnoreCase(s.skillOffered)
                        && finalSkillWanted.equalsIgnoreCase(s.skillWanted));
        if (duplicate) {
            throw new IllegalArgumentException("You already have a pending request like this.");
        }
        SwapRequest request = new SwapRequest(from, to);
        request.skillOffered = skillOffered;
        request.skillWanted = skillWanted;
        request.message = message;
        to.swapRequests.add(request);
        from.swapRequests.add(request);
        swaps.put(request.id, request);
        System.out.println(from.name + " sent a swap request to " + to.name);
        return request;
    }

    public synchronized SwapRequest accept(User user, String swapId) {
        SwapRequest request = requireSwap(swapId);
        if (!request.toId.equals(user.id)) {
            throw new IllegalArgumentException("Only the recipient can accept this request.");
        }
        if (!request.isPending()) {
            throw new IllegalArgumentException("This request is no longer pending.");
        }
        request.status = "accepted";
        request.resolvedAt = Instant.now();
        return request;
    }

    public synchronized SwapRequest reject(User user, String swapId) {
        SwapRequest request = requireSwap(swapId);
        if (!request.toId.equals(user.id)) {
            throw new IllegalArgumentException("Only the recipient can reject this request.");
        }
        if (!request.isPending()) {
            throw new IllegalArgumentException("This request is no longer pending.");
        }
        request.status = "rejected";
        request.resolvedAt = Instant.now();
        return request;
    }

    public synchronized void deletePending(User user, String swapId) {
        SwapRequest request = requireSwap(swapId);
        if (!request.fromId.equals(user.id)) {
            throw new IllegalArgumentException("Only the sender can withdraw this request.");
        }
        if (!request.isPending()) {
            throw new IllegalArgumentException("Accepted requests cannot be deleted.");
        }
        request.status = "cancelled";
        request.resolvedAt = Instant.now();
    }

    public synchronized SwapRequest finish(User user, String swapId) {
        SwapRequest request = requireSwap(swapId);
        if (!request.fromId.equals(user.id) && !request.toId.equals(user.id)) {
            throw new IllegalArgumentException("You are not part of this swap.");
        }
        if (!"accepted".equals(request.status) && !request.canLeaveFeedback()) {
            throw new IllegalArgumentException("Only an accepted swap can be marked finished.");
        }
        if ("accepted".equals(request.status)) {
            request.status = "finished";
            request.resolvedAt = Instant.now();
        }
        return request;
    }

    public synchronized SwapRequest addFeedback(User user, String swapId, int rating, String comment) {
        SwapRequest request = requireSwap(swapId);
        if (!request.canLeaveFeedback()) {
            throw new IllegalArgumentException("Feedback is only allowed after both of you mark the skill as finished.");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        String note = clip(comment, 400);
        if (request.fromId.equals(user.id)) {
            if (request.fromRating != null) {
                throw new IllegalArgumentException("You already left feedback.");
            }
            request.fromRating = rating;
            request.fromComment = note;
            if (request.to != null) {
                request.to.addFeedback(user.name + " ★" + rating + " — " + note);
            }
        } else if (request.toId.equals(user.id)) {
            if (request.toRating != null) {
                throw new IllegalArgumentException("You already left feedback.");
            }
            request.toRating = rating;
            request.toComment = note;
            if (request.from != null) {
                request.from.addFeedback(user.name + " ★" + rating + " — " + note);
            }
        } else {
            throw new IllegalArgumentException("You are not part of this swap.");
        }
        if (request.fromRating != null && request.toRating != null) {
            request.status = "completed";
        }
        return request;
    }

    public synchronized List<SwapRequest> swapsFor(User user) {
        return swaps.values().stream()
                .filter(s -> s.fromId.equals(user.id) || s.toId.equals(user.id))
                .sorted(Comparator.comparing((SwapRequest s) -> s.createdAt).reversed())
                .collect(Collectors.toList());
    }

    public synchronized List<SwapRequest> allSwaps() {
        return swaps.values().stream()
                .sorted(Comparator.comparing((SwapRequest s) -> s.createdAt).reversed())
                .collect(Collectors.toList());
    }

    public SwapRequest getSwap(String id) {
        return swaps.get(id);
    }

    public synchronized void ban(User actor, String userId) {
        requireAdmin(actor);
        User target = requireUser(userId);
        if (target.isAdmin) {
            throw new IllegalArgumentException("Admins cannot be banned.");
        }
        admin.banUser(target);
        sessions.entrySet().removeIf(e -> e.getValue().equals(target.id));
    }

    public synchronized void unban(User actor, String userId) {
        requireAdmin(actor);
        admin.unbanUser(requireUser(userId));
    }

    public synchronized void removeSkill(User actor, String userId, String skill) {
        requireAdmin(actor);
        User target = requireUser(userId);
        admin.rejectInappropriateSkill(target, skill);
    }

    public synchronized PlatformMessage broadcast(User actor, String message) {
        requireAdmin(actor);
        String text = clip(message, 280);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty.");
        }
        return admin.sendPlatformMessage(text);
    }

    public synchronized List<PlatformMessage> messages() {
        return new ArrayList<>(admin.platformMessages);
    }

    public synchronized double averageRating(User user) {
        List<Integer> ratings = new ArrayList<>();
        for (SwapRequest swap : swaps.values()) {
            if (swap.toId.equals(user.id) && swap.fromRating != null) {
                ratings.add(swap.fromRating);
            }
            if (swap.fromId.equals(user.id) && swap.toRating != null) {
                ratings.add(swap.toRating);
            }
        }
        if (ratings.isEmpty()) {
            return 0;
        }
        return ratings.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    public synchronized int ratingCount(User user) {
        int n = 0;
        for (SwapRequest swap : swaps.values()) {
            if (swap.toId.equals(user.id) && swap.fromRating != null) {
                n++;
            }
            if (swap.fromId.equals(user.id) && swap.toRating != null) {
                n++;
            }
        }
        return n;
    }

    public synchronized Map<String, Object> stats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("users", usersById.size());
        map.put("swaps", swaps.size());
        map.put("pending", swaps.values().stream().filter(SwapRequest::isPending).count());
        map.put("accepted", swaps.values().stream().filter(SwapRequest::isAccepted).count());
        map.put("skills", usersById.values().stream().flatMap(u -> u.skillsOffered.stream()).distinct().count());
        return map;
    }

    public synchronized void seed() {
        if (!usersById.isEmpty()) {
            return;
        }
        User adminUser = register("Platform Admin", "admin@skillswap.local", "admin123",
                "HQ", "", "Always", "Keeps the desk in order.", List.of("Moderation"), List.of(), false);
        adminUser.isAdmin = true;

        User aisha = register("Aisha Rahman", "aisha@skillswap.local", "aisha123",
                "Mumbai", "", "Weekends", "Designs posters for community theatres.",
                List.of("Photoshop", "UI Design", "Figma"), List.of("Java", "Excel"), true);
        User ravi = register("Ravi Mehta", "ravi@skillswap.local", "ravi123",
                "Pune", "", "Weekday evenings", "Writes backend services and terrible dad jokes.",
                List.of("Java", "Spring", "SQL"), List.of("Guitar", "Spanish"), true);
        User meera = register("Meera Iyer", "meera@skillswap.local", "meera123",
                "Ahmedabad", "", "Weekends", "Teaches guitar on a sunlit balcony.",
                List.of("Guitar", "Yoga", "Hindi"), List.of("Photography", "Lightroom"), true);
        User kabir = register("Kabir Singh", "kabir@skillswap.local", "kabir123",
                "Delhi", "", "Evenings", "Turns messy spreadsheets into calm ones.",
                List.of("Excel", "Accounting", "Public Speaking"), List.of("Photoshop", "Yoga"), true);
        User nora = register("Nora D'Souza", "nora@skillswap.local", "nora123",
                "Bengaluru", "", "Flexible", "Shoots portraits of street musicians.",
                List.of("Photography", "Lightroom", "Cooking"), List.of("Yoga", "Java"), true);
        register("Private Patil", "patil@skillswap.local", "patil123",
                "Goa", "", "Mornings", "Keeps a low profile while learning pottery.",
                List.of("Pottery"), List.of("Cooking"), false);

        SwapRequest s1 = sendSwapRequest(aisha, ravi, "Photoshop", "Java",
                "I can walk you through poster layouts if you help me with Java collections.");
        accept(ravi, s1.id);
        finish(ravi, s1.id);
        addFeedback(ravi, s1.id, 5, "Clear, patient, and brought example files.");
        addFeedback(aisha, s1.id, 4, "Great Java crash course. Notes were gold.");

        sendSwapRequest(meera, nora, "Guitar", "Photography",
                "Trade a beginner photography walk for two guitar lessons?");
        sendSwapRequest(kabir, aisha, "Excel", "Photoshop",
                "Need a one-pager designed. I can clean your budget sheet in return.");

        admin.sendPlatformMessage("Welcome to SkillSwap — list a skill you can teach and one you want to learn.");
    }

    private SwapRequest requireSwap(String id) {
        SwapRequest request = swaps.get(id);
        if (request == null) {
            throw new IllegalArgumentException("Swap request not found.");
        }
        return request;
    }

    private User requireUser(String id) {
        User user = usersById.get(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }
        return user;
    }

    private static void requireAdmin(User actor) {
        if (actor == null || !actor.isAdmin) {
            throw new IllegalArgumentException("Admin access required.");
        }
    }

    private static void addSkills(java.util.Set<String> target, List<String> skills) {
        if (skills == null) {
            return;
        }
        for (String skill : skills) {
            if (skill == null) {
                continue;
            }
            String trimmed = skill.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    private String randomSalt() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Hashing unavailable", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value, int max) {
        String s = safe(value);
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String requireLength(String field, String value, int min, int max) {
        String s = safe(value);
        if (s.length() < min || s.length() > max) {
            throw new IllegalArgumentException(field + " must be " + min + "-" + max + " chars");
        }
        return s;
    }

    private static void requirePassword(String password) {
        String s = safe(password);
        if (s.length() < 6) {
            throw new IllegalArgumentException("Password too short");
        }
    }

    private static String normalizeEmail(String email) {
        return safe(email).toLowerCase(Locale.ROOT).trim();
    }

    private static String sanitizeUrl(String url) {
        String s = safe(url);
        if (s.isEmpty()) return s;
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://" + s;
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}





