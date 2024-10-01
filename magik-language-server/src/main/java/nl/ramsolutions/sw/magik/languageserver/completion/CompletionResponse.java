package nl.ramsolutions.sw.magik.languageserver.completion;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import nl.ramsolutions.sw.magik.analysis.definitions.MagikDefinition;
import org.eclipse.lsp4j.CompletionContext;

public class CompletionResponse {
  private static final AtomicLong idSeed = new AtomicLong(0);
  private Long id;
  private CompletionContext context;
  private final Map<String, String> commonData = new HashMap<>();
  private final List<MagikDefinition> definitions = new ArrayList<>();

  public CompletionResponse() {
    this.id = idSeed.incrementAndGet();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public CompletionContext getContext() {
    return context;
  }

  public void setContext(CompletionContext context) {
    this.context = context;
  }

  public String getCommonData(String key) {
    return this.commonData.get(key);
  }

  public void setCommonData(String key, String value) {
    this.commonData.put(key, value);
  }

  public List<MagikDefinition> getDefinitions() {
    return definitions;
  }
}
