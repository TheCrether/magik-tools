package nl.ramsolutions.sw.magik.languageserver.formatting;

import com.sonar.sslr.api.AstNode;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import nl.ramsolutions.sw.MagikToolsProperties;
import nl.ramsolutions.sw.magik.MagikFile;
import nl.ramsolutions.sw.magik.api.MagikGrammar;
import nl.ramsolutions.sw.magik.formatting.FormattingWalker;
import nl.ramsolutions.sw.magik.formatting.MagikFormattingSettings;
import nl.ramsolutions.sw.magik.languageserver.Lsp4jConversion;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Formatting provider. */
public class FormattingProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(FormattingProvider.class);
  private final Set<URI> ignoredUris;

  public FormattingProvider(Set<URI> ignoredUris) {
    this.ignoredUris = ignoredUris;
  }

  public void setCapabilities(final ServerCapabilities capabilities) {
    capabilities.setDocumentFormattingProvider(true);
  }

  /**
   * Provide formatting for text.
   *
   * @param magikFile Magik file.
   * @param options Formatting options
   * @return {@link TextEdit}s.
   */
  public List<TextEdit> provideFormatting(
      final MagikFile magikFile,
      final FormattingOptions options,
      MagikToolsProperties toolsProperties) {
    if (ignoredUris.contains(magikFile.getUri())) {
      return Collections.emptyList();
    }

    final AstNode node = magikFile.getTopNode();

    final MagikToolsProperties properties =
        MagikToolsProperties.merge(toolsProperties, magikFile.getProperties());
    final MagikFormattingSettings settings = new MagikFormattingSettings(properties);

    final nl.ramsolutions.sw.magik.formatting.FormattingOptions magikToolsFormattingOptions =
        Lsp4jConversion.formattingOptionsFromLsp4j(options);
    magikToolsFormattingOptions.setTabSize(settings.getIndentWidth());
    magikToolsFormattingOptions.setInsertSpaces(settings.getIndentChar() == ' ');
    magikToolsFormattingOptions.setSpacedBraces(settings.getSpacedBraces());
    magikToolsFormattingOptions.setSpacedBracesOnEmpty(settings.getSpacesBracesOnEmpty());
    final FormattingWalker walker = new FormattingWalker(magikToolsFormattingOptions);
    walker.walkAst(node);
    final List<nl.ramsolutions.sw.magik.TextEdit> textEdits = walker.getTextEdits();
    return textEdits.stream().map(Lsp4jConversion::textEditToLsp4j).toList();
  }

  /**
   * Test if formatting can be provided.
   *
   * <p>A SYNTAX_ERROR prevents formatting.
   *
   * @param magikFile Magik file.
   * @return False if AST contains a SYNTAX_ERROR, true otherwise.
   */
  public boolean canFormat(final MagikFile magikFile) {
    final AstNode node = magikFile.getTopNode();
    return node.getFirstDescendant(MagikGrammar.SYNTAX_ERROR) == null;
  }
}
