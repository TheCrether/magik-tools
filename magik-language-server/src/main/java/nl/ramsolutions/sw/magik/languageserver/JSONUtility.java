package nl.ramsolutions.sw.magik.languageserver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.HashMap;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;

/** JSONUtility */
public class JSONUtility {

  /**
   * Converts given JSON objects to given Model objects.
   *
   * @throws IllegalArgumentException if clazz is null
   */
  public static <T> T toModel(Object object, Class<T> clazz) {
    return toModel(new Gson(), object, clazz);
  }

  /**
   * Converts given JSON objects to given Model objects with the customized TypeAdapters from lsp4j.
   *
   * @throws IllegalArgumentException if clazz is null
   */
  public static <T> T toLsp4jModel(Object object, Class<T> clazz) {
    return toModel(new MessageJsonHandler(new HashMap<>()).getGson(), object, clazz);
  }

  @Nullable
  private static <T> T toModel(Gson gson, @Nullable Object object, @Nullable Class<T> clazz) {
    if (object == null) {
      return null;
    }
    if (clazz == null) {
      throw new IllegalArgumentException("Class can not be null");
    }
    if (object instanceof JsonElement json) {
      return gson.fromJson(json, clazz);
    }
    if (clazz.isInstance(object)) {
      return clazz.cast(object);
    }
    if (object instanceof String json) {
      return gson.fromJson(json, clazz);
    }
    return null;
  }
}
