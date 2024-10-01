package nl.ramsolutions.sw.magik.languageserver.completion;

import java.net.URI;
import java.util.Map;
import javax.annotation.Nullable;
import nl.ramsolutions.sw.MagikToolsProperties;
import nl.ramsolutions.sw.magik.analysis.definitions.MethodDefinition;
import nl.ramsolutions.sw.magik.languageserver.JSONUtility;
import nl.ramsolutions.sw.magik.languageserver.hover.HoverProvider;
import org.eclipse.lsp4j.CompletionItem;

public class CompletionHelper {
  public static final String REQUEST_ID_KEY = "rId";
  public static final String INDEX_KEY = "idx";
  public static final String URI_KEY = "uri";

  private final MagikToolsProperties properties;

  public CompletionHelper(MagikToolsProperties properties) {
    this.properties = properties;
  }

  public Map<String, String> getCompletionData(Long requestId, Integer index, URI uri) {
    return Map.of(
        REQUEST_ID_KEY, requestId.toString(), INDEX_KEY, index.toString(), URI_KEY, uri.toString());
  }

  private String buildMethodDocumentation(MethodDefinition methodDef) {
    StringBuilder builder = new StringBuilder();
    HoverProvider.buildMethodSignatureDoc(methodDef, builder, this.properties);
    return builder.toString();
  }

  public void setUriField(CompletionResponse response, URI uri) {
    response.setCommonData(URI_KEY, uri.toString());
  }

  @Nullable
  public static URI getUriField(CompletionItem response) {
    if (response.getData() == null) {
      return null;
    }

    @SuppressWarnings("unchecked")
    Map<String, String> data = JSONUtility.toModel(response.getData(), Map.class);

    String uriStr = data.get(URI_KEY);
    if (uriStr == null) {
      return null;
    }

    try {
      return URI.create(uriStr);
    } catch (Exception e) {
      return null;
    }
  }
}
