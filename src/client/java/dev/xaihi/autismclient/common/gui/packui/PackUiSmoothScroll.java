package dev.xaihi.autismclient.common.gui.packui;

public final class PackUiSmoothScroll {
    public static final float SMOOTH_SPEED = 24.0f;

    public int targetOffset = 0;
    public float visualOffset = 0.0f;
    public boolean initialized = false;

    public void restore(int offset) {
        targetOffset = Math.max(0, offset);
        visualOffset = targetOffset;
        initialized = true;
    }

    public void setTarget(int offset, int maxScroll) {
        targetOffset = clamp(offset, maxScroll);
        if (!initialized) {
            visualOffset = targetOffset;
            initialized = true;
        }
    }

    public void jumpTo(int offset, int maxScroll) {
        targetOffset = clamp(offset, maxScroll);
        visualOffset = targetOffset;
        initialized = true;
    }

    public void nudge(double amount, float stepPixels, int maxScroll) {
        setTarget(targetOffset - Math.round((float) amount * stepPixels), maxScroll);
    }

    public void setFromThumb(PackUiScrollbar.Metrics metrics, double mouseY, int grabOffset) {
        if (metrics == null) {
            jumpTo(0, 0);
            return;
        }
        setTarget(PackUiScrollbar.scrollFromThumb(metrics, mouseY, grabOffset), metrics.maxScroll());
    }

    public void setFromThumbStepped(PackUiScrollbar.Metrics metrics, double mouseY, int grabOffset, int stepSize) {
        if (metrics == null) {
            jumpTo(0, 0);
            return;
        }
        int rawScroll = PackUiScrollbar.scrollFromThumb(metrics, mouseY, grabOffset);
        int steppedScroll = (rawScroll / stepSize) * stepSize;
        setTarget(steppedScroll, metrics.maxScroll());
    }

    public int tick(float delta, int maxScroll) {
        targetOffset = clamp(targetOffset, maxScroll);
        visualOffset = targetOffset;
        initialized = true;
        return Math.round(visualOffset);
    }

    public int targetOffset() {
        return targetOffset;
    }

    public int visualOffsetInt() {
        return Math.round(visualOffset);
    }

    public static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }
}
