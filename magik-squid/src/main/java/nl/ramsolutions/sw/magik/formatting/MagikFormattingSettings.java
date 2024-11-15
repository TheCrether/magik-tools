package nl.ramsolutions.sw.magik.formatting;

import nl.ramsolutions.sw.MagikToolsProperties;

/** Settings for magik formatting. */
public class MagikFormattingSettings {

  public static final String KEY_MAGIK_FORMATTING_INDENT_CHAR = "magik.formatting.indentChar";
  public static final String KEY_MAGIK_FORMATTING_INDENT_WIDTH = "magik.formatting.indentWidth";
  public static final String KEY_MAGIK_FORMATTING_INSERT_FINAL_NEWLINE =
      "magik.formatting.insertFinalNewline";
  public static final String KEY_MAGIK_FORMATTING_TRIM_TRAILING_WHITESPACE =
      "magik.formatting.trimTrailingWhitespace";
  public static final String KEY_MAGIK_FORMATTING_TRIM_FINAL_NEWLINES =
      "magik.formatting.trimFinalNewlines";
  public static final String KEY_MAGIK_FORMATTING_SPACED_BRACES = "magik.formatting.spacedBraces";
  public static final String KEY_MAGIK_FORMATTING_SPACED_BRACES_ON_EMPTY =
      "magik.formatting.spacedBracesOnEmpty";
  public static final String KEY_MAGIK_FORMATTING_BRACES_ON_NEWLINE =
      "magik.formatting.bracesOnNewline";

  private final MagikToolsProperties properties;

  /** Constructor. */
  public MagikFormattingSettings(final MagikToolsProperties properties) {
    this.properties = properties;
  }

  /**
   * Get the indent character. Defaults to tab.
   *
   * @return the indent character
   */
  public char getIndentChar() {
    return this.properties
            .getPropertyString(
                KEY_MAGIK_FORMATTING_INDENT_CHAR, FormattingOptions.DEFAULT_INDENT_CHAR)
            .equals(FormattingOptions.DEFAULT_INDENT_CHAR)
        ? '\t'
        : ' ';
  }

  /**
   * Get the indent width.
   *
   * @return the indent width
   */
  public int getIndentWidth() {
    return this.properties.getPropertyInteger(
        KEY_MAGIK_FORMATTING_INDENT_WIDTH, FormattingOptions.DEFAULT_INDENT_WIDTH);
  }

  public boolean insertFinalNewline() {
    return this.properties.getPropertyBoolean(
        KEY_MAGIK_FORMATTING_INSERT_FINAL_NEWLINE, FormattingOptions.DEFAULT_INSERT_FINAL_NEWLINE);
  }

  public boolean trimTrailingWhitespace() {
    return this.properties.getPropertyBoolean(
        KEY_MAGIK_FORMATTING_TRIM_TRAILING_WHITESPACE,
        FormattingOptions.DEFAULT_TRIM_TRAILING_WHITESPACE);
  }

  public boolean trimFinalNewlines() {
    return this.properties.getPropertyBoolean(
        KEY_MAGIK_FORMATTING_TRIM_FINAL_NEWLINES, FormattingOptions.DEFAULT_TRIM_FINAL_NEWLINES);
  }

  public String getIndent() {
    final char indentChar = this.getIndentChar();
    if (indentChar == '\t') {
      return String.valueOf(indentChar);
    }

    final int indentWidth = this.getIndentWidth();
    return String.valueOf(indentChar).repeat(indentWidth);
  }

  public boolean getSpacedBraces() {
    return this.properties.getPropertyBoolean(
        KEY_MAGIK_FORMATTING_SPACED_BRACES, FormattingOptions.DEFAULT_SPACED_BRACES);
  }

  public boolean getBracesOnNewline() {
    return this.properties.getPropertyBoolean(
        KEY_MAGIK_FORMATTING_BRACES_ON_NEWLINE, FormattingOptions.DEFAULT_BRACES_ON_NEWLINE);
  }

  public boolean getSpacesBracesOnEmpty() {
    return this.properties.getPropertyBoolean(
        KEY_MAGIK_FORMATTING_SPACED_BRACES_ON_EMPTY,
        FormattingOptions.DEFAULT_SPACED_BRACES_ON_EMPTY);
  }
}
