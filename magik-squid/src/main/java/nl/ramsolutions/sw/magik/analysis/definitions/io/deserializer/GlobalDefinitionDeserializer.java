package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.GlobalDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.MagikDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class GlobalDefinitionDeserializer extends DefinitionDeserializer<GlobalDefinition> {
  public GlobalDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public GlobalDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.readValueAsTree();

    MagikDefinition base = getDefinition(node);

    TypeString typeName = getTypeString(context, node, "type_n");
    TypeString aliasedTypeName = getTypeString(context, node, "alias_type_n");

    return new GlobalDefinition(
        base.getLocation(),
        base.getModuleName(),
        base.getDoc(),
        base.getNode(),
        typeName,
        aliasedTypeName);
  }
}
