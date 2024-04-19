package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.definitions.ModuleDefinition;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;

public class ModuleDefinitionDeserializer extends BaseDeserializer<ModuleDefinition> {
  public ModuleDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ModuleDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);

    String name = getString(node, "name");
    String baseVersion = getString(node, "base_ver");
    String currentVersion = nullableString(node, "cur_ver");
    List<String> requireds = getList(context, node, "req", String.class);

    Location location = getLocation(node);

    return new ModuleDefinition(location, name, baseVersion, currentVersion, requireds);
  }
}
