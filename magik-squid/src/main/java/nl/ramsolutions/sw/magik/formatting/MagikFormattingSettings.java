package nl.ramsolutions.sw.magik.formatting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.ramsolutions.sw.MagikToolsProperties;

/** Settings for magik formatting. */
public class MagikFormattingSettings {

  private static final String KEY_VSC_PREFIX = "magik.";

  public static final String KEY_MAGIK_FORMATTING_INDENT_CHAR = "formatting.indentChar";
  public static final String KEY_MAGIK_FORMATTING_INDENT_WIDTH = "formatting.indentWidth";
  public static final String KEY_MAGIK_FORMATTING_INSERT_FINAL_NEWLINE =
      "formatting.insertFinalNewline";
  public static final String KEY_MAGIK_FORMATTING_TRIM_TRAILING_WHITESPACE =
      "formatting.trimTrailingWhitespace";
  public static final String KEY_MAGIK_FORMATTING_TRIM_FINAL_NEWLINES =
      "formatting.trimFinalNewlines";
  public static final String KEY_MAGIK_FORMATTING_SPACED_BRACES = "formatting.spacedBraces";
  public static final String KEY_MAGIK_FORMATTING_SPACED_BRACES_ON_EMPTY =
      "formatting.spacedBracesOnEmpty";
  public static final String KEY_MAGIK_FORMATTING_BRACES_ON_NEWLINE = "formatting.bracesOnNewline";

  private final MagikToolsProperties properties;

  /** Constructor. */
  public MagikFormattingSettings(final MagikToolsProperties properties) {
    MagikToolsProperties newProperties = new MagikToolsProperties();
    this.properties = properties;
  }

  /**
   * Utility method to convert camel case to kebab case.
   *
   * @param string String in kebab case.
   * @return String in camel case.
   */
  public static String toKebabCase(String string) {
    final Pattern patern = Pattern.compile("[A-Z]+(?![a-z])|[A-Z]");
    final Matcher matcher = patern.matcher(string);
    final String stringCamel =
        matcher.replaceAll(
            matchResult ->
                (matchResult.start() > 0 ? "-" : "") + matchResult.group(0).toLowerCase());
    return stringCamel.substring(0, 1).toLowerCase() + stringCamel.substring(1);
  }

  /**
   * Get the indent character. Defaults to tab.
   *
   * @return the indent character
   */
  public char getIndentChar() {
    final String def = FormattingOptions.DEFAULT_INDENT_CHAR;
    String indentChar =
        this.properties.getPropertyString(
            toKebabCase(KEY_VSC_PREFIX + KEY_MAGIK_FORMATTING_INDENT_CHAR),
            FormattingOptions.DEFAULT_INDENT_CHAR);

    String kebabized = toKebabCase(KEY_MAGIK_FORMATTING_INDENT_CHAR);
    if (this.properties.hasProperty(kebabized)) {
      indentChar = this.properties.getPropertyString(kebabized, def);
    }

    return indentChar.equals(FormattingOptions.TAB_INDENT_VALUE) ? '\t' : ' ';
  }

  /**
   * Get the indent width.
   *
   * @return the indent width
   */
  public int getIndentWidth() {
    int def = FormattingOptions.DEFAULT_INDENT_WIDTH;

    String kebabized = toKebabCase(KEY_MAGIK_FORMATTING_INDENT_WIDTH);
    if (this.properties.hasProperty(kebabized)) {
      return this.properties.getPropertyInteger(kebabized, def);
    }

    return this.properties.getPropertyInteger(
        KEY_VSC_PREFIX + KEY_MAGIK_FORMATTING_INDENT_WIDTH, def);
  }

  public boolean insertFinalNewline() {
    boolean def = FormattingOptions.DEFAULT_INSERT_FINAL_NEWLINE;

    String kebabized = toKebabCase(KEY_MAGIK_FORMATTING_INSERT_FINAL_NEWLINE);
    if (this.properties.hasProperty(kebabized)) {
      return this.properties.getPropertyBoolean(kebabized, def);
    }

    return this.properties.getPropertyBoolean(
        KEY_VSC_PREFIX + KEY_MAGIK_FORMATTING_INSERT_FINAL_NEWLINE, def);
  }

  public boolean trimTrailingWhitespace() {
    boolean def = FormattingOptions.DEFAULT_TRIM_TRAILING_WHITESPACE;

    String kebabized = toKebabCase(KEY_MAGIK_FORMATTING_TRIM_TRAILING_WHITESPACE);
    if (this.properties.hasProperty(kebabized)) {
      return this.properties.getPropertyBoolean(kebabized, def);
    }

    return this.properties.getPropertyBoolean(
        KEY_VSC_PREFIX + KEY_MAGIK_FORMATTING_TRIM_TRAILING_WHITESPACE, def);
  }

  public boolean trimFinalNewlines() {
    boolean def = FormattingOptions.DEFAULT_TRIM_FINAL_NEWLINES;

    String kebabized = toKebabCase(KEY_MAGIK_FORMATTING_TRIM_FINAL_NEWLINES);
    if (this.properties.hasProperty(kebabized)) {
      return this.properties.getPropertyBoolean(kebabized, def);
    }

    return this.properties.getPropertyBoolean(
        KEY_VSC_PREFIX + KEY_MAGIK_FORMATTING_TRIM_FINAL_NEWLINES, def);
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
    boolean def = FormattingOptions.DEFAULT_SPACED_BRACES;

    String kebabized = toKebabCase(KEY_MAGIK_FORMATTING_SPACED_BRACES);
    if (this.properties.hasProperty(kebabized)) {
      return this.properties.getPropertyBoolean(kebabized, def);
    }

    return this.properties.getPropertyBoolean(
        KEY_VSC_PREFIX + KEY_MAGIK_FORMATTING_SPACED_BRACES, def);
  }

  public boolean getBracesOnNewline() {
    boolean def = FormattingOptions.DEFAULT_BRACES_ON_NEWLINE;

    String kebabized = toKebabCase(KEY_MAGIK_FORMATTING_BRACES_ON_NEWLINE);
    if (this.properties.hasProperty(kebabized)) {
      return this.properties.getPropertyBoolean(kebabized, def);
    }

    return this.properties.getPropertyBoolean(
        KEY_VSC_PREFIX + KEY_MAGIK_FORMATTING_BRACES_ON_NEWLINE, def);
  }

  public boolean getSpacesBracesOnEmpty() {
    boolean def = FormattingOptions.DEFAULT_SPACED_BRACES_ON_EMPTY;

    String kebabized = toKebabCase(KEY_MAGIK_FORMATTING_SPACED_BRACES_ON_EMPTY);
    if (this.properties.hasProperty(kebabized)) {
      return this.properties.getPropertyBoolean(kebabized, def);
    }

    return this.properties.getPropertyBoolean(
        KEY_VSC_PREFIX + KEY_MAGIK_FORMATTING_SPACED_BRACES_ON_EMPTY, def);
  }
}
