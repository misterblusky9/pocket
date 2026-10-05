package com.misterblusky9.pocket.client;

import net.minecraft.client.gui.screens.Screen;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class HudScaleText {
    private static final double EXACT = 1.0E-4D;

    public static String of(final double shown, final double live) {
        return Screen.hasShiftDown() ? precise(live) : compact(shown);
    }

    public static String compact(final double scale) {
        final String glyph = glyph(scale);
        if (glyph != null) return glyph + "×";
        final BigDecimal rounded = rounded(scale);
        final String approx = Math.abs(rounded.doubleValue() - scale) > EXACT ? "~" : "";
        return approx + rounded.stripTrailingZeros().toPlainString() + "×";
    }

    public static String bare(final double scale) {
        final String glyph = glyph(scale);
        return glyph != null ? glyph : rounded(scale).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal rounded(final double scale) {
        final BigDecimal value = BigDecimal.valueOf(scale);
        return scale < 1.0D
                ? value.round(new MathContext(2, RoundingMode.HALF_UP))
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static String precise(final double scale) {
        return BigDecimal.valueOf(scale)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "×";
    }

    private static String glyph(final double scale) {
        if (same(scale, 1.0D / 32.0D)) return "¹/₃₂";
        if (same(scale, 1.0D / 16.0D)) return "¹/₁₆";
        if (same(scale, 1.0D / 8.0D)) return "¹/₈";

        final long whole = (long) Math.floor(scale + EXACT);
        final String fraction = fractionGlyph(Math.max(0.0D, scale - whole));
        if (fraction == null) return null;
        return whole == 0 ? fraction : whole + fraction;
    }

    private static String fractionGlyph(final double part) {
        if (same(part, 0.1D)) return "¹/₁₀";
        if (same(part, 0.2D)) return "¹/₅";
        if (same(part, 0.25D)) return "¹/₄";
        if (same(part, 1.0D / 3.0D)) return "¹/₃";
        if (same(part, 0.5D)) return "¹/₂";
        if (same(part, 2.0D / 3.0D)) return "²/₃";
        if (same(part, 0.75D)) return "³/₄";
        return null;
    }

    private static boolean same(final double a, final double b) {
        return Math.abs(a - b) <= EXACT;
    }

    private HudScaleText() {}
}
