package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import nl.ramsolutions.sw.magik.PathMapping;

public final class LowerCaseEnumDeserializer<E extends Enum<?>> extends BaseDeserializer<E> {

  public LowerCaseEnumDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  @SuppressWarnings("unchecked")
  @Override
  public E deserialize(
      final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
      throws JsonParseException {
    final String value = json.getAsString().toUpperCase();
    if (typeOfT instanceof Class<?> clazz) {
      if (clazz.isEnum()) {
        return Arrays.stream(clazz.getEnumConstants())
            .filter(enumValue -> enumValue.toString().equals(value))
            .map(enumValue -> (E) enumValue)
            .findFirst()
            .orElseThrow();
      }
    }

    throw new IllegalStateException("Value '" + value + "' is not a known Enum value");
  }
}
