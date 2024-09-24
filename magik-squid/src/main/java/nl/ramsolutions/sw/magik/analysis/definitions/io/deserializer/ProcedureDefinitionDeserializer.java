package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.MagikDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.ParameterDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.ProcedureDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class ProcedureDefinitionDeserializer extends DefinitionDeserializer<ProcedureDefinition> {
  public ProcedureDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ProcedureDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.readValueAsTree();

    MagikDefinition base = getDefinition(node);

    Set<ProcedureDefinition.Modifier> modifiers =
        getSet(context, node, "mods", ProcedureDefinition.Modifier.class);
    TypeString typeName = getTypeString(context, node, "type_n");

    String procedureName = nullableString(node, "proc_name");

    List<ParameterDefinition> parameters =
        getList(context, node, "params", ParameterDefinition.class);
    ExpressionResultString returnTypes = get(context, node, "ret", ExpressionResultString.class);
    ExpressionResultString loopTypes = get(context, node, "loop", ExpressionResultString.class);

    return new ProcedureDefinition(
        base.getLocation(),
        base.getTimestamp(),
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
