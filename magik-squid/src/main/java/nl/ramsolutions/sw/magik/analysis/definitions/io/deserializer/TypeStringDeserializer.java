package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;
import nl.ramsolutions.sw.magik.parser.TypeStringParser;

public final class TypeStringDeserializer extends BaseDeserializer<TypeString> {

  @Override
  public TypeString deserialize(
      final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
      throws JsonParseException {
    final String identifier = json.getAsString();
    return TypeStringParser.parseTypeString(identifier);
  }
}
