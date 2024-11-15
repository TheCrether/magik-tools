package nl.ramsolutions.sw.magik.languageserver.diagnostics;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import nl.ramsolutions.sw.MagikToolsProperties;
import nl.ramsolutions.sw.magik.MagikTypedFile;
import nl.ramsolutions.sw.magik.languageserver.MagikLanguageServerSettings;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides diagnostics for Magik files. */
public class DiagnosticsProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticsProvider.class);

  private final Set<URI> ignoredUris;

  private final MagikToolsProperties properties;

  public DiagnosticsProvider(Set<URI> ignoredUris, final MagikToolsProperties properties) {
    this.ignoredUris = ignoredUris;
    this.properties = properties;
  }

  public void setCapabilities(final ServerCapabilities capabilities) {
    DiagnosticRegistrationOptions options = new DiagnosticRegistrationOptions();
    capabilities.setDiagnosticProvider(options);
    // No capabilities to set.
  }

  /**
   * Provides diagnostics for a Magik file.
   *
   * @param magikFile Magik file.
   * @return Diagnostics.
   */
  public List<Diagnostic> provideDiagnostics(
      final MagikTypedFile magikFile, CancelChecker checker) {
    final List<Diagnostic> diagnostics = new ArrayList<>();

    if (ignoredUris.contains(magikFile.getUri())) {
      return diagnostics;
    }

    if (checker.isCanceled()) {
      return diagnostics;
    }

    // Linter diagnostics.
    final List<Diagnostic> diagnosticsLinter =
        DiagnosticsProvider.getDiagnosticsFromLinter(magikFile, this.properties, checker);
    diagnostics.addAll(diagnosticsLinter);

    if (checker.isCanceled() || ignoredUris.contains(magikFile.getUri())) {
      return Collections.emptyList();
    }

    // Typing diagnostics.
    final MagikLanguageServerSettings settings = new MagikLanguageServerSettings(this.properties);
    final Boolean typingEnableChecks = settings.getTypingEnableChecks();
    if (Boolean.TRUE.equals(typingEnableChecks)) {
      final List<Diagnostic> diagnosticsTyping =
          DiagnosticsProvider.getDiagnosticsFromTyping(magikFile);
      diagnostics.addAll(diagnosticsTyping);
    }

    if (checker.isCanceled() || ignoredUris.contains(magikFile.getUri())) {
      return Collections.emptyList();
    }

    return diagnostics;
  }

  private static List<Diagnostic> getDiagnosticsFromLinter(
      final MagikTypedFile magikFile,
      final MagikToolsProperties globalProperties,
      CancelChecker checker) {
    final MagikToolsProperties magikFileProperties = magikFile.getProperties();

    final MagikToolsProperties properties = new MagikToolsProperties();
    properties.putAll(globalProperties);
    properties.putAll(magikFileProperties);

    final MagikChecksDiagnosticsProvider lintProvider =
        new MagikChecksDiagnosticsProvider(properties);

    try {
      return lintProvider.getDiagnostics(magikFile, checker);
    } catch (final IOException exception) {
      LOGGER.error(exception.getMessage(), exception);
    }

    return Collections.emptyList();
  }

  private static List<Diagnostic> getDiagnosticsFromTyping(final MagikTypedFile magikFile) {
    final MagikToolsProperties magikFileProperties = magikFile.getProperties();
    final MagikTypedChecksDiagnosticsProvider typedDiagnosticsProvider =
        new MagikTypedChecksDiagnosticsProvider(magikFileProperties);
    try {
      return typedDiagnosticsProvider.getDiagnostics(magikFile);
    } catch (final IOException exception) {
      LOGGER.error(exception.getMessage(), exception);
    }

    return Collections.emptyList();
  }
}
