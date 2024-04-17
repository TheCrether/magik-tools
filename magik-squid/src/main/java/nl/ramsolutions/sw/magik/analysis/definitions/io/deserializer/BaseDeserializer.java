package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.MagikFile;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public abstract class BaseDeserializer<T> implements JsonDeserializer<T> {
  private static HashMap<Path, List<Definition>> parsedFiles = new HashMap<>();
  private static Set<Path> erroredFiles = new HashSet<>();

  private final List<PathMapping> mappings;

  public BaseDeserializer(List<PathMapping> mappings) {
    this.mappings = mappings;
  }

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
    return getStream(obj, field).map(e -> context.<X>deserialize(e, clazz)).toList();
  }

  public static <X> Set<X> getSet(
      JsonDeserializationContext context, JsonObject obj, String field, Class<X> clazz) {
    return getStream(obj, field)
        .map(e -> context.<X>deserialize(e, clazz))
        .collect(Collectors.toSet());
  }

  @Nullable
  public Location getLocation(JsonObject obj) {
    Location location = null;
    String source = nullableString(obj, "src");
    if (source != null) {
      Path path = Path.of(source);
      location = Location.validLocation(new Location(path.toUri()), this.mappings);
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

  public static List<Definition> getDefinitions(Location location) {
    if (location == null) {
      return new ArrayList<>();
    }

    Path path = location.getPath();
    if (erroredFiles.contains(path)) {
      return new ArrayList<>();
    }

    if (Files.notExists(path)) {
      erroredFiles.add(path);
      return new ArrayList<>();
    }

    if (!parsedFiles.containsKey(path)) {
      try {
        MagikFile file = new MagikFile(path);
        parsedFiles.put(path, file.getDefinitions());
      } catch (Exception e) {
        erroredFiles.add(path);
        return new ArrayList<>();
      }
    }

    return Collections.unmodifiableList(parsedFiles.getOrDefault(path, new ArrayList<>()));
  }

  public static <X> Definition getParsedDefinition(Location location, String name, Class<X> clazz) {
    return getDefinitions(location).stream()
        .filter(def -> def.getName().endsWith(name) && clazz.isInstance(def))
        .findFirst()
        .orElse(null);
  }

  public static void clearParsedFiles() {
    erroredFiles = new HashSet<>();
    parsedFiles = new HashMap<>();
  }
}
