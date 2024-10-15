package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;
import nl.ramsolutions.sw.magik.parser.TypeStringParser;

public final class ExpressionResultStringDeserializer
    extends BaseDeserializer<ExpressionResultString> {

  private static final ExpressionResultString EMPTY_RESULT_STRING = new ExpressionResultString();

  public ExpressionResultStringDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public ExpressionResultString deserialize(JsonParser jp, DeserializationContext context)
      throws IOException {
    JsonNode json = jp.readValueAsTree();
    if (asString(json) != null) {
      if (json.asText().equals(ExpressionResultString.UNDEFINED_SERIALIZED_NAME)) {
        return ExpressionResultString.UNDEFINED;
      }

      return new ExpressionResultString(TypeStringParser.parseTypeString(asString(json)));
    } else if (json.isArray()) {
      final List<TypeString> types =
          StreamSupport.stream(json.spliterator(), false)
              .map(BaseDeserializer::asString)
              .filter(Objects::nonNull)
              .map(TypeStringParser::parseTypeString)
              .toList();
      return new ExpressionResultString(types);
    } else {
      return EMPTY_RESULT_STRING;
    }

    //    throw new IllegalStateException();
  }
}
