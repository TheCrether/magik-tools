package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.SlotDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class SlotDefinitionDeserializer extends BaseDeserializer<SlotDefinition> {
  public SlotDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public SlotDefinition deserialize(JsonParser jsonParser, DeserializationContext context)
      throws IOException {
    JsonNode node = jsonParser.getCodec().readTree(jsonParser);
    String moduleName = nullableString(node, "mod_n");
    String doc = nullableString(node, "doc");
    TypeString typeName = getTypeString(context, node, "type_n");
    String name = getStringField(node, "name");

    Location location = getLocation(node);

    return new SlotDefinition(location, moduleName, doc, null, name, typeName);
  }
}
