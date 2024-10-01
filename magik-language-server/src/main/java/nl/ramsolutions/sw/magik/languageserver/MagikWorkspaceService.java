package nl.ramsolutions.sw.magik.languageserver;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import nl.ramsolutions.sw.IgnoreHandler;
import nl.ramsolutions.sw.MagikToolsProperties;
import nl.ramsolutions.sw.magik.PathMapping;
import nl.ramsolutions.sw.magik.analysis.definitions.IDefinitionKeeper;
import nl.ramsolutions.sw.magik.analysis.definitions.io.JsonDefinitionReader;
import nl.ramsolutions.sw.magik.analysis.definitions.io.deserializer.BaseDeserializer;
import nl.ramsolutions.sw.magik.analysis.indexer.MagikIndexer;
import nl.ramsolutions.sw.magik.analysis.indexer.ModuleIndexer;
import nl.ramsolutions.sw.magik.analysis.indexer.ProductIndexer;
import nl.ramsolutions.sw.magik.analysis.typing.ClassInfoDefinitionReader;
import nl.ramsolutions.sw.magik.languageserver.munit.MUnitTestItem;
import nl.ramsolutions.sw.magik.languageserver.munit.MUnitTestItemProvider;
import nl.ramsolutions.sw.magik.languageserver.symbol.SymbolProvider;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Magik WorkspaceService. */
public class MagikWorkspaceService implements WorkspaceService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MagikWorkspaceService.class);
  private static final Logger LOGGER_DURATION =
      LoggerFactory.getLogger(MagikWorkspaceService.class.getName() + "Duration");

  private final MagikLanguageServer languageServer;
  private final MagikToolsProperties languageServerProperties;
  private final IDefinitionKeeper definitionKeeper;
  private final IgnoreHandler ignoreHandler;
  private final ProductIndexer productIndexer;
  private final ModuleIndexer moduleIndexer;
  private final MagikIndexer magikIndexer;
  private final SymbolProvider symbolProvider;
  private final MUnitTestItemProvider testItemProvider;

  /**
   * Constructor.
   *
   * @param languageServer Owner language server.
   * @param definitionKeeper {@link IDefinitionKeeper} used for definition storage.
   * @throws IOException If an error occurs.
   */
  public MagikWorkspaceService(
      final MagikLanguageServer languageServer,
      final MagikToolsProperties languageServerProperties,
      final IDefinitionKeeper definitionKeeper) {
    this.languageServer = languageServer;
    this.languageServerProperties = languageServerProperties;
    this.definitionKeeper = definitionKeeper;

    this.ignoreHandler = new IgnoreHandler();
    this.productIndexer = new ProductIndexer(this.definitionKeeper, this.ignoreHandler);
    this.moduleIndexer = new ModuleIndexer(this.definitionKeeper, this.ignoreHandler);
    this.magikIndexer =
        new MagikIndexer(this.definitionKeeper, this.languageServerProperties, this.ignoreHandler);
    this.symbolProvider = new SymbolProvider(this.definitionKeeper, this.languageServerProperties);
    this.testItemProvider =
        new MUnitTestItemProvider(this.definitionKeeper, this.languageServerProperties);

    BaseDeserializer.setProperties(this.languageServerProperties);
  }

  /**
   * Set capabilities.
   *
   * @param capabilities Server capabilities to set.
   */
  public void setCapabilities(final ServerCapabilities capabilities) {
    this.symbolProvider.setCapabilities(capabilities);
    this.testItemProvider.setCapabilities(capabilities);
  }

  @Override
  public void didChangeConfiguration(final DidChangeConfigurationParams params) {
    LOGGER.trace("didChangeConfiguration");

    final MagikLanguageServerSettings oldLspSettings =
        new MagikLanguageServerSettings(this.languageServerProperties);
    final List<String> oldTypeDBPaths =
        new ArrayList<>(oldLspSettings.getTypingTypeDatabasePaths());
    final List<String> oldProductDirs = new ArrayList<>(oldLspSettings.getProductDirs());
    final List<PathMapping> oldPathMappings = new ArrayList<>(oldLspSettings.getPathMappings());

    final JsonObject settings = (JsonObject) params.getSettings();
    final Properties props = JsonObjectPropertiesConverter.convert(settings);
    LOGGER.debug("New properties: {}", props);
    this.languageServerProperties.reset();
    this.languageServerProperties.putAll(props);
    //    BaseDeserializer.setProperties(this.languageServerProperties); // TODO check if this is
    // not needed because object reference?

    // TODO change how typing db gets accessed
    final MagikLanguageServerSettings lspSettings =
        new MagikLanguageServerSettings(this.languageServerProperties);
    if (collectionsDiffers(oldTypeDBPaths, lspSettings.getTypingTypeDatabasePaths())
        || collectionsDiffers(oldProductDirs, lspSettings.getProductDirs())
        || collectionsDiffers(oldPathMappings, lspSettings.getPathMappings())) {
      this.definitionKeeper.clear();

      this.runIndexersInBackground();
    }
  }

  private <T> boolean collectionsDiffers(Collection<T> collection1, Collection<T> collection2) {
    if (collection1.size() != collection2.size()) {
      return true;
    }

    int sizeBefore = collection1.size();
    collection1.retainAll(collection2);
    return sizeBefore != collection1.size();
  }

  private void readProductsClassInfos(final List<String> productDirs) {
    LOGGER.trace("Reading docs from product dirs: {}", productDirs);

    productDirs.forEach(
        pathStr -> {
          final Path path = Path.of(pathStr);
          if (!Files.exists(path)) {
            LOGGER.warn("Path to product dir does not exist: {}", pathStr);
            return;
          }

          try {
            ClassInfoDefinitionReader.readProductDirectory(path, this.definitionKeeper);
          } catch (final IOException exception) {
            LOGGER.error(exception.getMessage(), exception);
          }
        });
  }

  /**
   * Read the type databases from the given path.
   *
   * @param typeDbPaths Paths to type databases.
   */
  public void readTypesDbs(final List<String> typeDbPaths) {
    final long start = System.nanoTime();

    LOGGER.info("Reading type databases from: {}", typeDbPaths);

    final MagikLanguageServerSettings lspSettings =
        new MagikLanguageServerSettings(this.languageServerProperties);
    final String smallworldGis =
        Objects.requireNonNull(lspSettings.getSmallworldGis(), "smallworldGis not defined");

    typeDbPaths.stream()
        .map(
            pathStr -> {
              Path path = JsonDefinitionReader.parseTypeDBPath(smallworldGis, pathStr);
              if (path == null) {
                LOGGER.warn("Path to types database does not exist: {}", pathStr);
                return null;
              }
              return path;
            })
        .filter(Objects::nonNull)
        .flatMap(
            path -> {
              if (Files.isDirectory(path)) {
                File dir = path.toFile();
                File[] files =
                    dir.listFiles((dir1, name) -> name.endsWith(JsonDefinitionReader.TYPE_DB_EXT));
                if (files != null) {
                  return Arrays.stream(files).map(File::toPath);
                }
                return Stream.empty();
              }
              return Stream.of(path);
            })
        .forEach(
            path -> {
              try {
                JsonDefinitionReader.readTypes(
                    path, this.definitionKeeper, lspSettings.getPathMappings());
              } catch (final IOException exception) {
                LOGGER.error(exception.getMessage(), exception);
              }
            });

    LOGGER.info(
        "Finished reading type databases, Duration: {}",
        (System.nanoTime() - start) / 1000000000.0);
  }

  @Override
  public void didChangeWatchedFiles(final DidChangeWatchedFilesParams params) {
    params.getChanges().stream()
        .forEach(
            fileEvent -> {
              LOGGER.debug(
                  "File event: uri: {}, type: {}", fileEvent.getUri(), fileEvent.getType());

              final FileChangeType fileChangeType = fileEvent.getType();
              final URI uri = URI.create(fileEvent.getUri());
              final Path path = Path.of(uri);
              if (fileChangeType != FileChangeType.Deleted && !Files.exists(path)) {
                // Ensure file still exists. Files such as `.git/index.lock` are often already
                // deleted before it reaches this method.
                return;
              }

              final nl.ramsolutions.sw.magik.FileEvent.FileChangeType magikFileChangeType =
                  Lsp4jConversion.fileChangeTypeFromLsp4j(fileChangeType);
              final nl.ramsolutions.sw.magik.FileEvent magikFileEvent =
                  new nl.ramsolutions.sw.magik.FileEvent(uri, magikFileChangeType);
              try {
                this.productIndexer.handleFileEvent(magikFileEvent);
                this.moduleIndexer.handleFileEvent(magikFileEvent);
                this.magikIndexer.handleFileEvent(magikFileEvent);
              } catch (final IOException exception) {
                LOGGER.error(exception.getMessage(), exception);
              }
            });
  }

  @Override
  public CompletableFuture<
          Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>>
      symbol(WorkspaceSymbolParams params) {
    final String query = params.getQuery();
    LOGGER.trace("symbol, query: {}", query);

    return CompletableFuture.supplyAsync(
        () -> {
          final List<WorkspaceSymbol> queryResults = this.symbolProvider.getSymbols(query);
          LOGGER.debug("Symbols found for: '{}', count: {}", query, queryResults.size());
          return Either.forRight(queryResults);
        });
  }

  // region: Additional commands.

  /**
   * Re-index all magik files.
   *
   * @return CompletableFuture.
   */
  @JsonRequest(value = "custom/reIndex")
  public CompletableFuture<Void> reIndex() {
    return CompletableFuture.runAsync(
        () -> {
          this.definitionKeeper.clear();

          this.runIndexersInBackground();
        });
  }

  /**
   * Get test items.
   *
   * @return Test items.
   */
  @JsonRequest(value = "custom/munit/getTestItems")
  public CompletableFuture<Collection<MUnitTestItem>> getTestItems() {
    // TODO: Rewrite this to generic queries on types. Such as:
    //       - Get type by name
    //         - doc
    //         - location
    //         - parents
    //         - children
    //         - ...
    //         - methods?
    //       - Get methods from type name
    //       - Get method by name
    //       In fact, maybe we can use LSP typeHierarchy support?
    LOGGER.trace("munit/getTestItems");

    return CompletableFuture.supplyAsync(this.testItemProvider::getTestItems);
  }

  // endregion

  private void runIndexers() {
    final long start = System.nanoTime();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Run indexers");
    }

    // run indexing for workspace folders first without any other information for faster
    // hover/completion etc on start
    for (final MagikWorkspaceFolder workspaceFolder : this.languageServer.getWorkspaceFolders()) {
      try {
        workspaceFolder.onInit();
      } catch (final IOException exception) {
        LOGGER.error(
            "Caught error when initializing workspacefolder: " + workspaceFolder, exception);
      }
    }

    // Read types dbs.
    final MagikLanguageServerSettings settings =
        new MagikLanguageServerSettings(this.languageServerProperties);
    final List<String> typesDbPaths = settings.getTypingTypeDatabasePaths();
    this.readTypesDbs(typesDbPaths);

    // Read class_infos from product dirs.
    final List<String> productDirs = settings.getProductDirs();
    this.readProductsClassInfos(productDirs);

    if (LOGGER_DURATION.isTraceEnabled()) {
      LOGGER_DURATION.trace("Duration: {} runIndexers", (System.nanoTime() - start) / 1000000000.0);
    }

    // Update workspace folders.
    for (final MagikWorkspaceFolder workspaceFolder : this.languageServer.getWorkspaceFolders()) {
      try {
        workspaceFolder.onInit();
      } catch (final IOException exception) {
        LOGGER.error(
            "Caught error when initializing workspacefolder: " + workspaceFolder, exception);
      }
    }
  }

  @SuppressWarnings("IllegalCatch")
  private void runIndexersInBackground() {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Run background indexer");
    }

    final LanguageClient languageClient = this.languageServer.getLanguageClient();
    final WorkDoneProgressCreateParams params = new WorkDoneProgressCreateParams();
    final String token = UUID.randomUUID().toString();
    params.setToken(token);
    languageClient.createProgress(params);

    CompletableFuture.runAsync(
        () -> {
          LOGGER.trace("Start indexing workspace");
          final ProgressParams progressParams = new ProgressParams();
          progressParams.setToken(token);

          final WorkDoneProgressBegin begin = new WorkDoneProgressBegin();
          begin.setTitle("Indexing workspace");
          progressParams.setValue(Either.forLeft(begin));
          languageClient.notifyProgress(progressParams);

          try {
            this.runIndexers();
          } catch (final Exception exception) {
            LOGGER.error(exception.getMessage(), exception);
          }

          final WorkDoneProgressEnd end = new WorkDoneProgressEnd();
          end.setMessage("Done indexing workspace");
          progressParams.setValue(Either.forLeft(end));
          languageClient.notifyProgress(progressParams);
          LOGGER.trace("Done indexing workspace in background");
        });
  }

  /** Handle shutdown. */
  public void shutdown() {
    for (final MagikWorkspaceFolder workspaceFolder : this.languageServer.getWorkspaceFolders()) {
      try {
        workspaceFolder.onShutdown();
      } catch (final IOException exception) {
        LOGGER.error(
            "Caught error when shutting down workspacefolder: " + workspaceFolder, exception);
      }
    }
  }
}
