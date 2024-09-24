package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.moduledef.ModuleDefinition;
import nl.ramsolutions.sw.moduledef.ModuleUsage;

public class ModuleDefinitionDeserializer extends BaseDeserializer<ModuleDefinition> {
  public ModuleDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ModuleDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.readValueAsTree();

    String name = getStringField(node, "name");
    String baseVersion = getStringField(node, "base_ver");
    String currentVersion = nullableString(node, "cur_ver");
    String product = nullableString(node, "prod");
    List<ModuleUsage> usages = getList(context, node, "usgs", ModuleUsage.class);

    Location location = getLocation(node);

    return new ModuleDefinition(
        location, getTimestamp(location), name, product, baseVersion, currentVersion, null, usages);
  }
}
