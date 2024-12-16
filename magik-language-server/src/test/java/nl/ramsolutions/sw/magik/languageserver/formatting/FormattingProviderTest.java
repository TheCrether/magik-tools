package nl.ramsolutions.sw.magik.languageserver.formatting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import nl.ramsolutions.sw.MagikToolsProperties;
import nl.ramsolutions.sw.magik.MagikTypedFile;
import nl.ramsolutions.sw.magik.analysis.definitions.DefinitionKeeper;
import nl.ramsolutions.sw.magik.analysis.definitions.IDefinitionKeeper;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

/** Test FormattingProvider. */
@SuppressWarnings("checkstyle:MagicNumber")
class FormattingProviderTest {

  private List<TextEdit> getEdits(final String code) {
    final FormattingOptions options = new FormattingOptions();
    return this.getEdits(code, options);
  }

  private List<TextEdit> getEdits(final String code, final FormattingOptions options) {
    return this.getEdits(code, options, new MagikToolsProperties());
  }

  private List<TextEdit> getEdits(
      final String code, final FormattingOptions options, MagikToolsProperties properties) {
    final IDefinitionKeeper definitionKeeper = new DefinitionKeeper();
    final MagikTypedFile magikFile =
        new MagikTypedFile(MagikTypedFile.DEFAULT_URI, code, definitionKeeper);

    final FormattingProvider provider = new FormattingProvider(Collections.emptySet());
    return provider.provideFormatting(magikFile, options, new MagikToolsProperties());
  }

  @Test
  void testFormattingSomething() {
    final String code =
        """
        _method a. b(x, y, z)
        _endmethod
        """;
    final List<TextEdit> edits = this.getEdits(code);
    assertThat(edits)
        .containsExactly(new TextEdit(new Range(new Position(0, 10), new Position(0, 11)), ""));
  }

  @Test
  void testFormattingNothing() {
    final String code =
        """
        _method a.b(x, y, z)
        _endmethod
        """;
    final List<TextEdit> edits = this.getEdits(code);
    assertThat(edits).isEmpty();
  }
}
