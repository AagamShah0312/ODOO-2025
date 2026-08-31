package com.skillswap.model;

import java.time.Instant;
import java.util.UUID;

public class SwapRequest {
    public final String id;
    public final String fromId;
    public final String toId;
    public User from;
    public User to;
    public String skillOffered = "";
    public String skillWanted = "";
    public String message = "";
    public String status = "pending";
    public final Instant createdAt = Instant.now();
    public Instant resolvedAt;

    public Integer fromRating;
    public String fromComment = "";
    public Integer toRating;
    public String toComment = "";

    public SwapRequest(User from, User to) {
        this.id = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
        this.fromId = from.id;
        this.toId = to.id;
    }

    public SwapRequest(String id, String fromId, String toId) {
        this.id = id;
        this.fromId = fromId;
        this.toId = toId;
    }

    public boolean isPending() {
        return "pending".equals(status);
    }

    public boolean isAccepted() {
        return "accepted".equals(status) || "completed".equals(status);
    }

    public String fromNameSafe() {
        return from != null ? from.name : fromId;
    }

    public String toNameSafe() {
        return to != null ? to.name : toId;
    }
}
