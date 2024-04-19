package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.ConditionDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;

public class ConditionDefinitionDeserializer extends DefinitionDeserializer<ConditionDefinition> {
  public ConditionDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ConditionDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);

    Definition base = getDefinition(node);

    String name = getString(node, "name");
    String parent = nullableString(node, "par");
    List<String> dataNames = getList(context, node, "d_names", String.class);

    return new ConditionDefinition(
        base.getLocation(),
        base.getModuleName(),
        base.getDoc(),
        base.getNode(),
        name,
        parent,
        dataNames);
  }
}
