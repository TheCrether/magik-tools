package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import nl.ramsolutions.sw.magik.analysis.definitions.BinaryOperatorDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class BinaryOperatorDefinitionDeserializer
    extends DefinitionDeserializer<BinaryOperatorDefinition> {
  @Override
  public BinaryOperatorDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();

    Definition base = getDefinition(jObj);

    String operator = getString(jObj, "operator");

    TypeString lhsTypeName = getTypeString(context, jObj, "lhs_type_name");
    TypeString rhsTypeName = getTypeString(context, jObj, "rhs_type_name");
    TypeString resultTypeName = getTypeString(context, jObj, "result_type_name");

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
