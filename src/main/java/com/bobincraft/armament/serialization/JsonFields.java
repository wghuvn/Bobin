package com.bobincraft.armament.serialization;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.validation.Checks;
import com.bobincraft.armament.validation.DefinitionValidationException;
import com.google.gson.*;
import java.util.*;

/** Exact field access: no Gson coercion from strings to numbers or booleans. */
public final class JsonFields {
    private final JsonObject object;
    private final String path;

    public JsonFields(JsonElement value, String path, String... allowed) {
        Checks.require(value != null && value.isJsonObject(), path, "expected object");
        this.object = value.getAsJsonObject();
        this.path = path;
        Set<String> names = Set.of(allowed);
        object.keySet().forEach(name -> Checks.require(names.contains(name), at(name), "unknown field"));
    }

    public String at(String name) { return path + "." + name; }
    public boolean has(String name) { return object.has(name); }
    public JsonElement get(String name) {
        Checks.require(object.has(name), at(name), "required field is missing");
        JsonElement value = object.get(name);
        Checks.require(!value.isJsonNull(), at(name), "must not be null");
        return value;
    }

    public String string(String name) { return stringValue(get(name), at(name)); }
    public static String stringValue(JsonElement value, String path) {
        Checks.require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), path, "expected string");
        return value.getAsString();
    }

    public WeaponId id(String name) { return idValue(get(name), at(name)); }
    public static WeaponId idValue(JsonElement value, String path) {
        String text = stringValue(value, path);
        try { return WeaponId.parse(text); }
        catch (DefinitionValidationException e) { throw new DefinitionValidationException(path, "invalid identifier '" + text + "': " + e.reason()); }
    }

    public long integer(String name) {
        JsonElement value = get(name);
        Checks.require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), at(name), "expected integer");
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException e) { throw new DefinitionValidationException(at(name), "expected an exact 64-bit integer"); }
    }

    public int intValue(String name) {
        long value = integer(name);
        Checks.require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE, at(name), "integer outside 32-bit range");
        return (int) value;
    }

    public double number(String name) { return numberValue(get(name), at(name)); }
    public static double numberValue(JsonElement value, String path) {
        Checks.require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), path, "expected number");
        double number = value.getAsDouble();
        Checks.require(Double.isFinite(number), path, "expected finite number");
        return number;
    }

    public JsonArray array(String name, int min, int max) {
        JsonElement value = get(name);
        Checks.require(value.isJsonArray(), at(name), "expected array");
        JsonArray array = value.getAsJsonArray();
        Checks.require(array.size() >= min && array.size() <= max, at(name), "expected " + min + ".." + max + " entries");
        return array;
    }

    public UUID uuid(String name) {
        String value = string(name);
        Checks.require(value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), at(name), "expected canonical lowercase UUID");
        return UUID.fromString(value);
    }

    public <E extends Enum<E>> E enumeration(String name, Class<E> type) {
        String value = string(name);
        for (E candidate : type.getEnumConstants())
            if (candidate.name().toLowerCase(Locale.ROOT).equals(value)) return candidate;
        throw new DefinitionValidationException(at(name), "unknown value '" + value + "'");
    }
}
