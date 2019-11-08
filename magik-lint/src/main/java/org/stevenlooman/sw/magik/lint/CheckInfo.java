package org.stevenlooman.sw.magik.lint;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.sonar.check.RuleProperty;
import org.stevenlooman.sw.magik.CheckList;
import org.stevenlooman.sw.magik.MagikCheck;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class CheckInfo {

  class ParameterInfo {

    private String name;
    private String description;
    private Object value;

    ParameterInfo(String name, String description, Object value) {
      this.name = name;
      this.description = description;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public Object getValue() {
      return value;
    }
  }

  private MagikCheck check;
  private boolean enabled;

  public CheckInfo(MagikCheck check) {
    this.check = check;
    this.enabled = true;
  }

  public MagikCheck getCheck() {
    return check;
  }

  public boolean isEnabled() {
    return enabled;
  }

  void setDisabled() {
    enabled = false;
  }

  /**
   * Get all parameters.
   * @return List of ParameterInfo
   * @throws IllegalAccessException -
   */
  public Iterable<ParameterInfo> getParameters() throws IllegalAccessException {
    List<ParameterInfo> parameters = new ArrayList<>();

    for (Field field: check.getClass().getFields()) {
      RuleProperty ruleProperty = field.getAnnotation(RuleProperty.class);
      if (ruleProperty == null) {
        continue;
      }

      String key = ruleProperty.key().replaceAll(" ", "-");
      Object value = field.get(check);
      String description = ruleProperty.description();
      ParameterInfo parameterInfo = new ParameterInfo(key, description, value);
      parameters.add(parameterInfo);
    }

    return parameters;
  }

  private JSONObject readMetadata() {
    // determine path
    Class<?> klass = check.getClass();
    String simpleName = klass.getSimpleName();
    String name = simpleName.substring(0, simpleName.length() - 5);  // strip Check
    String filename = "/" + CheckList.PROFILE_DIR + "/" + name + ".json";

    // parse json
    InputStream inputStream = getClass().getResourceAsStream(filename);
    JSONTokener tokener = new JSONTokener(inputStream);
    JSONObject object = new JSONObject(tokener);
    return object;
  }

  public String getSqKey() throws FileNotFoundException {
    JSONObject metadata = readMetadata();
    return metadata.getString("sqKey");
  }

  public String getSeverity() throws FileNotFoundException {
    JSONObject metadata = readMetadata();
    return metadata.getString("defaultSeverity");
  }

  /**
   * Get tags for this check.
   * @return List of tags.
   * @throws FileNotFoundException -
   */
  public List<String> getTags() throws FileNotFoundException {
    JSONObject metadata = readMetadata();
    JSONArray tags = metadata.getJSONArray("tags");
    List<String> tagsList = new ArrayList<>();
    for (int i = 0; i < tags.length(); ++i) {
      String tag = tags.getString(i);
      tagsList.add(tag);
    }
    return tagsList;
  }

  /**
   * Get the first tag for this check.
   * @return First tag.
   * @throws FileNotFoundException -
   */
  public String getTag() throws FileNotFoundException {
    List<String> tags = getTags();
    if (tags.isEmpty()) {
      return null;
    }
    return tags.get(0);
  }

  public String getTitle() throws FileNotFoundException {
    JSONObject metadata = readMetadata();
    return metadata.getString("title");
  }

  /**
   * Set a parameter.
   * @param name Key of parameter to set
   * @param value Value of parameter to set
   * @throws IllegalAccessException -
   */
  public void setParameter(String name, Object value) throws IllegalAccessException {
    boolean found = false;
    for (Field field: check.getClass().getFields()) {
      RuleProperty ruleProperty = field.getAnnotation(RuleProperty.class);
      if (ruleProperty == null) {
        continue;
      }
      String key = ruleProperty.key().replaceAll(" ", "-");
      if (key.equals(name)) {
        field.set(check, value);
        found = true;
      }
    }

    if (!found) {
      throw new Error("Parameter '" + name + "' not found");
    }
  }

  /**
   * Get all checks, enabled in the given configuration.
   * @param config Configuration to use
   * @return Collection of checks
   * @throws IllegalAccessException -
   * @throws InstantiationException -
   * @throws FileNotFoundException -
   */
  public static List<CheckInfo> getAllChecks(Configuration config) throws
      IllegalAccessException, InstantiationException {
    List<CheckInfo> checkInfos = new ArrayList<>();

    List<String> disabled = config.getPropertySplit("disabled");
    if (disabled == null) {
      disabled = new ArrayList<>();
    }

    List<Class<?>> checkClasses = CheckList.getChecks();
    for (Class<?> checkClass: checkClasses) {
      MagikCheck check = (MagikCheck) checkClass.newInstance();

      CheckInfo checkInfo = new CheckInfo(check);
      String name = check.getCheckKeyKebabCase();
      boolean enabled = !disabled.contains(name);
      if (!enabled) {
        checkInfo.setDisabled();
      }

      // set properties on check, if given
      for (Field field: check.getClass().getFields()) {
        RuleProperty ruleProperty = field.getAnnotation(RuleProperty.class);
        if (ruleProperty != null) {
          String key = ruleProperty.key().replaceAll(" ", "-");
          String configKey = name + "." + key;
          if (!config.hasProperty(configKey)) {
            continue;
          }

          if (field.getType() == int.class) {
            Integer configValue = config.getPropertyInt(configKey);
            checkInfo.setParameter(key, configValue);
          } else if (field.getType() == String.class) {
            String configValue = config.getProperty(configKey);
            checkInfo.setParameter(key, configValue);
          }
        }
      }

      checkInfos.add(checkInfo);
    }
    return checkInfos;
  }

}
