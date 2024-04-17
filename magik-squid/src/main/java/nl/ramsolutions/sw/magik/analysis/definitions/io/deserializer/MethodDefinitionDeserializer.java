package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.*;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class MethodDefinitionDeserializer extends DefinitionDeserializer<MethodDefinition> {
  public MethodDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public MethodDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    Definition base = getDefinition(jObj);

    TypeString typeName = getTypeString(context, jObj, "type_n");
    String methodName = jObj.get("m_name").getAsString();

    Location loc = base.getLocation();
    if (loc != null && type instanceof Class) {
      Definition parsed = getParsedDefinition(loc, methodName, (Class<?>) type);
      if (parsed != null) {
        loc = parsed.getLocation();
      }
    }

    Set<MethodDefinition.Modifier> modifiers =
        getSet(context, jObj, "mods", MethodDefinition.Modifier.class);
    List<ParameterDefinition> parameters =
        getList(context, jObj, "params", ParameterDefinition.class);

    ParameterDefinition assignmentParameter =
        get(context, jObj, "a_params", ParameterDefinition.class);

    Set<String> topics = getSet(context, jObj, "top", String.class);

    ExpressionResultString returnTypes =
        get(context, jObj, "ret", ExpressionResultString.class);
    ExpressionResultString loopTypes =
        get(context, jObj, "loop", ExpressionResultString.class);

    Set<GlobalUsage> usedGlobals = getSet(context, jObj, "u_globals", GlobalUsage.class);
    Set<MethodUsage> usedMethods = getSet(context, jObj, "u_methods", MethodUsage.class);
    Set<SlotUsage> usedSlots = getSet(context, jObj, "u_slots", SlotUsage.class);
    Set<ConditionUsage> usedConditions =
        getSet(context, jObj, "u_conds", ConditionUsage.class);

    return new MethodDefinition(
        loc,
        base.getModuleName(),
        base.getDoc(),
        base.getNode(),
        typeName,
        methodName,
        modifiers,
        parameters,
        assignmentParameter,
        topics,
        returnTypes,
        loopTypes,
        usedGlobals,
        usedMethods,
        usedSlots,
        usedConditions);
  }
}
