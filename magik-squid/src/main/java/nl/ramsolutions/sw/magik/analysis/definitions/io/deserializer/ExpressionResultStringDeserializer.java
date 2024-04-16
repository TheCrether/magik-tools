package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;
import nl.ramsolutions.sw.magik.parser.TypeStringParser;

public final class ExpressionResultStringDeserializer
    extends BaseDeserializer<ExpressionResultString> {

  public ExpressionResultStringDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ExpressionResultString deserialize(
      final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
      throws JsonParseException {
    if (json.isJsonPrimitive()
        && json.getAsString().equals(ExpressionResultString.UNDEFINED_SERIALIZED_NAME)) {
      return ExpressionResultString.UNDEFINED;
    } else if (json.isJsonArray()) {
      final List<TypeString> types =
          json.getAsJsonArray().asList().stream()
              .map(JsonElement::getAsString)
              .map(TypeStringParser::parseTypeString)
              .toList();
      return new ExpressionResultString(types);
    }

    throw new IllegalStateException();
  }
}
