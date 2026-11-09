package com.misterblusky9.pym.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public final class RefusalText {
    private static final char SEPARATOR = '\u001f';
    private static final char ESCAPE = '\u001e';

    public static String of(String key, Object... args) {
        StringBuilder encoded = new StringBuilder(key);
        for (Object arg : args) {
            encoded.append(SEPARATOR);
            String text = String.valueOf(arg);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == SEPARATOR || c == ESCAPE) encoded.append(ESCAPE);
                encoded.append(c);
            }
        }
        return encoded.toString();
    }

    public static MutableComponent component(String message) {
        List<String> parts = split(message);
        String key = parts.get(0);
        Object[] args = new Object[parts.size() - 1];
        for (int i = 0; i < args.length; i++) args[i] = component(parts.get(i + 1));
        return Component.translatableWithFallback(key, key, args);
    }

    private static List<String> split(String message) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == ESCAPE && i + 1 < message.length()) {
                part.append(message.charAt(++i));
            } else if (c == SEPARATOR) {
                parts.add(part.toString());
                part.setLength(0);
            } else {
                part.append(c);
            }
        }
        parts.add(part.toString());
        return parts;
    }

    private RefusalText() {}
}
