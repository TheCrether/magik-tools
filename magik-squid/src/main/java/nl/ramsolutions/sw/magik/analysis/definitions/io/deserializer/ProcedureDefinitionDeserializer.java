package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;
import nl.ramsolutions.sw.magik.analysis.definitions.ParameterDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.ProcedureDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class ProcedureDefinitionDeserializer extends DefinitionDeserializer<ProcedureDefinition> {
  @Override
  public ProcedureDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    Definition base = getDefinition(jObj);

    Set<ProcedureDefinition.Modifier> modifiers =
        getSet(context, jObj, "modifiers", ProcedureDefinition.Modifier.class);
    TypeString typeName = getTypeString(context, jObj, "type_name");

    String procedureName = nullableString(jObj, "procedure_name");

    List<ParameterDefinition> parameters =
        getList(context, jObj, "parameters", ParameterDefinition.class);
    ExpressionResultString returnTypes =
        get(context, jObj, "return_types", ExpressionResultString.class);
    ExpressionResultString loopTypes =
        get(context, jObj, "loop_types", ExpressionResultString.class);

    ProcedureDefinition def =
        new ProcedureDefinition(
            base.getLocation(),
            base.getModuleName(),
            base.getDoc(),
            base.getNode(),
            modifiers,
            typeName,
            procedureName,
            parameters,
            returnTypes,
            loopTypes);

    return def;
  }
}
