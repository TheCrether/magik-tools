package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.BinaryOperatorDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.MagikDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class BinaryOperatorDefinitionDeserializer
    extends DefinitionDeserializer<BinaryOperatorDefinition> {
  public BinaryOperatorDefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public BinaryOperatorDefinition deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode node = jp.readValueAsTree();

    MagikDefinition base = getDefinition(node);

    String operator = getStringField(node, "operator");

    TypeString lhsTypeName = getTypeString(context, node, "lhs_type_n");
    TypeString rhsTypeName = getTypeString(context, node, "rhs_type_n");
    TypeString resultTypeName = getTypeString(context, node, "result_type_n");

    return new BinaryOperatorDefinition(
        base.getLocation(),
        base.getModuleName(),
        base.getDoc(),
        base.getNode(),
        operator,
        lhsTypeName,
        rhsTypeName,
        resultTypeName);
  }
}
