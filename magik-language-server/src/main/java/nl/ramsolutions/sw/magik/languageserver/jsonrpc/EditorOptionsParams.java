package nl.ramsolutions.sw.magik.languageserver.jsonrpc;

import java.util.Objects;

public class EditorOptionsParams {
  private int tabSize;
  private boolean insertSpaces;
  private String uri;

  @SuppressWarnings("unused")
  public EditorOptionsParams() {}

  @SuppressWarnings("unused")
  public EditorOptionsParams(boolean insertSpaces, int tabSize, String uri) {
    this.insertSpaces = insertSpaces;
    this.tabSize = tabSize;
    this.uri = uri;
  }

  public int getTabSize() {
    return tabSize;
  }

  public void setTabSize(int tabSize) {
    this.tabSize = tabSize;
  }

  public boolean isInsertSpaces() {
    return insertSpaces;
  }

  public void setInsertSpaces(boolean insertSpaces) {
    this.insertSpaces = insertSpaces;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EditorOptionsParams that = (EditorOptionsParams) o;
    return tabSize == that.tabSize
        && insertSpaces == that.insertSpaces
        && Objects.equals(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tabSize, insertSpaces, uri);
  }

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }
}
