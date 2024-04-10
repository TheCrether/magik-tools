package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;
import nl.ramsolutions.sw.magik.analysis.definitions.GlobalDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class GlobalDefinitionDeserializer extends DefinitionDeserializer<GlobalDefinition> {
  @Override
  public GlobalDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    Definition base = getDefinition(jObj);

    TypeString typeName = getTypeString(context, jObj, "type_name");
    TypeString aliasedTypeName = getTypeString(context, jObj, "aliased_type_name");

    return new GlobalDefinition(
        base.getLocation(),
        base.getModuleName(),
        base.getDoc(),
        base.getNode(),
        typeName,
        aliasedTypeName);
  }
}
