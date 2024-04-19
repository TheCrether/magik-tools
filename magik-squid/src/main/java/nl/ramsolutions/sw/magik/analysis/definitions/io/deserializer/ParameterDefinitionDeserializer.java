package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;
import nl.ramsolutions.sw.magik.analysis.definitions.ParameterDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class ParameterDefinitionDeserializer extends DefinitionDeserializer<ParameterDefinition> {
  public ParameterDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ParameterDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);

    Definition base = getDefinition(node);

    String name = getString(node, "name");

    ParameterDefinition.Modifier modifier =
        get(context, node, "modifier", ParameterDefinition.Modifier.class);
    TypeString typeName = getTypeString(context, node, "type_name");

    return new ParameterDefinition(
        base.getLocation(),
        base.getModuleName(),
        base.getDoc(),
        base.getNode(),
        name,
        modifier,
        typeName);
  }
}
