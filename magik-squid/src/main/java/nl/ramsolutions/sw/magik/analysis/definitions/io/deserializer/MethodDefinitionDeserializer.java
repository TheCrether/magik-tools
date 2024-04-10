package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import nl.ramsolutions.sw.magik.analysis.definitions.*;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class MethodDefinitionDeserializer extends DefinitionDeserializer<MethodDefinition> {
  @Override
  public MethodDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    Definition base = getDefinition(jObj);

    TypeString typeName = getTypeString(context, jObj, "type_name");
    String methodName = jObj.get("method_name").getAsString();

    Set<MethodDefinition.Modifier> modifiers =
        getSet(context, jObj, "modifiers", MethodDefinition.Modifier.class);
    List<ParameterDefinition> parameters =
        getList(context, jObj, "parameters", ParameterDefinition.class);

    ParameterDefinition assignmentParameter =
        get(context, jObj, "assignment_parameter", ParameterDefinition.class);

    Set<String> topics = getSet(context, jObj, "topics", String.class);

    ExpressionResultString returnTypes =
        get(context, jObj, "return_types", ExpressionResultString.class);
    ExpressionResultString loopTypes =
        get(context, jObj, "loop_types", ExpressionResultString.class);

    Set<GlobalUsage> usedGlobals = getSet(context, jObj, "used_globals", GlobalUsage.class);
    Set<MethodUsage> usedMethods = getSet(context, jObj, "used_methods", MethodUsage.class);
    Set<SlotUsage> usedSlots = getSet(context, jObj, "used_slots", SlotUsage.class);
    Set<ConditionUsage> usedConditions =
        getSet(context, jObj, "used_conditions", ConditionUsage.class);

    return new MethodDefinition(
        base.getLocation(),
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
