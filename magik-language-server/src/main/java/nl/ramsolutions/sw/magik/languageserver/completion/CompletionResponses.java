package nl.ramsolutions.sw.magik.languageserver.completion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public class CompletionResponses {
  private CompletionResponses() {
    // Don't instantiate
  }

  private static final Map<Long, CompletionResponse> COMPLETIONS = new ConcurrentHashMap<>();

  public static CompletionResponse get(Long id) {
    return COMPLETIONS.get(id);
  }

  public static void store(@Nullable CompletionResponse response) {
    if (response != null) {
      COMPLETIONS.put(response.getId(), response);
    }
  }

  public static void delete(@Nullable CompletionResponse response) {
    if (response != null) {
      COMPLETIONS.remove(response.getId());
    }
  }

  public static void clear() {
    COMPLETIONS.clear();
  }
}
