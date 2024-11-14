package nl.ramsolutions.sw.magik.checks;

import java.util.List;
import nl.ramsolutions.sw.MagikToolsProperties;
import nl.ramsolutions.sw.magik.CodeAction;
import nl.ramsolutions.sw.magik.MagikFile;
import nl.ramsolutions.sw.magik.Range;

/** Base class to provide automatic fixes for Magik checks. */
public abstract class MagikCheckFixer {

  private MagikToolsProperties properties;

  /**
   * Provide automatic fixes for violations detected by the sibling check.
   *
   * @return List of {@link CodeAction}s to be applied.
   */
  public abstract List<CodeAction> provideCodeActions(MagikFile magikFile, Range range);

  public MagikToolsProperties getProperties() {
    return properties;
  }

  public void setProperties(MagikToolsProperties properties) {
    this.properties = properties;
  }
}
