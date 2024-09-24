package nl.ramsolutions.sw.magik.checks;

import java.util.*;
import java.util.stream.Collectors;
import nl.ramsolutions.sw.MagikToolsProperties;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;

/** {@link MagikCheck} specific configuration. */
public class MagikChecksConfiguration {

  public static final String KEY_DISABLED_CHECKS = "disabled";
  public static final String KEY_ENABLED_CHECKS = "enabled";
  public static final String KEY_IGNORED_PATHS = "ignore";

  private final MagikToolsProperties properties;
  private final List<Class<? extends MagikCheck>> checkClasses;

  /**
   * Constructor which reads properties from {@code path}.
   *
   * @param checkClasses {@link Class}es of {@link MagikCheck}s.
   * @param properties Properties to use.
   */
  public MagikChecksConfiguration(
      final List<Class<? extends MagikCheck>> checkClasses, final MagikToolsProperties properties) {
    this.checkClasses = checkClasses;
    this.properties = properties;
  }

  public List<String> getIgnores() {
    return this.properties.getPropertyList(KEY_IGNORED_PATHS);
  }

  /**
   * Get {@link MagikCheck}s, each contained by a {@link MagikCheckHolder}.
   *
   * @return
   */
  public List<MagikCheckHolder> getAllChecks() {
    final List<String> disabled = this.properties.getPropertyList(KEY_DISABLED_CHECKS);
    if (disabled.contains("all")) {
      return Collections.emptyList();
    }

    final List<String> enabled = this.properties.getPropertyList(KEY_ENABLED_CHECKS);
    final List<Class<? extends MagikCheck>> disabledByDefault =
        CheckList.getDisabledByDefaultChecks();

    final List<MagikCheckHolder> holders = new ArrayList<>();

    for (final Class<? extends MagikCheck> checkClass : this.checkClasses) {
      final String checkKey = MagikChecksConfiguration.checkKey(checkClass);
      final boolean checkEnabled =
          enabled.contains(checkKey)
              || (!disabled.contains(checkKey) && !disabledByDefault.contains(checkClass));

      // Gather parameters from MagikCheck, value from config.
      final Set<MagikCheckHolder.Parameter> parameters =
          Arrays.stream(checkClass.getFields())
              .map(field -> field.getAnnotation(RuleProperty.class))
              .filter(Objects::nonNull)
              .map(
                  ruleProperty -> {
                    final String propertyKey = MagikChecksConfiguration.propertyKey(ruleProperty);
                    final String configKey = checkKey + "." + propertyKey;
                    if (!this.properties.hasProperty(configKey)) {
                      return null;
                    }

                    // Store parameter.
                    final String description = ruleProperty.description();
                    final MagikCheckHolder.Parameter parameter;
                    if (ruleProperty.type().equals("INTEGER")) {
                      final Integer configValue = this.properties.getPropertyInteger(configKey);
                      parameter =
                          new MagikCheckHolder.Parameter(configKey, description, configValue);
                    } else if (ruleProperty.type().equals("STRING")) {
                      final String configValue = this.properties.getPropertyString(configKey);
                      parameter =
                          new MagikCheckHolder.Parameter(configKey, description, configValue);
                    } else if (ruleProperty.type().equals("BOOLEAN")) {
                      final Boolean configValue = this.properties.getPropertyBoolean(configKey);
                      parameter =
                          new MagikCheckHolder.Parameter(configKey, description, configValue);
                    } else {
                      throw new IllegalStateException(
                          "Unknown type for property: " + ruleProperty.type());
                    }

                    return parameter;
                  })
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());

      final MagikCheckHolder holder = new MagikCheckHolder(checkClass, parameters, checkEnabled);
      holders.add(holder);
    }
    return holders;
  }

  public static String checkKey(final Class<?> checkClass) {
    final Rule annotation = checkClass.getAnnotation(Rule.class);
    final String checkKey = annotation.key();
    return MagikCheckHolder.toKebabCase(checkKey);
  }

  public static String propertyKey(final RuleProperty ruleProperty) {
    return ruleProperty.key().replace(" ", "-");
  }
}
