package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import nl.ramsolutions.sw.definitions.ProductDefinition;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;

public class ProductDefinitionDeserializer extends BaseDeserializer<ProductDefinition> {
  public ProductDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ProductDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    String name = getString(jObj, "name");
    String version = nullableString(jObj, "ver");
    String versionComment = nullableString(jObj, "ver_com");

    Location location = getLocation(jObj);

    ProductDefinition def = new ProductDefinition(location, name, version, versionComment);

    getList(context, jObj, "children", String.class).forEach(def::addChild);
    getList(context, jObj, "mods", String.class).forEach(def::addModule);
    getList(context, jObj, "req", String.class).forEach(def::addRequired);

    return def;
  }
}
