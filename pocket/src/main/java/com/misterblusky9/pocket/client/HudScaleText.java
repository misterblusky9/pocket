package com.misterblusky9.pocket.client;

import com.misterblusky9.pym.api.ScaleFormat;
import net.minecraft.client.gui.screens.Screen;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class HudScaleText {
    private static final double EXACT = 1.0E-4D;

    public static String of(double shown, double live) {
        return Screen.hasShiftDown() ? precise(live) : compact(shown);
    }

    public static String compact(double scale) {
        String glyph = glyph(scale);
        if (glyph != null) return glyph + "×";
        BigDecimal rounded = rounded(scale);
        String approx = Math.abs(rounded.doubleValue() - scale) > EXACT ? "~" : "";
        return approx + rounded.stripTrailingZeros().toPlainString() + "×";
    }

    public static String ratio(double magnification) {
        long whole = Math.round(magnification);
        if (Math.abs(magnification - whole) <= EXACT) return whole + ":1";
        BigDecimal rounded = BigDecimal.valueOf(magnification).setScale(1, RoundingMode.HALF_UP);
        return "~" + rounded.stripTrailingZeros().toPlainString() + ":1";
    }

    public static String bare(double scale) {
        String glyph = glyph(scale);
        return glyph != null ? glyph : rounded(scale).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal rounded(double scale) {
        BigDecimal value = BigDecimal.valueOf(scale);
        return scale < 1.0D
                ? value.round(new MathContext(2, RoundingMode.HALF_UP))
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static String precise(double scale) {
        return BigDecimal.valueOf(scale)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "×";
    }

    private static String glyph(double scale) {
        long unit = ScaleFormat.unitDenominator(scale, EXACT);
        if (unit > 0L) return "¹/" + subscript(unit);

        long whole = (long) Math.floor(scale + EXACT);
        String fraction = fractionGlyph(Math.max(0.0D, scale - whole));
        if (fraction == null) return null;
        return whole == 0 ? fraction : whole + fraction;
    }

    private static String fractionGlyph(double part) {
        if (same(part, 0.1D)) return "¹/₁₀";
        if (same(part, 0.2D)) return "¹/₅";
        if (same(part, 0.25D)) return "¹/₄";
        if (same(part, 1.0D / 3.0D)) return "¹/₃";
        if (same(part, 0.5D)) return "¹/₂";
        if (same(part, 2.0D / 3.0D)) return "²/₃";
        if (same(part, 0.75D)) return "³/₄";
        return null;
    }

    private static String subscript(long value) {
        StringBuilder out = new StringBuilder();
        for (char digit : Long.toString(value).toCharArray()) out.append((char) ('₀' + (digit - '0')));
        return out.toString();
    }

    private static boolean same(double a, double b) {
        return Math.abs(a - b) <= EXACT;
    }

    private HudScaleText() {}
}
