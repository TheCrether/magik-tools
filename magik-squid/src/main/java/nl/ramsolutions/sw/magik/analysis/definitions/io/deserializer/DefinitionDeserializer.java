package nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.sonar.sslr.api.AstNode;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.Definition;

public abstract class DefinitionDeserializer<T> extends BaseDeserializer<T> {

  public DefinitionDeserializer(List<PathMapping> mappings) {
    super(mappings);
  }

  public static class DeserializedDefinition extends Definition {

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

  public Definition getDefinition(JsonNode node) {
    return new DeserializedDefinition(
        getLocation(node), nullableString(node, "mod_n"), nullableString(node, "doc"), null);
  }
}
