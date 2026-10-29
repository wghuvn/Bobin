package com.bobincraft.armament.serialization;

import com.bobincraft.armament.validation.Checks;
import com.bobincraft.armament.validation.DefinitionValidationException;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

/** Strict JSON parsing on existing Gson; rejects duplicates before they can be overwritten. */
public final class StrictJson {
    private static final Gson WRITER = new GsonBuilder().disableHtmlEscaping().create();
    private StrictJson() {}

    public static JsonElement parse(String text) {
        Checks.present(text, "$");
        Checks.require(text.length() <= 1_048_576, "$", "document exceeds 1 MiB character limit");
        checkLexicalRules(text);
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement result = read(reader, 0);
            Checks.require(reader.peek() == JsonToken.END_DOCUMENT, "$", "trailing content");
            return result;
        } catch (IOException | NumberFormatException e) {
            throw new DefinitionValidationException("$", "invalid JSON: " + e.getMessage());
        }
    }

    private static void checkLexicalRules(String text) {
        // Gson 2.10's strict mode still accepts raw controls, \' and mixed-case
        // literals. Enforce these JSON lexical rules without changing game Gson.
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean whitespace = c == '\n' || c == '\r' || c == '\t';
            Checks.require(c >= 0x20 || (!quoted && whitespace), "$",
                    "unescaped control character at character " + i);
            if (quoted) {
                if (escaped) {
                    Checks.require("\"\\/bfnrtu".indexOf(c) >= 0, "$", "invalid JSON escape at character " + i);
                    escaped = false;
                }
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (Character.isLetter(c) && c != 'e' && c != 'E') {
                String literal = switch (c) {
                    case 't', 'T' -> "true";
                    case 'f', 'F' -> "false";
                    case 'n', 'N' -> "null";
                    default -> "";
                };
                Checks.require(!literal.isEmpty() && text.startsWith(literal, i), "$", "invalid JSON literal at character " + i);
                i += literal.length() - 1;
            }
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        Checks.require(depth <= 64, reader.getPath(), "nesting exceeds 64 levels");
        String path = reader.getPath();
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    Checks.require(!object.has(name), reader.getPath(), "duplicate field '" + name + "'");
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new DefinitionValidationException(path, "expected a JSON value");
        };
    }

    public static String write(JsonElement value) { return WRITER.toJson(value); }
}
