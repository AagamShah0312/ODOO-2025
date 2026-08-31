package com.skillswap.model;

import java.time.Instant;
import java.util.UUID;

public class PlatformMessage {
    public final String id = UUID.randomUUID().toString();
    public final String text;
    public final Instant createdAt = Instant.now();

    public PlatformMessage(String text) {
        this.text = text;
    }
}
