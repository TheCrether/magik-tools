package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import java.io.IOException;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;
import nl.ramsolutions.sw.magik.parser.TypeStringParser;

public final class TypeStringDeserializer extends BaseDeserializer<TypeString> {

  public TypeStringDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @Override
  public TypeString deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    final String identifier = jsonParser.getValueAsString();
    return TypeStringParser.parseTypeString(identifier);
  }
}
