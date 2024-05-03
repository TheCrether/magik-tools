package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonParseException;
import java.io.IOException;
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
  public MethodDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws JsonParseException, IOException {
    JsonNode node = jp.readValueAsTree();

    MagikDefinition base = getDefinition(node);

    TypeString typeName = getTypeString(context, node, "type_n");
    String methodName = getStringField(node, "m_name");

    Location loc = base.getLocation();
    if (loc != null) {
      MagikDefinition parsed = getParsedDefinition(loc, methodName, MethodDefinition.class);
      if (parsed != null) {
        loc = parsed.getLocation();
      }
    }

    Set<MethodDefinition.Modifier> modifiers =
        getSet(context, node, "mods", MethodDefinition.Modifier.class);
    List<ParameterDefinition> parameters =
        getList(context, node, "params", ParameterDefinition.class);

    ParameterDefinition assignmentParameter =
        get(context, node, "a_params", ParameterDefinition.class);

    Set<String> topics = getSet(context, node, "top", String.class);

    ExpressionResultString returnTypes = get(context, node, "ret", ExpressionResultString.class);
    ExpressionResultString loopTypes = get(context, node, "loop", ExpressionResultString.class);

    Set<GlobalUsage> usedGlobals = getSet(context, node, "u_globals", GlobalUsage.class);
    Set<MethodUsage> usedMethods = getSet(context, node, "u_methods", MethodUsage.class);
    Set<SlotUsage> usedSlots = getSet(context, node, "u_slots", SlotUsage.class);
    Set<ConditionUsage> usedConditions = getSet(context, node, "u_conds", ConditionUsage.class);

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
