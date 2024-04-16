package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
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
  public ParameterDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    Definition base = getDefinition(jObj);

    String name = getString(jObj, "name");

    ParameterDefinition.Modifier modifier =
        get(context, jObj, "modifier", ParameterDefinition.Modifier.class);
    TypeString typeName = getTypeString(context, jObj, "type_name");

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
