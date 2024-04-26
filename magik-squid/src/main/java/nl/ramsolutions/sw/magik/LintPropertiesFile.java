package nl.ramsolutions.sw.magik;

import java.net.URI;
import nl.ramsolutions.sw.OpenedFile;

public class LintPropertiesFile extends OpenedFile {
  /**
   * Constructor.
   *
   * @param uri URI.
   * @param source Source.
   */
  public LintPropertiesFile(URI uri, String source) {
    super(uri, source);
  }

  @Override
  public String getLanguageId() {
    return "properties";
  }
}
