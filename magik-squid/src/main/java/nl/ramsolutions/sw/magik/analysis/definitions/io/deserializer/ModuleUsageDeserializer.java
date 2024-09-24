package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.moduledef.ModuleUsage;

public class ModuleUsageDeserializer extends BaseDeserializer<ModuleUsage> {
  public ModuleUsageDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ModuleUsage deserialize(JsonParser jp, DeserializationContext context) throws IOException {
    JsonNode node = jp.readValueAsTree();

    String name = getStringField(node, "name");

    return new ModuleUsage(name, null);
  }
}
