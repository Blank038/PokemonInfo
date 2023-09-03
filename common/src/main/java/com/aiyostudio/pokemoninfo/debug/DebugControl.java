package com.aiyostudio.pokemoninfo.debug;

import com.aiyostudio.pokemoninfo.PokemonInfo;

import java.util.logging.Level;

/**
 * @author Blank038
 */
public class DebugControl {
    private static boolean debug;

    public static void setDebug(boolean debug) {
        DebugControl.debug = debug;
    }

    public static void log(Level level, String message) {
        PokemonInfo.getInstance().getLogger().log(level, message);
    }

    public static void debug(Level level, String message) {
        if (debug) {
            PokemonInfo.getInstance().getLogger().log(level, message);
        }
    }
}
