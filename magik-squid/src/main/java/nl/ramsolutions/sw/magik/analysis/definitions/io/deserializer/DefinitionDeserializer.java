package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.google.gson.JsonObject;
import com.sonar.sslr.api.AstNode;
import edu.umd.cs.findbugs.annotations.Nullable;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;

public abstract class DefinitionDeserializer<T> extends BaseDeserializer<T> {

  public class DeserializedDefinition extends Definition {

    /**
     * Constructor.
     *
     * @param location Location.
     * @param moduleName Name of the module this definition resides in.
     * @param doc Doc.
     * @param node Node.
     */
    protected DeserializedDefinition(
        @Nullable Location location,
        @Nullable String moduleName,
        @Nullable String doc,
        @Nullable AstNode node) {
      super(location, moduleName, doc, node);
    }

    @Override
    public String getName() {
      return null;
    }

    @Override
    public String getPackage() {
      return null;
    }

    @Override
    public Definition getWithoutNode() {
      return null;
    }
  }

  public Definition getDefinition(JsonObject jObj) {
    return new DeserializedDefinition(
        getLocation(jObj), nullableString(jObj, "module_name"), nullableString(jObj, "doc"), null);
  }
}
