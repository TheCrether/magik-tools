package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.definitions.ProductDefinition;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;

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
    List<String> requireds = getList(context, node, "req", String.class);

    Location location = getLocation(node);

    ProductDefinition def = new ProductDefinition(location, name, version, versionComment, null, null, requireds);

    getList(context, node, "children", String.class).forEach(def::addChild);
    getList(context, node, "mods", String.class).forEach(def::addModule);

    return def;
  }
}
