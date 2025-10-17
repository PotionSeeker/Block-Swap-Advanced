package potionseeker.block_swap_advanced.serialization;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonArray;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import java.util.stream.Stream;

public class JanksonJsonOps implements DynamicOps<com.google.gson.JsonElement> {
    public static final JanksonJsonOps INSTANCE = new JanksonJsonOps();
    private final JsonOps jsonOps = JsonOps.INSTANCE;
    private final Gson gson = new Gson();

    private JanksonJsonOps() {}

    private com.google.gson.JsonElement convertToGson(JsonElement janksonElement) {
        String jsonString = janksonElement.toJson(false, false);
        return JsonParser.parseString(jsonString);
    }

    private JsonElement convertFromGson(com.google.gson.JsonElement gsonElement) {
        String jsonString = gson.toJson(gsonElement);
        try {
            return Jankson.builder().build().load(jsonString);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    @Override
    public com.google.gson.JsonElement empty() {
        return jsonOps.empty();
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, com.google.gson.JsonElement input) {
        return jsonOps.convertTo(outOps, input);
    }

    @Override
    public DataResult<Number> getNumberValue(com.google.gson.JsonElement input) {
        return jsonOps.getNumberValue(input);
    }

    @Override
    public com.google.gson.JsonElement createNumeric(Number i) {
        return jsonOps.createNumeric(i);
    }

    @Override
    public DataResult<String> getStringValue(com.google.gson.JsonElement input) {
        return jsonOps.getStringValue(input);
    }

    @Override
    public com.google.gson.JsonElement createString(String value) {
        return jsonOps.createString(value);
    }

    @Override
    public DataResult<Boolean> getBooleanValue(com.google.gson.JsonElement input) {
        return jsonOps.getBooleanValue(input);
    }

    @Override
    public com.google.gson.JsonElement createBoolean(boolean value) {
        return jsonOps.createBoolean(value);
    }

    @Override
    public DataResult<com.google.gson.JsonElement> mergeToList(com.google.gson.JsonElement list, com.google.gson.JsonElement value) {
        if (list instanceof com.google.gson.JsonArray gsonArray) {
            com.google.gson.JsonArray newArray = new com.google.gson.JsonArray();
            gsonArray.forEach(newArray::add);
            newArray.add(value);
            return DataResult.success(newArray);
        }
        return jsonOps.mergeToList(list, value);
    }

    @Override
    public DataResult<com.google.gson.JsonElement> mergeToMap(com.google.gson.JsonElement map, com.google.gson.JsonElement key, com.google.gson.JsonElement value) {
        return jsonOps.mergeToMap(map, key, value);
    }

    @Override
    public DataResult<Stream<com.google.gson.JsonElement>> getStream(com.google.gson.JsonElement input) {
        if (input instanceof com.google.gson.JsonArray array) {
            return DataResult.success(Stream.of(array).flatMap(arr -> arr.asList().stream()));
        }
        return jsonOps.getStream(input);
    }

    @Override
    public DataResult<Stream<com.mojang.datafixers.util.Pair<com.google.gson.JsonElement, com.google.gson.JsonElement>>> getMapValues(com.google.gson.JsonElement input) {
        if (input instanceof com.google.gson.JsonObject jsonObject) {
            Stream<com.mojang.datafixers.util.Pair<com.google.gson.JsonElement, com.google.gson.JsonElement>> stream =
                    jsonObject.entrySet().stream()
                            .map(entry -> com.mojang.datafixers.util.Pair.of(
                                    new com.google.gson.JsonPrimitive(entry.getKey()),
                                    entry.getValue()
                            ));
            return DataResult.success(stream);
        }
        return jsonOps.getMapValues(input);
    }

    @Override
    public com.google.gson.JsonElement createMap(Stream<com.mojang.datafixers.util.Pair<com.google.gson.JsonElement, com.google.gson.JsonElement>> map) {
        return jsonOps.createMap(map);
    }

    @Override
    public com.google.gson.JsonElement createList(Stream<com.google.gson.JsonElement> input) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        input.forEach(array::add);
        return array;
    }

    @Override
    public com.google.gson.JsonElement remove(com.google.gson.JsonElement input, String key) {
        return jsonOps.remove(input, key);
    }
}