package nl.ramsolutions.sw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import nl.ramsolutions.sw.magik.MagikFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Magik-tools properties.
 *
 * <p>Note that this is currently used in (at least) three ways: - Settings for
 * magik-language-server settings - Settings for magik-lint from command line - Settings for
 * `magik-lint.properties` files for a given {@link MagikFile}
 *
 * <p>These are separate code-paths, but given the shared used of {@link MagikToolsProperties} the
 * separation can be confusing.
 *
 * <p>The helper classes {@link ConfigurationLocator} and {@link ConfigurationReader} are used to
 * locate and read the settings.
 */
public class MagikToolsProperties {

  public static final MagikToolsProperties DEFAULT_PROPERTIES = new MagikToolsProperties(Map.of());
  public static final String LIST_SEPARATOR = ",";

  /**
   * this is used for when an array of objects is separated into a property list `\u001b`
   * corresponds to the ESCAPE key
   */
  public static final String LIST_SEPARATOR_OBJ = "\u001b";

  private static final Logger LOGGER = LoggerFactory.getLogger(MagikToolsProperties.class);

  private final Properties properties = new Properties();

  public MagikToolsProperties() {
    this.putAll(MagikToolsProperties.DEFAULT_PROPERTIES);
  }

  public MagikToolsProperties(final Map<String, String> properties) {
    this.properties.putAll(properties);
  }

  /**
   * Constructor.
   *
   * @param path Path to properties file.
   * @throws IOException -
   */
  public MagikToolsProperties(final Path path) throws IOException {
    LOGGER.debug("Reading configuration from: {}", path.toAbsolutePath());
    try (final FileInputStream inputStream = new FileInputStream(path.toFile())) {
      this.properties.load(inputStream);
    }
  }

  public void clear() {
    this.properties.clear();
  }

  private MagikToolsProperties(final InputStream stream) throws IOException {
    this.putAll(MagikToolsProperties.DEFAULT_PROPERTIES);
    this.properties.load(stream);
  }

  public void reset() {
    this.clear();
    this.putAll(MagikToolsProperties.DEFAULT_PROPERTIES);
  }

  public void putAll(final Properties newProperties) {
    this.properties.putAll(newProperties);
  }

  public void putAll(final MagikToolsProperties newProperties) {
    this.properties.putAll(newProperties.properties);
  }

  /**
   * Set property.
   *
   * @param key Key of property.
   * @param value Value of property.
   */
  public void setProperty(final String key, @Nullable final String value) {
    if (value == null) {
      this.properties.remove(key);
      return;
    }

    this.properties.setProperty(key, value);
  }

  /**
   * Set property.
   *
   * @param key Key of property.
   * @param value Value of property.
   */
  public void setProperty(final String key, @Nullable final Integer value) {
    if (value == null) {
      this.properties.remove(key);
      return;
    }

    final String valueStr = Integer.toString(value);
    this.properties.setProperty(key, valueStr);
  }

  /**
   * Set property.
   *
   * @param key Key of property.
   * @param value Value of property.
   */
  public void setProperty(final String key, @Nullable final Long value) {
    if (value == null) {
      this.properties.remove(key);
      return;
    }

    final String valueStr = Long.toString(value);
    this.properties.setProperty(key, valueStr);
  }

  /**
   * Set property.
   *
   * @param key Key of property.
   * @param value Value of property.
   */
  public void setProperty(final String key, @Nullable final Boolean value) {
    if (value == null) {
      this.properties.remove(key);
      return;
    }

    final String valueStr = Boolean.toString(value);
    this.properties.setProperty(key, valueStr);
  }

  /**
   * Get property as {@link String}.
   *
   * @param key Key of property.
   * @return Value of property.
   */
  @CheckForNull
  public String getPropertyString(final String key) {
    return this.properties.getProperty(key);
  }

  /**
   * Get property as {@link String}, or default value.
   *
   * @param key Key of property.
   * @param defaultValue Default value, if property does not exist.
   * @return Value of property.
   */
  public String getPropertyString(final String key, final String defaultValue) {
    return this.properties.getProperty(key, defaultValue);
  }

  /**
   * Get property as {@link Boolean}.
   *
   * @param key Key of property.
   * @return Value of property.
   */
  @CheckForNull
  public Boolean getPropertyBoolean(final String key) {
    final String value = this.getPropertyString(key);
    if (value == null) {
      return null;
    }

    return Boolean.valueOf(value);
  }

  /**
   * Get property as {@link Boolean}, or default value.
   *
   * @param key Key of property.
   * @param defaultValue Default value, if property does not exist.
   * @return Value of property.
   */
  public boolean getPropertyBoolean(final String key, final boolean defaultValue) {
    final String value = this.getPropertyString(key);
    if (value == null) {
      return defaultValue;
    }

    return Boolean.valueOf(value);
  }

