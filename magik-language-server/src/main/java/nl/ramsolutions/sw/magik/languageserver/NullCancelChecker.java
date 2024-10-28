package nl.ramsolutions.sw.magik.languageserver;

import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class NullCancelChecker implements CancelChecker {
  @Override
  public void checkCanceled() {}

  @Override
  public boolean isCanceled() {
    return false;
  }
}
