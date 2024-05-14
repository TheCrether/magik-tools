package nl.ramsolutions.sw.magik.languageserver.jsonrpc;

import java.util.Objects;
import org.eclipse.lsp4j.jsonrpc.util.Preconditions;
import org.eclipse.lsp4j.jsonrpc.util.ToStringBuilder;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

@SuppressWarnings("all")
public class LintIgnoreParams {
  @NonNull private String uri;

  public LintIgnoreParams() {}

  public LintIgnoreParams(@NonNull final String uri) {
    this.uri = Preconditions.checkNotNull(uri, "uri");
  }

  @NonNull
  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = Preconditions.checkNotNull(uri, "uri");
  }

  @Override
  public String toString() {
    ToStringBuilder b = new ToStringBuilder(this);
    b.add("uri", this.uri);
    return b.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LintIgnoreParams that = (LintIgnoreParams) o;
    return Objects.equals(getUri(), that.getUri());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getUri());
  }
}
