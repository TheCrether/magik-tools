package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import nl.ramsolutions.sw.definitions.ModuleDefinition;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;

public class ModuleDefinitionDeserializer extends BaseDeserializer<ModuleDefinition> {
  public ModuleDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ModuleDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();
    String name = jObj.get("name").getAsString();
    String baseVersion = jObj.get("base_version").getAsString();
    String currentVersion = nullableString(jObj, "current_version");
    List<String> requireds = getList(context, jObj, "requireds", String.class);

    Location location = getLocation(jObj);

    return new ModuleDefinition(location, name, baseVersion, currentVersion, requireds);
  }
}
