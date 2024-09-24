package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.ExemplarDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.ExemplarDefinition.Sort;
import nl.ramsolutions.sw.magik.analysis.definitions.SlotDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class ExemplarDefinitionDeserializer extends BaseDeserializer<ExemplarDefinition> {
  public ExemplarDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ExemplarDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.readValueAsTree();
    String moduleName = nullableString(node, "mod_n");
    String doc = nullableString(node, "doc");
    Sort sort = get(context, node, "sort", ExemplarDefinition.Sort.class);
    TypeString typeName = getTypeString(context, node, "type_n");

    List<SlotDefinition> slots = getList(context, node, "slots", SlotDefinition.class);
    List<TypeString> parents = getList(context, node, "par", TypeString.class);
    Set<String> topics = getSet(context, node, "top", String.class);

    Location location = getLocation(node);

    return new ExemplarDefinition(
        location,
        getTimestamp(location),
        moduleName,
        doc,
        null,
        sort,
        typeName,
        slots,
        parents,
        topics);
  }
}
