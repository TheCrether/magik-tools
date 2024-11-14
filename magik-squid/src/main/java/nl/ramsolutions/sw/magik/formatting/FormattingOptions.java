package nl.ramsolutions.sw.magik.formatting;

/** Formatting options for {@link FormattingStrategy}. */
public class FormattingOptions {

  private int tabSize;
  private boolean insertSpaces;
  private boolean insertFinalNewline;
  private boolean trimTrailingWhitespace;
  private boolean trimFinalNewlines;
  private boolean spacedBraces = true;

  public FormattingOptions(
      final int tabSize,
      final boolean insertSpaces,
      final boolean insertFinalNewline,
      final boolean trimTrailingWhitespace,
      final boolean trimFinalNewlines) {
    this.tabSize = tabSize;
    this.insertSpaces = insertSpaces;
    this.trimTrailingWhitespace = trimTrailingWhitespace;
    this.insertFinalNewline = insertFinalNewline;
    this.trimFinalNewlines = trimFinalNewlines;
  }

  public int getTabSize() {
    return this.tabSize;
  }

  public boolean isInsertSpaces() {
    return this.insertSpaces;
  }

  public boolean isInsertFinalNewline() {
    return this.insertFinalNewline;
  }

  public boolean isTrimTrailingWhitespace() {
    return this.trimTrailingWhitespace;
  }

  public boolean isTrimFinalNewlines() {
    return this.trimFinalNewlines;
  }

  public void setTabSize(int tabSize) {
    this.tabSize = tabSize;
  }

  public void setInsertSpaces(boolean insertSpaces) {
    this.insertSpaces = insertSpaces;
  }

  public void setInsertFinalNewline(boolean insertFinalNewline) {
    this.insertFinalNewline = insertFinalNewline;
  }

  public void setTrimTrailingWhitespace(boolean trimTrailingWhitespace) {
    this.trimTrailingWhitespace = trimTrailingWhitespace;
  }

  public void setTrimFinalNewlines(boolean trimFinalNewlines) {
    this.trimFinalNewlines = trimFinalNewlines;
  }

  public boolean isSpacedBraces() {
    return spacedBraces;
  }

  public void setSpacedBraces(boolean spacedBraces) {
    this.spacedBraces = spacedBraces;
  }
}
