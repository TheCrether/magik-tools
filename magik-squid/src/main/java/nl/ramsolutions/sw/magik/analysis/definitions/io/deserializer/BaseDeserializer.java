package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.MagikFile;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.MagikDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public abstract class BaseDeserializer<T> extends StdDeserializer<T> {
  private static final HashMap<Path, IndexedFile> parsedFiles = new HashMap<>();
  private static final Set<Path> erroredFiles = new HashSet<>();

  private final List<PathMapping> mappings;

  private static class IndexedFile {
    private final List<MagikDefinition> definitions;
    private final long indexedAt;

    private IndexedFile(List<MagikDefinition> definitions, long indexedAt) {
      this.definitions = definitions;
      this.indexedAt = indexedAt;
    }

    public List<MagikDefinition> getDefinitions() {
      return definitions;
    }

    public long getIndexedAt() {
      return indexedAt;
    }
  }

  public BaseDeserializer(List<PathMapping> mappings) {
    super((Class<?>) null);
    this.mappings = mappings;
  }

  @Nullable
  public static String nullableString(JsonNode node, String field) {
    JsonNode strNode = node.get(field);
    return asString(strNode);
  }

  @Nullable
  public static String asString(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    return node.asText();
  }

  public static Stream<JsonNode> getStream(JsonNode node, String field) {
    JsonNode arrNode = node.get(field);
    if (arrNode == null || !arrNode.isArray()) {
      return Stream.empty();
    }

    return StreamSupport.stream(arrNode.spliterator(), false);
  }

  public static <X> List<X> getList(
      DeserializationContext context, JsonNode node, String field, Class<X> clazz) {
    return getStream(node, field)
        .map(
            e -> {
              try {
                return context.readTreeAsValue(e, clazz);
              } catch (IOException ex) {
                throw new RuntimeException(ex);
              }
            })
        .toList();
  }

  public static <X> Set<X> getSet(
      DeserializationContext context, JsonNode node, String field, Class<X> clazz) {
    return getStream(node, field)
        .map(
            e -> {
              try {
                return context.readTreeAsValue(e, clazz);
              } catch (IOException ex) {
                throw new RuntimeException(ex);
              }
            })
        .collect(Collectors.toSet());
  }

  @Nullable
  public Location getLocation(JsonNode node) {
    Location location = null;
    String source = nullableString(node, "src");
    if (source != null) {
      Path path = Path.of(source);
      location = Location.validLocation(new Location(path.toUri()), this.mappings);
    }
    return location;
  }

  public static String getStringField(JsonNode node, String field) {
    JsonNode strNode = node.get(field);
    try {
      return getString(strNode);
    } catch (IllegalStateException ex) {
      throw new RuntimeException("Missing required field " + field);
    }
  }

  public static String getString(JsonNode node) {
    String str = asString(node);
    if (str == null) {
      throw new IllegalStateException("Missing required string node " + node);
    }
    return str;
  }

  public static TypeString getTypeString(
      DeserializationContext context, JsonNode node, String field) {
    return get(context, node, field, TypeString.class);
  }

  public static <X> X get(
      DeserializationContext context, JsonNode node, String field, Class<X> clazz) {
    try {
      return context.readTreeAsValue(node.get(field), clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<MagikDefinition> getDefinitions(Location location) {
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

    long now = System.currentTimeMillis();

    if (parsedFiles.containsKey(path)) {
      IndexedFile file = parsedFiles.get(path);
      if (file.getIndexedAt() < path.toFile().lastModified()) {
        parsedFiles.remove(path);
      }
    }

    if (!parsedFiles.containsKey(path)) {
      try {
        MagikFile file = new MagikFile(path);
        parsedFiles.put(path, new IndexedFile(file.getDefinitions(), now));
      } catch (Exception e) {
        erroredFiles.add(path);
        return new ArrayList<>();
      }
    }

    IndexedFile file = parsedFiles.get(path);
    if (file == null) {
      return Collections.unmodifiableList(new ArrayList<>());
    }

    return Collections.unmodifiableList(file.getDefinitions());
  }

  public static <X> MagikDefinition getParsedDefinition(
      Location location, String name, Class<X> clazz) {
    return getDefinitions(location).stream()
        .filter(def -> def.getName().endsWith(name) && clazz.isInstance(def))
        .findFirst()
        .orElse(null);
  }

  public static void clearParsedFiles() {
    erroredFiles.clear();
  }
}
