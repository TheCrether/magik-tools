package nl.ramsolutions.sw.magik.checks.checks;

import com.sonar.sslr.api.*;
import java.net.URI;
import java.util.Set;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.Range;
import nl.ramsolutions.sw.magik.api.MagikPunctuator;
import nl.ramsolutions.sw.magik.checks.MagikCheck;
import nl.ramsolutions.sw.magik.formatting.FormattingOptions;
import nl.ramsolutions.sw.magik.formatting.FormattingWalker;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;

/** Check for formatting errors. */
@Rule(key = FormattingCheck.CHECK_KEY)
public class FormattingCheck extends MagikCheck {
  @SuppressWarnings("checkstyle:JavadocVariable")
  public static final String CHECK_KEY = "Formatting";

  private static final String MESSAGE = "Improper formatting: %s.";

  private static final String DEFAULT_INDENT_CHARACTER = FormattingOptions.DEFAULT_INDENT_CHAR;
  private static final int DEFAULT_TAB_WIDTH = FormattingOptions.DEFAULT_INDENT_WIDTH;
  private static final boolean DEFAULT_SPACED_BRACES = FormattingOptions.DEFAULT_SPACED_BRACES;
  private static final boolean DEFAULT_SPACED_BRACES_ON_EMPTY =
      FormattingOptions.DEFAULT_SPACED_BRACES_ON_EMPTY;

  private static final Set<String> AUGMENTED_ASSIGNMENT_TOKENS =
      Set.of(
          "_is", "_isnt", "_andif", "_and", "_orif", "_or", "_xor", "_div", "_mod", "_cf", "+", "-",
          "*", "/", "**", "=", "~=");
  private static final Set<String> SPACED_BRACES_L =
      Set.of(MagikPunctuator.BRACE_L.getValue(), MagikPunctuator.PAREN_L.getValue());
  private static final Set<String> SPACED_BRACES_R =
      Set.of(MagikPunctuator.BRACE_R.getValue(), MagikPunctuator.PAREN_R.getValue());

  /** The character used for indentation (tab/space). */
  @RuleProperty(
      key = "indent character",
      description = "The character used for indentation (tab/space)",
      defaultValue = DEFAULT_INDENT_CHARACTER,
      type = "STRING")
  @SuppressWarnings("checkstyle:VisibilityModifier")
  public String indentCharacter = DEFAULT_INDENT_CHARACTER;

  /** The width of a tab character. */
  @RuleProperty(
      key = "tab width",
      description = "The width of a tab character",
      defaultValue = "" + DEFAULT_TAB_WIDTH,
      type = "INTEGER")
  @SuppressWarnings("checkstyle:VisibilityModifier")
  public int tabWidth = DEFAULT_TAB_WIDTH;

  @RuleProperty(
      key = "spaced braces",
      description = "Whether braces should have whitespace around ((), {})",
      defaultValue = "" + DEFAULT_SPACED_BRACES,
      type = "BOOLEAN")
  public Boolean spacedBraces = DEFAULT_SPACED_BRACES;

  @RuleProperty(
      key = "spaced braces on empty",
      description = "Whether empty braces should have whitespace inside ((), {})",
      defaultValue = "" + DEFAULT_SPACED_BRACES_ON_EMPTY,
      type = "BOOLEAN")
  public Boolean spacedBracesOnEmpty = DEFAULT_SPACED_BRACES_ON_EMPTY;

  @Override
  protected void walkPostMagik(final AstNode node) {
    final boolean insertSpaces = this.indentCharacter.equalsIgnoreCase("space");
    final FormattingOptions formattingOptions =
        new FormattingOptions(this.tabWidth, insertSpaces, false, false, false);
    formattingOptions.setSpacedBraces(this.spacedBraces);
    formattingOptions.setSpacedBracesOnEmpty(this.spacedBracesOnEmpty);
    final FormattingWalker walker = new FormattingWalker(formattingOptions);
    final AstNode topNode = this.getMagikFile().getTopNode();
    walker.walkAst(topNode);

    final URI uri = this.getMagikFile().getUri();
    walker
        .getTextEdits()
        .forEach(
            textEdit -> {
              final String reason = textEdit.getReason();
              final String message = String.format(MESSAGE, reason);
              final Range range = textEdit.getRange();
              final Location location = new Location(uri, range);
              this.addIssue(location, message);
            });
  }
}
