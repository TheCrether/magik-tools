package nl.ramsolutions.sw.magik.languageserver;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

public interface MagikLanguageClient extends LanguageClient {
  @JsonNotification("indexed")
  void sendIndexed();
}
