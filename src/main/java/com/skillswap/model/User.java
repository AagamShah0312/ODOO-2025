package com.skillswap.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class User {
    public final String id;
    public String name;
    public String email;
    public String passwordHash;
    public String salt;
    public String location = "";
    public String profilePhoto = "";
    public String availability = "";
    public String bio = "";
    public boolean isPublic = true;
    public boolean isAdmin = false;
    public boolean isBanned = false;
    public final Set<String> skillsOffered = new LinkedHashSet<>();
    public final Set<String> skillsWanted = new LinkedHashSet<>();
    public final List<SwapRequest> swapRequests = new ArrayList<>();
    public final List<String> feedbackList = new ArrayList<>();
    public final Instant createdAt = Instant.now();

    public User(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    public User(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void addFeedback(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return;
        }
        feedbackList.add(feedback);
        if (feedbackList.size() > 50) {
            feedbackList.remove(0);
        }
    }

    public List<SwapRequest> swapRequests() {
        return swapRequests;
    }

    public String initials() {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    @Override
    public String toString() {
        return "Name: " + name + ", Skills Offered: " + skillsOffered
                + ", Skills Wanted: " + skillsWanted + ", Public: " + isPublic;
    }
}
