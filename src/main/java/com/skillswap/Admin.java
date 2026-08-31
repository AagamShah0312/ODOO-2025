package com.skillswap;

import com.skillswap.model.PlatformMessage;
import com.skillswap.model.SwapRequest;
import com.skillswap.model.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Admin {
    public final Set<String> bannedUsers = new HashSet<>();
    public final List<PlatformMessage> platformMessages = new ArrayList<>();

    public void rejectInappropriateSkill(User user, String skill) {
        boolean removed = user.skillsOffered.remove(skill) | user.skillsWanted.remove(skill);
        if (removed) {
            System.out.println("Skill '" + skill + "' removed from user " + user.name);
        }
    }

    public void banUser(User user) {
        user.isBanned = true;
        bannedUsers.add(user.email == null ? user.name : user.email);
        System.out.println("User " + user.name + " has been banned.");
    }

    public void unbanUser(User user) {
        user.isBanned = false;
        bannedUsers.remove(user.email);
        bannedUsers.remove(user.name);
        System.out.println("User " + user.name + " has been unbanned.");
    }

    public void monitorSwaps(List<User> users) {
        Set<String> seen = new HashSet<>();
        for (User user : users) {
            for (SwapRequest sr : user.swapRequests()) {
                if (seen.add(sr.id)) {
                    String from = sr.from != null ? sr.from.name : sr.fromId;
                    String to = sr.to != null ? sr.to.name : sr.toId;
                    System.out.println(from + " -> " + to + ": " + sr.status);
                }
            }
        }
    }

    public PlatformMessage sendPlatformMessage(String message) {
        PlatformMessage msg = new PlatformMessage(message);
        platformMessages.add(0, msg);
        System.out.println("Broadcast message: " + message);
        return msg;
    }

    public void downloadReports(List<User> users) {
        System.out.println("User Activity Report:");
        for (User user : users) {
            System.out.println(user.name + " Feedbacks: " + user.feedbackList);
        }
    }
}
