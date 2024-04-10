package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.analysis.definitions.ExemplarDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.SlotDefinition;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;

public class ExemplarDefinitionDeserializer extends BaseDeserializer<ExemplarDefinition> {
  @Override
  public ExemplarDefinition deserialize(
      JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jObj = json.getAsJsonObject();
    String moduleName = nullableString(jObj, "module_name");
    String doc = nullableString(jObj, "doc");
    ExemplarDefinition.Sort sort = get(context, jObj, "sort", ExemplarDefinition.Sort.class);
    TypeString typeName = getTypeString(context, jObj, "type_name");

    List<SlotDefinition> slots = getList(context, jObj, "slots", SlotDefinition.class);
    List<TypeString> parents = getList(context, jObj, "parents", TypeString.class);
    Set<String> topics = getSet(context, jObj, "topics", String.class);

    Location location = getLocation(jObj);

    return new ExemplarDefinition(
        location, moduleName, doc, null, sort, typeName, slots, parents, topics);
  }
}
