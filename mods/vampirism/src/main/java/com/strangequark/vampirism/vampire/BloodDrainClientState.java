package com.strangequark.vampirism.vampire;

public final class BloodDrainClientState {
    private static final long SUPPRESS_SWING_WINDOW_MS = 250L;
    private static long suppressSwingUntilMs;

    private BloodDrainClientState() {
    }

    public static void suppressNextSwing() {
        suppressSwingUntilMs = System.currentTimeMillis() + SUPPRESS_SWING_WINDOW_MS;
    }

    public static boolean consumeSwingSuppression() {
        long now = System.currentTimeMillis();
        if (suppressSwingUntilMs == 0L || now > suppressSwingUntilMs) {
            suppressSwingUntilMs = 0L;
            return false;
        }

        suppressSwingUntilMs = 0L;
        return true;
    }
}
