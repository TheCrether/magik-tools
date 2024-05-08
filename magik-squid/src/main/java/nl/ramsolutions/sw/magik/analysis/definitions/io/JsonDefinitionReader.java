package nl.ramsolutions.sw.magik.analysis.definitions.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import nl.ramsolutions.sw.definitions.ModuleDefinition;
import nl.ramsolutions.sw.definitions.ProductDefinition;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.*;
import nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer.*;
import nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer.ExemplarDefinitionDeserializer;
import nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer.SlotDefinitionDeserializer;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON-line TypeKeeper reader. */
public final class JsonDefinitionReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonDefinitionReader.class);
  private static final Logger LOGGER_DURATION =
      LoggerFactory.getLogger(JsonDefinitionReader.class.getName() + "Duration");

  public static final String TYPE_DB_DEFAULT_ALIAS = "$default";
  private static final String TYPE_DB_DEFAULT_PATH =
      "../type_dbs"; // relative to smallworldGis path
  public static final String TYPE_DB_EXT = ".types_db.jsonl";

  private final IDefinitionKeeper definitionKeeper;
  private final List<PathMapping> mappings;
  private final ObjectMapper objectMapper;

  private JsonDefinitionReader(
      final IDefinitionKeeper definitionKeeper, final @Nullable List<PathMapping> mappings) {
    this.definitionKeeper = definitionKeeper;
    this.mappings = mappings;

    this.objectMapper = new ObjectMapper();
    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    objectMapper.registerModule(this.buildObjectMapperModule());
  }

  private SimpleModule buildObjectMapperModule() {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(TypeString.class, new TypeStringDeserializer(mappings));
    module.addDeserializer(
        ExpressionResultString.class, new ExpressionResultStringDeserializer(mappings));
    module.addDeserializer(
        ExemplarDefinition.Sort.class,
        new LowerCaseEnumDeserializer<>(mappings, ExemplarDefinition.Sort.class));
    module.addDeserializer(
        MethodDefinition.Modifier.class,
        new LowerCaseEnumDeserializer<>(mappings, MethodDefinition.Modifier.class));
    module.addDeserializer(
        ProcedureDefinition.Modifier.class,
        new LowerCaseEnumDeserializer<>(mappings, ProcedureDefinition.Modifier.class));
    module.addDeserializer(
        ParameterDefinition.Modifier.class,
        new LowerCaseEnumDeserializer<>(mappings, ParameterDefinition.Modifier.class));
    module.addDeserializer(SlotDefinition.class, new SlotDefinitionDeserializer(mappings));
    module.addDeserializer(
        ParameterDefinition.class, new ParameterDefinitionDeserializer(mappings));
    module.addDeserializer(ProductDefinition.class, new ProductDefinitionDeserializer(mappings));
    module.addDeserializer(ModuleDefinition.class, new ModuleDefinitionDeserializer(mappings));
    module.addDeserializer(PackageDefinition.class, new PackageDefinitionDeserializer(mappings));
    module.addDeserializer(ExemplarDefinition.class, new ExemplarDefinitionDeserializer(mappings));
    module.addDeserializer(MethodDefinition.class, new MethodDefinitionDeserializer(mappings));
    module.addDeserializer(
        ConditionDefinition.class, new ConditionDefinitionDeserializer(mappings));
    module.addDeserializer(
        BinaryOperatorDefinition.class, new BinaryOperatorDefinitionDeserializer(mappings));
    module.addDeserializer(
        ProcedureDefinition.class, new ProcedureDefinitionDeserializer(mappings));
    module.addDeserializer(GlobalDefinition.class, new GlobalDefinitionDeserializer(mappings));

    return module;
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
    final long start = System.nanoTime();
    reader.run(path);
    LOGGER_DURATION.trace(
        "Duration: {} readTypes, type db: {}", (System.nanoTime() - start) / 1000000000.0, path);
    BaseDeserializer.clearParsedFiles(); // free memory
  }

  /**
   * parses a path for a type db and will replace {@value
   * JsonDefinitionReader#TYPE_DB_DEFAULT_ALIAS} with the default type db path
   *
   * @param gisPath the gisPath if the default alias gets replaced
   * @param pathStr the path that should get parsed
   * @return the parsed Path or null if the file can't be found
   */
  @Nullable
  public static Path parseTypeDBPath(String gisPath, String pathStr) {
    Path path = Path.of(pathStr);
    if (pathStr.equals(TYPE_DB_DEFAULT_ALIAS)) {
      path = generateDefaultTypeDBPath(gisPath);
    }

    if (!Files.exists(path)) {
      return null;
    }

    return path;
  }

  /**
   * Generate the default type db path
   *
   * @param gisPath the Smallworld GIS path to use as the basis
   * @return the path
   */
  public static Path generateDefaultTypeDBPath(String gisPath) {
    return Paths.get(gisPath, TYPE_DB_DEFAULT_PATH);
  }

  public void run(Path path) {
    LOGGER.info("Reading type database from path: {}", path);

    final File file = path.toFile();
    int lineNo = 1;
    try (FileReader fileReader = new FileReader(file, StandardCharsets.ISO_8859_1);
        BufferedReader bufferedReader = new BufferedReader(fileReader)) {
      String line = bufferedReader.readLine();
      while (line != null) {
        if (lineNo % 10000 == 0) {
          LOGGER.debug("On line {} of {}", lineNo, path);
        }
        this.processLineSafe(lineNo, line);

        ++lineNo;
        line = bufferedReader.readLine();
      }
    } catch (final IOException exception) {
      LOGGER.error("JSON Error reading line no: {}", lineNo);
      throw new IllegalStateException(exception);
    }
  }

  private void processLineSafe(final int lineNo, final String line) {
    try {
      this.processLine(line);
    } catch (final Exception exception) {
      LOGGER.error("Error parsing line {}, line data: {}", lineNo, line);
      LOGGER.error(exception.getMessage(), exception);
    }
  }

  private void processLine(String line) throws Exception {
    if (line.trim().startsWith("//")) {
      // Ignore comments.
      return;
    }

    final JsonNode node = objectMapper.readTree(line);
    final JsonNode instructionObj = node.get(Instruction.FIELD_NAME);
    final Instruction instruction = Instruction.fromValue(instructionObj.intValue());

    switch (instruction) {
      case PRODUCT:
        this.handleProduct(node);
        break;

      case MODULE:
        this.handleModule(node);
        break;

      case PACKAGE:
        this.handlePackage(node);
        break;

      case TYPE:
        this.handleType(node);
        break;

      case METHOD:
        this.handleMethod(node);
        break;

      case PROCEDURE:
        this.handleProcedure(node);
        break;

      case CONDITION:
        this.handleCondition(node);
        break;

      case BINARY_OPERATOR:
        this.handleBinaryOperator(node);
        break;

      case GLOBAL:
        this.handleGlobal(node);
        break;

      default:
        throw new IllegalStateException(
            "Unexpected instruction: " + instruction + "\nline: " + line);
    }
  }

  private void handleProduct(final JsonNode node) throws IOException {
    ProductDefinition definition = objectMapper.reader().readValue(node, ProductDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleModule(final JsonNode node) throws IOException {
    ModuleDefinition definition = objectMapper.reader().readValue(node, ModuleDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handlePackage(final JsonNode node) throws IOException {
    PackageDefinition definition = objectMapper.reader().readValue(node, PackageDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleType(final JsonNode node) throws IOException {
    ExemplarDefinition definition = objectMapper.reader().readValue(node, ExemplarDefinition.class);

    // We are allowed to overwrite definitions which have no location, as these will most likely
    // be the default definitions from DefaultDefinitionsAdder.
    final TypeString typeString = definition.getTypeString();
    this.definitionKeeper.getExemplarDefinitions(typeString).stream()
        .filter(def -> def.getLocation() == null)
        .forEach(this.definitionKeeper::remove);

    this.definitionKeeper.add(definition);
  }

  private void handleMethod(final JsonNode node) throws IOException {
    MethodDefinition definition = objectMapper.reader().readValue(node, MethodDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleCondition(final JsonNode node) throws IOException {
    ConditionDefinition definition =
        objectMapper.reader().readValue(node, ConditionDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleBinaryOperator(final JsonNode node) throws IOException {
    BinaryOperatorDefinition definition =
        objectMapper.reader().readValue(node, BinaryOperatorDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleProcedure(final JsonNode node) throws IOException {
    ProcedureDefinition definition =
        objectMapper.reader().readValue(node, ProcedureDefinition.class);
    this.definitionKeeper.add(definition);
  }

  private void handleGlobal(final JsonNode node) throws IOException {
    GlobalDefinition definition = objectMapper.reader().readValue(node, GlobalDefinition.class);
    this.definitionKeeper.add(definition);
  }
}
