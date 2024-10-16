package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.productdef.ProductDefinition;
import nl.ramsolutions.sw.productdef.ProductUsage;

public class ProductDefinitionDeserializer extends BaseDeserializer<ProductDefinition> {
  public ProductDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ProductDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.readValueAsTree();

    String name = getStringField(node, "name");
    String version = nullableString(node, "ver");
    String versionComment = nullableString(node, "ver_com");
    String parent = nullableString(node, "parent");
    List<ProductUsage> usages = getList(context, node, "usage", ProductUsage.class);

    Location location = getLocation(node);

    return new ProductDefinition(
        location,
        getTimestamp(location),
        name,
        parent,
        version,
        versionComment,
        null,
        null,
        usages);
  }
}
