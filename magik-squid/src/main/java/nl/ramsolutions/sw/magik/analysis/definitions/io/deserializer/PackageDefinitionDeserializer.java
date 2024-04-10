package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;
import nl.ramsolutions.sw.magik.analysis.definitions.PackageDefinition;

public class PackageDefinitionDeserializer extends DefinitionDeserializer<PackageDefinition> {
  @Override
  public PackageDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    Definition base = getDefinition(jObj);

    String name = getString(jObj, "name");
    List<String> uses = getList(context, jObj, "uses", String.class);

    PackageDefinition def =
        new PackageDefinition(
            base.getLocation(), base.getModuleName(), base.getDoc(), base.getNode(), name, uses);

    return def;
  }
}
