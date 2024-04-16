package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.ConditionDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;

public class ConditionDefinitionDeserializer extends DefinitionDeserializer<ConditionDefinition> {
  public ConditionDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ConditionDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    Definition base = getDefinition(jObj);

    String name = getString(jObj, "name");
    String parent = nullableString(jObj, "parent");
    List<String> dataNames = getList(context, jObj, "data_names", String.class);

    return new ConditionDefinition(
        base.getLocation(),
        base.getModuleName(),
        base.getDoc(),
        base.getNode(),
        name,
        parent,
        dataNames);
  }
}
