package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.debug.PocketTrace;
import net.minecraft.server.level.ServerLevel;

import java.util.IdentityHashMap;
import java.util.Map;

public final class RapierSceneLifetime {
    private static final Map<ServerLevel, Token> BY_LEVEL = new IdentityHashMap<>();
    private static final Map<Long, Token> BY_HANDLE = new java.util.HashMap<>();
    private static long nextGeneration;

    public static synchronized void opened(final ServerLevel level, final long nativeHandle) {
        if (level == null || nativeHandle == RapierBridge.NO_SCENE) return;

        final Token previous = BY_LEVEL.remove(level);
        if (previous != null) {
            previous.live = false;
            BY_HANDLE.remove(previous.nativeHandle, previous);
        }

        final Token token = new Token(nativeHandle, ++nextGeneration);
        BY_LEVEL.put(level, token);
        BY_HANDLE.put(nativeHandle, token);
        PocketTrace.scale("Rapier scene opened {} dimension={}", token, level.dimension().location());
    }

    public static synchronized void closing(final ServerLevel level) {
        final Token token = BY_LEVEL.remove(level);
        if (token == null) return;
        token.live = false;
        BY_HANDLE.remove(token.nativeHandle, token);
        PocketTrace.scale("Rapier scene closing {} dimension={}", token, level.dimension().location());
    }

    public static synchronized Token tokenFor(final long nativeHandle) {
        final Token token = BY_HANDLE.get(nativeHandle);
        return token != null && token.live ? token : null;
    }

    public static synchronized boolean isLive(final Token token, final long nativeHandle) {
        return token != null
                && token.live
                && token.nativeHandle == nativeHandle
                && BY_HANDLE.get(nativeHandle) == token;
    }

    public static final class Token {
        private final long nativeHandle;
        private final long generation;
        private boolean live = true;

        private Token(final long nativeHandle, final long generation) {
            this.nativeHandle = nativeHandle;
            this.generation = generation;
        }

        @Override
        public String toString() {
            return "RapierSceneToken[handle=0x" + Long.toHexString(nativeHandle)
                    + ", generation=" + generation + ", live=" + live + "]";
        }
    }

    private RapierSceneLifetime() {}
}
