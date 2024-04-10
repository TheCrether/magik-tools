package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import nl.ramsolutions.sw.definitions.ProductDefinition;
import nl.ramsolutions.sw.magik.Location;

public class ProductDefinitionDeserializer extends BaseDeserializer<ProductDefinition> {
  @Override
  public ProductDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    String name = getString(jObj, "name");
    String version = nullableString(jObj, "version");
    String versionComment = nullableString(jObj, "version_comment");

    Location location = getLocation(jObj);

    ProductDefinition def = new ProductDefinition(location, name, version, versionComment);

    getList(context, jObj, "children", String.class).forEach(def::addChild);
    getList(context, jObj, "modules", String.class).forEach(def::addModule);
    getList(context, jObj, "requireds", String.class).forEach(def::addRequired);

    return def;
  }
}
