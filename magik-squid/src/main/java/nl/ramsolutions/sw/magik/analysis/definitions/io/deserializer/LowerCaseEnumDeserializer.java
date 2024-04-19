package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;

public final class LowerCaseEnumDeserializer<E extends Enum<?>> extends BaseDeserializer<E> {

  private Class<E> clazz;

  public LowerCaseEnumDeserializer(List<PathMapping> mappings, Class<E> clazz) {
    super(mappings);
    this.clazz = clazz;
  }

  @Override
  public E deserialize(JsonParser jp, DeserializationContext context) throws IOException {
    final String value = jp.getValueAsString().toUpperCase();
    if (clazz.isEnum()) {
      return Arrays.stream(clazz.getEnumConstants())
          .filter(enumValue -> enumValue.toString().equals(value))
          .map(enumValue -> (E) enumValue)
          .findFirst()
          .orElseThrow();
    }

    throw new IllegalStateException("Value '" + value + "' is not a known Enum value");
  }
}
