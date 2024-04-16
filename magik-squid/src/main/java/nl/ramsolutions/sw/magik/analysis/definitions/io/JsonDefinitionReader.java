package nl.ramsolutions.sw.magik.analysis.definitions.io;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import nl.ramsolutions.sw.definitions.ModuleDefinition;
import nl.ramsolutions.sw.definitions.ProductDefinition;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.BinaryOperatorDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.ConditionDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.ExemplarDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.GlobalDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.IDefinitionKeeper;
import nl.ramsolutions.sw.magik.analysis.definitions.MethodDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.PackageDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.ParameterDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.ProcedureDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer.*;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON-line TypeKeeper reader. */
public final class JsonDefinitionReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonDefinitionReader.class);
  private final Gson gson;

  private final IDefinitionKeeper definitionKeeper;
  private final List<PathMapping> mappings;

  private JsonDefinitionReader(
      final IDefinitionKeeper definitionKeeper, final @Nullable List<PathMapping> mappings) {
    this.definitionKeeper = definitionKeeper;
    this.mappings = mappings;
    this.gson = this.buildGson();
  }

  private void run(final Path path) throws IOException {
    LOGGER.debug("Reading type database from path: {}", path);

    final File file = path.toFile();
    int lineNo = 1;
    try (FileReader fileReader = new FileReader(file, StandardCharsets.ISO_8859_1);
        BufferedReader bufferedReader = new BufferedReader(fileReader)) {
      String line = bufferedReader.readLine();
      while (line != null) {
        this.processLineSafe(lineNo, line);

        ++lineNo;
        line = bufferedReader.readLine();
      }
    } catch (final JsonParseException exception) {
      LOGGER.error("JSON Error reading line no: {}", lineNo);
      throw new IllegalStateException(exception);
    }
  }

  @SuppressWarnings("checkstyle:IllegalCatch")
  private void processLineSafe(final int lineNo, final String line) {
    try {
      this.processLine(line);
    } catch (final RuntimeException exception) {
      LOGGER.error("Error parsing line {}, line data: {}", lineNo, line);
      LOGGER.error(exception.getMessage(), exception);
    }
  }

  private void processLine(final String line) {
    if (line.trim().startsWith("//")) {
      // Ignore comments.
      return;
    }

    final JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
    final String instructionStr = obj.get(Instruction.INSTRUCTION.getValue()).getAsString();
    final Instruction instruction = Instruction.fromValue(instructionStr);
    switch (instruction) {
      case PRODUCT:
        this.handleProduct(obj);
        break;

      case MODULE:
        this.handleModule(obj);
        break;

      case PACKAGE:
        this.handlePackage(obj);
        break;

      case TYPE:
        this.handleType(obj);
        break;

      case METHOD:
        this.handleMethod(obj);
        break;

      case PROCEDURE:
        this.handleProcedure(obj);
        break;

      case CONDITION:
        this.handleCondition(obj);
        break;

      case BINARY_OPERATOR:
        this.handleBinaryOperator(obj);
        break;

      case GLOBAL:
        this.handleGlobal(obj);
        break;

      default:
        break;
    }
  }

  private Gson buildGson() {
    return new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(TypeString.class, new TypeStringDeserializer(this.mappings))
        .registerTypeAdapter(
            ExpressionResultString.class, new ExpressionResultStringDeserializer(this.mappings))
        .registerTypeAdapter(
            ExemplarDefinition.Sort.class,
            new LowerCaseEnumDeserializer<ExemplarDefinition.Sort>(this.mappings))
        .registerTypeAdapter(
            MethodDefinition.Modifier.class,
            new LowerCaseEnumDeserializer<MethodDefinition.Modifier>(this.mappings))
        .registerTypeAdapter(
            ProcedureDefinition.Modifier.class,
            new LowerCaseEnumDeserializer<ProcedureDefinition.Modifier>(this.mappings))
        .registerTypeAdapter(
            ParameterDefinition.Modifier.class,
            new LowerCaseEnumDeserializer<ParameterDefinition.Modifier>(this.mappings))
        .registerTypeAdapter(
            ParameterDefinition.class, new ParameterDefinitionDeserializer(this.mappings))
        .registerTypeAdapter(
            ProductDefinition.class, new ProductDefinitionDeserializer(this.mappings))
        .registerTypeAdapter(
            ModuleDefinition.class, new ModuleDefinitionDeserializer(this.mappings))
        .registerTypeAdapter(
            PackageDefinition.class, new PackageDefinitionDeserializer(this.mappings))
        .registerTypeAdapter(
            ExemplarDefinition.class, new ExemplarDefinitionDeserializer(this.mappings))
        .registerTypeAdapter(
            MethodDefinition.class, new MethodDefinitionDeserializer(this.mappings))
        .registerTypeAdapter(
            ConditionDefinition.class, new ConditionDefinitionDeserializer(this.mappings))
        .registerTypeAdapter(
            BinaryOperatorDefinition.class, new BinaryOperatorDefinitionDeserializer(this.mappings))
        .registerTypeAdapter(
            ProcedureDefinition.class, new ProcedureDefinitionDeserializer(this.mappings))
        .registerTypeAdapter(
            GlobalDefinition.class, new GlobalDefinitionDeserializer(this.mappings))
        .create();
  }

  private void handleProduct(final JsonObject instruction) {
    final ProductDefinition definition = gson.fromJson(instruction, ProductDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleModule(final JsonObject instruction) {
    final ModuleDefinition definition = gson.fromJson(instruction, ModuleDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handlePackage(final JsonObject instruction) {
    final PackageDefinition definition = gson.fromJson(instruction, PackageDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleType(final JsonObject instruction) {
    final ExemplarDefinition definition = gson.fromJson(instruction, ExemplarDefinition.class);

    // We are allowed to overwrite definitions which have no location, as these will most likely
    // be the default definitions from DefaultDefinitionsAdder.
    final TypeString typeString = definition.getTypeString();
    this.definitionKeeper.getExemplarDefinitions(typeString).stream()
        .filter(def -> def.getLocation() == null)
        .forEach(this.definitionKeeper::remove);

    this.definitionKeeper.add(definition);
  }

  private void handleMethod(final JsonObject instruction) {
    final MethodDefinition definition = gson.fromJson(instruction, MethodDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleCondition(final JsonObject instruction) {
    final ConditionDefinition definition = gson.fromJson(instruction, ConditionDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleBinaryOperator(final JsonObject instruction) {
    final BinaryOperatorDefinition definition =
        gson.fromJson(instruction, BinaryOperatorDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleProcedure(final JsonObject instruction) {
    final ProcedureDefinition definition = gson.fromJson(instruction, ProcedureDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleGlobal(final JsonObject instruction) {
    final GlobalDefinition definition = gson.fromJson(instruction, GlobalDefinition.class);
    this.definitionKeeper.add(definition);
  }

  /**
   * Read types from a JSON-line file.
   *
   * @param path Path to JSON-line file.
   * @param definitionKeeper {@link IDefinitionKeeper} to fill.
   * @throws IOException -
   */
  public static void readTypes(
      final Path path,
      final IDefinitionKeeper definitionKeeper,
      final @Nullable List<PathMapping> mappings)
      throws IOException {
    final JsonDefinitionReader reader = new JsonDefinitionReader(definitionKeeper, mappings);
    BaseDeserializer.clearParsedFiles();
    reader.run(path);
  }
}
