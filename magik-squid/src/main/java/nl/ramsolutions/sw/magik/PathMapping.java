package nl.ramsolutions.sw.magik;

import java.nio.file.Path;
import java.util.Objects;

public class PathMapping {
  private final Path from;
  private final Path to;
  private Boolean readOnly = false;

  public PathMapping(String from, String to) {
    this.from = Path.of(from);
    this.to = Path.of(to);
  }

  public PathMapping(String from, String to, Boolean readOnly) {
    this(from, to);
    this.readOnly = readOnly;
  }

  public Path getFrom() {
    return from;
  }

  public Path getTo() {
    return to;
  }

  public Boolean getReadOnly() {
    return readOnly;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PathMapping that = (PathMapping) o;
    return Objects.equals(getFrom(), that.getFrom())
        && Objects.equals(getTo(), that.getTo())
        && Objects.equals(getReadOnly(), that.getReadOnly());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getFrom(), getTo(), getReadOnly());
  }

  public Location mapLocation(Location location) {
    String path = location.getPath().toString();
    if (path.startsWith(this.from.toString())) {
      location =
          new Location(
              Path.of(path.replace(this.from.toString(), this.to.toString())).toUri(),
              location.getRange());
    }

    return location;
  }
}
