package dev.laakirun.veyloria.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonUtils {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonUtils() {
    }
}
