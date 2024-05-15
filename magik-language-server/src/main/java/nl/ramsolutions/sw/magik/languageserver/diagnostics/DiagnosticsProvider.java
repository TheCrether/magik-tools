package nl.ramsolutions.sw.magik.languageserver.diagnostics;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import nl.ramsolutions.sw.magik.MagikTypedFile;
import nl.ramsolutions.sw.magik.languageserver.MagikSettings;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ServerCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides diagnostics for Magik files. */
public class DiagnosticsProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticsProvider.class);

  private final Set<URI> ignoredUris = new HashSet<>();

  public void setCapabilities(final ServerCapabilities capabilities) {
    // No capabilities to set.
  }

  /**
   * Provides diagnostics for a Magik file.
   *
   * @param magikFile Magik file.
   * @return Diagnostics.
   */
  public List<Diagnostic> provideDiagnostics(final MagikTypedFile magikFile) {
    final List<Diagnostic> diagnostics = new ArrayList<>();

    if (ignoredUris.contains(magikFile.getUri())) {
      return diagnostics;
    }

    // Linter diagnostics.
    final List<Diagnostic> diagnosticsLinter = this.getDiagnosticsFromLinter(magikFile);
    diagnostics.addAll(diagnosticsLinter);

    // Typing diagnostics.
    final Boolean typingEnableChecks = MagikSettings.INSTANCE.getTypingEnableChecks();
    if (Boolean.TRUE.equals(typingEnableChecks)) {
      final List<Diagnostic> diagnosticsTyping = this.getDiagnosticsFromTyping(magikFile);
      diagnostics.addAll(diagnosticsTyping);
    }

    return diagnostics;
  }

  private List<Diagnostic> getDiagnosticsFromLinter(final MagikTypedFile magikFile) {
    final Path overrideSettingsPath = MagikSettings.INSTANCE.getChecksOverrideSettingsPath();

    final MagikChecksDiagnosticsProvider lintProvider =
        new MagikChecksDiagnosticsProvider(overrideSettingsPath);
    try {
      return lintProvider.getDiagnostics(magikFile);
    } catch (final IOException exception) {
      LOGGER.error(exception.getMessage(), exception);
    }

    return Collections.emptyList();
  }

  private List<Diagnostic> getDiagnosticsFromTyping(final MagikTypedFile magikFile) {
    final Path overrideSettingsPath = MagikSettings.INSTANCE.getChecksOverrideSettingsPath();

    final MagikTypedChecksDiagnosticsProvider typedDiagnosticsProvider =
        new MagikTypedChecksDiagnosticsProvider(overrideSettingsPath);
    try {
      return typedDiagnosticsProvider.getDiagnostics(magikFile);
    } catch (final IOException exception) {
      LOGGER.error(exception.getMessage(), exception);
    }

    return Collections.emptyList();
  }

  public void addIgnoredUri(final String uri) {
    this.ignoredUris.add(URI.create(uri));
  }

  public void removeIgnoredUri(final String uri) {
    this.ignoredUris.remove(URI.create(uri));
  }
}
