package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;
import nl.ramsolutions.sw.magik.analysis.definitions.ParameterDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.ProcedureDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class ProcedureDefinitionDeserializer extends DefinitionDeserializer<ProcedureDefinition> {
  public ProcedureDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ProcedureDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    Definition base = getDefinition(jObj);

    Set<ProcedureDefinition.Modifier> modifiers =
        getSet(context, jObj, "mods", ProcedureDefinition.Modifier.class);
    TypeString typeName = getTypeString(context, jObj, "type_n");

    String procedureName = nullableString(jObj, "proc_name");

    List<ParameterDefinition> parameters =
        getList(context, jObj, "params", ParameterDefinition.class);
    ExpressionResultString returnTypes =
        get(context, jObj, "ret", ExpressionResultString.class);
    ExpressionResultString loopTypes =
        get(context, jObj, "loop", ExpressionResultString.class);

    return new ProcedureDefinition(
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
  }
}
