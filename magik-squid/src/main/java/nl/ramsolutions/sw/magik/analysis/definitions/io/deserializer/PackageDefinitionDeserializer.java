package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;
import nl.ramsolutions.sw.magik.analysis.definitions.PackageDefinition;

public class PackageDefinitionDeserializer extends DefinitionDeserializer<PackageDefinition> {
  public PackageDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public PackageDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);

    Definition base = getDefinition(node);

    String name = getString(node, "name");
    List<String> uses = getList(context, node, "uses", String.class);

    return new PackageDefinition(
        base.getLocation(), base.getModuleName(), base.getDoc(), base.getNode(), name, uses);
  }
}