  /**
   * Get property as {@link Integer}.
   *
   * @param key Key of property.
   * @return Value of property, as Integer.
   */
  @CheckForNull
  public Integer getPropertyInteger(final String key) {
    final String value = this.getPropertyString(key);
    if (value == null) {
      return null;
    }

    return Integer.valueOf(value);
  }

  /**
   * Get property as {@link Integer}, or default value.
   *
   * @param key Key of property.
   * @param defaultValue Default value, if property does not exist.
   * @return Value of property.
   */
  public int getPropertyInteger(final String key, final int defaultValue) {
    final String value = this.getPropertyString(key);
    if (value == null) {
      return defaultValue;
    }

    return Integer.valueOf(value);
  }

  /**
   * Get property as {@link Long}.
   *
   * @param key Key of property.
   * @return Value of property, as Long.
   */
  @CheckForNull
  public Long getPropertyLong(final String key) {
    final String value = this.getPropertyString(key);
    if (value == null) {
      return null;
    }

    return Long.valueOf(value);
  }

  /**
   * Get property as {@link Long}, or default value.
   *
   * @param key Key of property.
   * @param defaultValue Default value, if property does not exist.
   * @return Value of property.
   */
  public Long getPropertyLong(final String key, final long defaultValue) {
    final String value = this.getPropertyString(key);
    if (value == null) {
      return defaultValue;
    }

    return Long.valueOf(value);
  }

  /**
   * Get property as {@link Path}.
   *
   * @param key Key of property.
   * @return Value of property, as path.
   */
  @CheckForNull
  public Path getPropertyPath(final String key) {
    final String value = this.getPropertyString(key);
    if (value == null) {
      return null;
    }

    return Path.of(value);
  }

  public boolean hasProperty(final String key) {
    return this.getPropertyString(key) != null;
  }

  /**
   * Get a property value as a {@link List}. Items are separated by {@link
   * MagikToolsProperties#LIST_SEPARATOR}.
   *
   * @param key Key of the property.
   * @return List of values.
   */
  public List<String> getPropertyList(final String key) {
    final String value = this.getPropertyString(key);
    if (value == null || value.isBlank()) {
      return Collections.emptyList();
    }

    final String[] values = value.split(MagikToolsProperties.LIST_SEPARATOR);
    return Arrays.stream(values).map(String::trim).toList();
  }

  public <T> List<T> getPropertyList(
      final String key, @Nullable String separator, final Class<T> type) {
    final String value = this.getPropertyString(key);
    if (value == null || value.isBlank()) {
      return Collections.emptyList();
    }

    if (separator == null) {
      if (type.isPrimitive()) {
        separator = LIST_SEPARATOR;
      } else {
        separator = LIST_SEPARATOR_OBJ;
      }
    }

    Stream<String> valuesStream = Arrays.stream(value.split(separator)).map(String::trim);

    List<?> values;
    String typeName = type.getSimpleName();
    switch (typeName) {
      case "Integer":
        values = valuesStream.map(Integer::valueOf).toList();
        break;
      case "Long":
        values = valuesStream.map(Long::valueOf).toList();
        break;
      case "Double":
        values = valuesStream.map(Double::valueOf).toList();
        break;
      case "Float":
        values = valuesStream.map(Float::valueOf).toList();
        break;
      case "Boolean":
        values = valuesStream.map(Boolean::valueOf).toList();
        break;
      case "Byte":
        values = valuesStream.map(Byte::valueOf).toList();
        break;
      case "Short":
        values = valuesStream.map(Short::valueOf).toList();
        break;
      case "Character":
        values = valuesStream.filter(str -> !str.isBlank()).map(str -> str.charAt(0)).toList();
        break;
      case "String":
        values = valuesStream.toList();
        break;
      default:
        ObjectMapper om = new ObjectMapper();
        values =
            valuesStream
                .map(
                    v -> {
                      try {
                        return om.readValue(v, type);
                      } catch (JsonProcessingException e) {
                        LOGGER.error("Could not read value {} with type {}", v, type, e);
                        return null;
                      }
                    })
                .filter(Objects::nonNull)
                .toList();
    }

    return (List<T>) values;
  }

  /**
   * Merge two sets of properties.
   *
   * <p>In case of duplicate keys, the values of `properties2` win.
   *
   * @param properties1 Properties 1.
   * @param properties2 Properties 2.
   * @return Merged properties.
   */
  public static MagikToolsProperties merge(
      final MagikToolsProperties properties1, final MagikToolsProperties properties2) {
    final MagikToolsProperties result = new MagikToolsProperties();
    result.properties.putAll(properties1.properties);
    result.properties.putAll(properties2.properties);
    return result;
  }
}
