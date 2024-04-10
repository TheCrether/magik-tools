package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public abstract class BaseDeserializer<T> implements JsonDeserializer<T> {
  @Nullable
  public static String nullableString(JsonObject obj, String field) {
    JsonElement strObj = obj.get(field);
    if (strObj == null || strObj.isJsonNull()) {
      return null;
    }
    return strObj.getAsString();
  }

  public static Stream<JsonElement> getStream(JsonObject obj, String field) {
    JsonElement listObj = obj.get(field);
    if (listObj == null || listObj.isJsonNull()) {
      return Stream.empty();
    }
    return listObj.getAsJsonArray().asList().stream();
  }

  public static <X> List<X> getList(
      JsonDeserializationContext context, JsonObject obj, String field, Class<X> clazz) {
    return getStream(obj, field).map(e -> (X) context.<X>deserialize(e, clazz)).toList();
  }

  public static <X> Set<X> getSet(
      JsonDeserializationContext context, JsonObject obj, String field, Class<X> clazz) {
    return getStream(obj, field)
        .map(e -> (X) context.<X>deserialize(e, clazz))
        .collect(Collectors.toSet());
  }

  @Nullable
  public static Location getLocation(JsonObject obj) {
    Location location = null;
    String source = nullableString(obj, "source_file");
    if (source != null) {
      Path path = Path.of(source);
      location = new Location(path.toUri());
    }
    return location;
  }

  public static String getString(JsonObject obj, String field) {
    return obj.get(field).getAsString();
  }

  public static TypeString getTypeString(
      JsonDeserializationContext context, JsonObject obj, String field) {
    return get(context, obj, field, TypeString.class);
  }

  public static <X> X get(
      JsonDeserializationContext context, JsonObject obj, String field, Class<X> clazz) {
    return context.deserialize(obj.get(field), clazz);
  }
}
