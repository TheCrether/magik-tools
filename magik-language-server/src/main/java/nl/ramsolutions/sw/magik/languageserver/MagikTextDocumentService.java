package nl.ramsolutions.sw.magik.languageserver;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import nl.ramsolutions.sw.ConfigurationReader;
import nl.ramsolutions.sw.MagikToolsProperties;
import nl.ramsolutions.sw.OpenedFile;
import nl.ramsolutions.sw.magik.LintPropertiesFile;
import nl.ramsolutions.sw.magik.MagikTypedFile;
import nl.ramsolutions.sw.magik.analysis.definitions.IDefinitionKeeper;
import nl.ramsolutions.sw.magik.checks.CheckList;
import nl.ramsolutions.sw.magik.checks.MagikCheck;
import nl.ramsolutions.sw.magik.checks.MagikChecksConfiguration;
import nl.ramsolutions.sw.magik.languageserver.callhierarchy.CallHierarchyProvider;
import nl.ramsolutions.sw.magik.languageserver.codeactions.CodeActionProvider;
import nl.ramsolutions.sw.magik.languageserver.completion.CompletionProvider;
import nl.ramsolutions.sw.magik.languageserver.definitions.DefinitionsProvider;
import nl.ramsolutions.sw.magik.languageserver.diagnostics.DiagnosticsProvider;
import nl.ramsolutions.sw.magik.languageserver.documentsymbols.DocumentSymbolProvider;
import nl.ramsolutions.sw.magik.languageserver.folding.FoldingRangeProvider;
import nl.ramsolutions.sw.magik.languageserver.formatting.FormattingProvider;
import nl.ramsolutions.sw.magik.languageserver.hover.HoverProvider;
import nl.ramsolutions.sw.magik.languageserver.implementation.ImplementationProvider;
import nl.ramsolutions.sw.magik.languageserver.inlayhint.InlayHintProvider;
import nl.ramsolutions.sw.magik.languageserver.jsonrpc.LintIgnoreParams;
import nl.ramsolutions.sw.magik.languageserver.references.ReferencesProvider;
import nl.ramsolutions.sw.magik.languageserver.rename.RenameProvider;
import nl.ramsolutions.sw.magik.languageserver.selectionrange.SelectionRangeProvider;
import nl.ramsolutions.sw.magik.languageserver.semantictokens.SemanticTokenProvider;
import nl.ramsolutions.sw.magik.languageserver.signaturehelp.SignatureHelpProvider;
import nl.ramsolutions.sw.magik.languageserver.typehierarchy.TypeHierarchyProvider;
import nl.ramsolutions.sw.moduledef.ModuleDefFile;
import nl.ramsolutions.sw.productdef.ProductDefFile;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.check.RuleProperty;

/** Magik TextDocumentService. */
public class MagikTextDocumentService implements TextDocumentService {

  // TODO: Better separation of Lsp4J and magik-tools regarding Range/Position.

  private static final Logger LOGGER = LoggerFactory.getLogger(MagikTextDocumentService.class);
  private static final Logger LOGGER_DURATION =
      LoggerFactory.getLogger(MagikTextDocumentService.class.getName() + "Duration");

  private final MagikLanguageServer languageServer;
  private final MagikToolsProperties properties;
  private final IDefinitionKeeper definitionKeeper;
  private final DiagnosticsProvider diagnosticsProvider;
  private final HoverProvider hoverProvider;
  private final ImplementationProvider implementationProvider;
  private final SignatureHelpProvider signatureHelpProvider;
  private final DefinitionsProvider definitionsProvider;
  private final ReferencesProvider referencesProvider;
  private final CompletionProvider completionProvider;
  private final FormattingProvider formattingProvider;
  private final FoldingRangeProvider foldingRangeProvider;
  private final SemanticTokenProvider semanticTokenProver;
  private final RenameProvider renameProvider;
  private final DocumentSymbolProvider documentSymbolProvider;
  private final TypeHierarchyProvider typeHierarchyProvider;
  private final InlayHintProvider inlayHintProvider;
  private final CodeActionProvider codeActionProvider;
  private final SelectionRangeProvider selectionRangeProvider;
  private final CallHierarchyProvider callHierarchyProvider;
  private final Map<TextDocumentIdentifier, OpenedFile> openedFiles = new HashMap<>();

  /**
   * Constructor.
   *
   * @param languageServer Owning language server.
   * @param definitionKeeper IDefinitionKeeper to use.
   */
  public MagikTextDocumentService(
      final MagikLanguageServer languageServer,
      final MagikToolsProperties properties,
      final IDefinitionKeeper definitionKeeper) {
    this.languageServer = languageServer;
    this.properties = properties;
    this.definitionKeeper = definitionKeeper;

    this.diagnosticsProvider = new DiagnosticsProvider(this.properties);
    this.hoverProvider = new HoverProvider(this.properties);
    this.implementationProvider = new ImplementationProvider(this.properties);
    this.signatureHelpProvider = new SignatureHelpProvider();
    this.definitionsProvider = new DefinitionsProvider(this.properties);
    this.referencesProvider = new ReferencesProvider(this.properties);
    this.completionProvider = new CompletionProvider();
    this.formattingProvider = new FormattingProvider();
    this.foldingRangeProvider = new FoldingRangeProvider();
    this.semanticTokenProver = new SemanticTokenProvider();
    this.renameProvider = new RenameProvider();
    this.documentSymbolProvider = new DocumentSymbolProvider();
    this.typeHierarchyProvider = new TypeHierarchyProvider(this.definitionKeeper, this.properties);
    this.inlayHintProvider = new InlayHintProvider(this.properties);
    this.codeActionProvider = new CodeActionProvider(this.properties);
    this.selectionRangeProvider = new SelectionRangeProvider();
    this.callHierarchyProvider = new CallHierarchyProvider(this.definitionKeeper, this.properties);
  }

  /**
   * Set capabilities.
   *
   * @param capabilities Server capabilities to set.
   */
  public void setCapabilities(final ServerCapabilities capabilities) {
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

    this.diagnosticsProvider.setCapabilities(capabilities);
    this.hoverProvider.setCapabilities(capabilities);
    this.implementationProvider.setCapabilities(capabilities);
    this.signatureHelpProvider.setCapabilities(capabilities);
    this.definitionsProvider.setCapabilities(capabilities);
    this.referencesProvider.setCapabilities(capabilities);
    this.completionProvider.setCapabilities(capabilities);
    this.formattingProvider.setCapabilities(capabilities);
    this.foldingRangeProvider.setCapabilities(capabilities);
    this.semanticTokenProver.setCapabilities(capabilities);
    this.renameProvider.setCapabilities(capabilities);
    this.documentSymbolProvider.setCapabilities(capabilities);
    this.typeHierarchyProvider.setCapabilities(capabilities);
    this.inlayHintProvider.setCapabilities(capabilities);
    this.codeActionProvider.setCapabilities(capabilities);
    this.selectionRangeProvider.setCapabilities(capabilities);
    this.callHierarchyProvider.setCapabilities(capabilities);
  }

  @Override
  public void didOpen(final DidOpenTextDocumentParams params) {
    final long start = System.nanoTime();

    final TextDocumentItem textDocument = params.getTextDocument();
    LOGGER.debug("didOpen, uri: {}", textDocument.getUri());

    // Read relevant properties.
    final String uriStr = textDocument.getUri();
    final URI uri = URI.create(uriStr);
    final MagikToolsProperties fileProperties;
    try {
      fileProperties = ConfigurationReader.readProperties(uri, properties);
    } catch (final IOException exception) {
      throw new IllegalStateException(exception);
    }

    // Store file contents.
    final TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(uriStr);
    final String text = textDocument.getText();
    final OpenedFile openedFile;
    switch (textDocument.getLanguageId()) {
      case "productdef":
        {
          openedFile = new ProductDefFile(uri, text, this.definitionKeeper, null);
          break;
        }

      case "moduledef":
        {
          openedFile = new ModuleDefFile(uri, text, this.definitionKeeper, null);
          break;
        }

      case "magik":
        {
          final MagikTypedFile magikFile =
              new MagikTypedFile(fileProperties, uri, text, this.definitionKeeper);
          openedFile = magikFile;

          // Publish diagnostics to client.
          this.publishDiagnostics(magikFile);
          break;
        }
      case "properties":
        {
          // TODO support for all settings
          openedFile = new LintPropertiesFile(uri, text);
          break;
        }

      default:
        throw new UnsupportedOperationException();
    }

    this.openedFiles.put(textDocumentIdentifier, openedFile);
    if (LOGGER_DURATION.isTraceEnabled()) {
      LOGGER_DURATION.trace(
          "Duration: {} didOpen, uri: {}",
          String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
          textDocument.getUri());
    }
  }

  public void reopenAllFiles() {
    Map<TextDocumentIdentifier, OpenedFile> openFilesCopy = new HashMap<>(this.openedFiles);

    for (Map.Entry<TextDocumentIdentifier, OpenedFile> openFile : openFilesCopy.entrySet()) {
      if (openFile.getValue() instanceof ProductDefFile) {}

      this.didOpen(
          new DidOpenTextDocumentParams(
              new TextDocumentItem(
                  openFile.getKey().getUri(),
                  openFile.getValue().getLanguageId(),
                  1,
                  openFile.getValue().getSource())));
    }
  }

  @Override
  public void didChange(final DidChangeTextDocumentParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocumentIdentifier = params.getTextDocument();
    LOGGER.debug("didChange, uri: {}}", textDocumentIdentifier.getUri());

    // Read relevant properties.
    final String uriStr = textDocumentIdentifier.getUri();
    final URI uri = URI.create(uriStr);
    final MagikToolsProperties fileProperties;
    try {
      fileProperties = ConfigurationReader.readProperties(uri, properties);
    } catch (final IOException exception) {
      throw new IllegalStateException(exception);
    }

    // Update file contents.
    final List<TextDocumentContentChangeEvent> contentChangeEvents = params.getContentChanges();
    final TextDocumentContentChangeEvent contentChangeEvent = contentChangeEvents.get(0);
    final String text = contentChangeEvent.getText();

    // Find original TextDocumentIdentifier.
    final TextDocumentIdentifier realTextDocumentIdentifier = new TextDocumentIdentifier(uriStr);
    final OpenedFile existingOpenedFile = this.openedFiles.get(realTextDocumentIdentifier);
    if (existingOpenedFile == null) {
      // Race condition?
      return;
    }

    final String languageId = existingOpenedFile.getLanguageId();
    final OpenedFile openedFile;
    switch (languageId) {
      case "productdef":
        {
          openedFile = new ProductDefFile(uri, text, this.definitionKeeper, null);
          break;
        }

      case "moduledef":
        {
          openedFile = new ModuleDefFile(uri, text, this.definitionKeeper, null);
          break;
        }

      case "magik":
        {
          final MagikTypedFile magikFile =
              new MagikTypedFile(fileProperties, uri, text, this.definitionKeeper);
          openedFile = magikFile;

          // Publish diagnostics to client.
          this.publishDiagnostics(magikFile);
          break;
        }

      case "properties":
        {
          openedFile = new LintPropertiesFile(uri, text);
          break;
        }

      default:
        throw new UnsupportedOperationException();
    }

    this.openedFiles.put(realTextDocumentIdentifier, openedFile);
    if (LOGGER_DURATION.isTraceEnabled()) {
      LOGGER_DURATION.trace(
          "Duration: {} didChange, uri: {}",
          String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
          textDocumentIdentifier.getUri());
    }
  }

  @Override
  public void didClose(final DidCloseTextDocumentParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocumentIdentifier = params.getTextDocument();
    LOGGER.debug("didClose, uri: {}", textDocumentIdentifier.getUri());

    this.openedFiles.remove(textDocumentIdentifier);
    this.diagnosticsProvider.removeIgnoredUri(textDocumentIdentifier.getUri());

    // Clear published diagnostics.
    final List<Diagnostic> diagnostics = Collections.emptyList();
    final String uriStr = textDocumentIdentifier.getUri();
    final PublishDiagnosticsParams publishParams =
        new PublishDiagnosticsParams(uriStr, diagnostics);
    final LanguageClient languageClient = this.languageServer.getLanguageClient();
    languageClient.publishDiagnostics(publishParams);

    if (LOGGER_DURATION.isTraceEnabled()) {
      LOGGER_DURATION.trace(
          "Duration: {} didClose, uri: {}",
          String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
          textDocumentIdentifier.getUri());
    }
  }

  @Override
  public void didSave(final DidSaveTextDocumentParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocumentIdentifier = params.getTextDocument();
    LOGGER.debug("didSave, uri: {}", textDocumentIdentifier.getUri());
    if (LOGGER_DURATION.isTraceEnabled()) {
      LOGGER_DURATION.trace(
          "Duration: {} didSave, uri: {}",
          String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
          textDocumentIdentifier.getUri());
    }
  }

  private void publishDiagnostics(final MagikTypedFile magikFile) {
    final List<Diagnostic> diagnostics = this.diagnosticsProvider.provideDiagnostics(magikFile);

    // Publish to client.
    final String uri = magikFile.getUri().toString();
    final PublishDiagnosticsParams publishParams = new PublishDiagnosticsParams(uri, diagnostics);
    final LanguageClient languageClient = this.languageServer.getLanguageClient();
    languageClient.publishDiagnostics(publishParams);
  }

  @Override
  public CompletableFuture<Hover> hover(final HoverParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug(
        "hover: uri: {}, position: {},{}",
        textDocument.getUri(),
        params.getPosition().getLine(),
        params.getPosition().getCharacter());

    final Position position = params.getPosition();
    final OpenedFile openedFile = this.openedFiles.get(textDocument);

    return CompletableFuture.supplyAsync(
        () -> {
          final Hover hover;
          if (openedFile == null) {
            hover = null;
          } else if (openedFile instanceof ProductDefFile productDefFile) {
            hover = this.hoverProvider.provideHover(productDefFile, position);
          } else if (openedFile instanceof ModuleDefFile moduleDefFile) {
            hover = this.hoverProvider.provideHover(moduleDefFile, position);
          } else if (openedFile instanceof MagikTypedFile magikFile) {
            hover = this.hoverProvider.provideHover(magikFile, position);
          } else if (openedFile instanceof LintPropertiesFile) {
            // TODO support hovering for lint properties file
            hover = null;
          } else {
            throw new UnsupportedOperationException();
          }

          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} hover: uri: {}, position: {},{}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri(),
                params.getPosition().getLine(),
                params.getPosition().getCharacter());
          }
          return hover;
        });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      implementation(final ImplementationParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug(
        "implementation, uri: {}, position: {},{}",
        textDocument.getUri(),
        params.getPosition().getLine(),
        params.getPosition().getCharacter());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(() -> Either.forLeft(Collections.emptyList()));
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    final Position lsp4jPosition = params.getPosition();
    final nl.ramsolutions.sw.magik.Position position =
        Lsp4jConversion.positionFromLsp4j(lsp4jPosition);
    return CompletableFuture.supplyAsync(
        () -> {
          final List<nl.ramsolutions.sw.magik.Location> locations =
              this.implementationProvider.provideImplementations(magikFile, position);
          final List<Location> lsp4jLocations =
              locations.stream().map(Lsp4jConversion::locationToLsp4j).toList();
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} implementation, uri: {}, position: {},{}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri(),
                params.getPosition().getLine(),
                params.getPosition().getCharacter());
          }
          return Either.forLeft(lsp4jLocations);
        });
  }

  @Override
  public CompletableFuture<SignatureHelp> signatureHelp(final SignatureHelpParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug(
        "signatureHelp, uri: {}, position: {},{}",
        textDocument.getUri(),
        params.getPosition().getLine(),
        params.getPosition().getCharacter());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(SignatureHelp::new);
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    final Position position = params.getPosition();
    return CompletableFuture.supplyAsync(
        () -> {
          final SignatureHelp signatureHelp =
              this.signatureHelpProvider.provideSignatureHelp(magikFile, position);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} signatureHelp, uri: {}, position: {},{}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri(),
                params.getPosition().getLine(),
                params.getPosition().getCharacter());
          }
          return signatureHelp;
        });
  }

  @Override
  public CompletableFuture<List<FoldingRange>> foldingRange(
      final FoldingRangeRequestParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug("foldingRange, uri: {}", textDocument.getUri());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    return CompletableFuture.supplyAsync(
        () -> {
          final List<FoldingRange> foldingRanges;
          if (openedFile == null) {
            foldingRanges = Collections.emptyList();
          } else if (openedFile instanceof ProductDefFile productDefFile) {
            foldingRanges = this.foldingRangeProvider.provideFoldingRanges(productDefFile);
          } else if (openedFile instanceof ModuleDefFile moduleDefFile) {
            foldingRanges = this.foldingRangeProvider.provideFoldingRanges(moduleDefFile);
          } else if (openedFile instanceof MagikTypedFile magikFile) {
            foldingRanges = this.foldingRangeProvider.provideFoldingRanges(magikFile);
          } else if (openedFile instanceof LintPropertiesFile) {
            return new ArrayList<>();
          } else {
            throw new UnsupportedOperationException();
          }

          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} foldingRange, uri: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri());
          }
          return foldingRanges;
        });
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
      definition(final DefinitionParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug("definitions, uri: {}", textDocument.getUri());

    final Position lsp4jPosition = params.getPosition();
    final nl.ramsolutions.sw.magik.Position position =
        Lsp4jConversion.positionFromLsp4j(lsp4jPosition);
    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    return CompletableFuture.supplyAsync(
        () -> {
          final List<nl.ramsolutions.sw.magik.Location> locations;
          if (openedFile == null) {
            locations = Collections.emptyList();
          } else if (openedFile instanceof ProductDefFile productDefFile) {
            locations = this.definitionsProvider.provideDefinitions(productDefFile, position);
          } else if (openedFile instanceof ModuleDefFile moduleDefFile) {
            locations = this.definitionsProvider.provideDefinitions(moduleDefFile, position);
          } else if (openedFile instanceof MagikTypedFile magikFile) {
            locations = this.definitionsProvider.provideDefinitions(magikFile, position);
          } else {
            throw new UnsupportedOperationException();
          }

          final Either<List<? extends Location>, List<? extends LocationLink>> forLeft =
              Either.forLeft(locations.stream().map(Lsp4jConversion::locationToLsp4j).toList());
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} definitions, uri: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri());
          }
          return forLeft;
        });
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(final ReferenceParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug("references, uri: {}", textDocument.getUri());

    final Position lsp4jPosition = params.getPosition();
    final nl.ramsolutions.sw.magik.Position position =
        Lsp4jConversion.positionFromLsp4j(lsp4jPosition);
    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    return CompletableFuture.supplyAsync(
        () -> {
          final List<Location> references;
          if (openedFile == null) {
            references = Collections.emptyList();
          } else if (openedFile instanceof ProductDefFile productDefFile) {
            references =
                this.referencesProvider.provideReferences(productDefFile, position).stream()
                    .map(Lsp4jConversion::locationToLsp4j)
                    .toList();
          } else if (openedFile instanceof ModuleDefFile moduleDefFile) {
            references =
                this.referencesProvider.provideReferences(moduleDefFile, position).stream()
                    .map(Lsp4jConversion::locationToLsp4j)
                    .toList();
          } else if (openedFile instanceof MagikTypedFile magikFile) {
            references =
                this.referencesProvider.provideReferences(magikFile, position).stream()
                    .map(Lsp4jConversion::locationToLsp4j)
                    .toList();
          } else {
            throw new UnsupportedOperationException();
          }

          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} references, uri: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri());
          }
          return references;
        });
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
      final CompletionParams params) {
    final long start = System.nanoTime();


    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug(
      "completion, uri: {}, position: {},{}",
      textDocument.getUri(),
      params.getPosition().getLine(),
      params.getPosition().getCharacter()
    );

    final OpenedFile openedFile = this.openedFiles.get(textDocument);

    if (textDocument.getUri().endsWith("magiklintrc.properties")) {
      // TODO use the params to get if the user is behind a = or not and then check if integer
      // etc.
      return completionsForLintFile(params);
    } else if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(() -> Either.forLeft(Collections.emptyList()));
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    final Position position = params.getPosition();
    return CompletableFuture.supplyAsync(
      () -> {
        final List<CompletionItem> completions =
          this.completionProvider.provideCompletions(magikFile, position);
        if (LOGGER_DURATION.isTraceEnabled()) {
          LOGGER_DURATION.trace(
            "Duration: {} completion, uri: {}, position: {},{}",
            String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
            textDocument.getUri(),
            params.getPosition().getLine(),
            params.getPosition().getCharacter()
          );
        }
        return Either.forLeft(completions);
      });
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(
      final DocumentFormattingParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug("formatting, uri: {}", textDocument.getUri());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(Collections::emptyList);
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    final FormattingOptions options = params.getOptions();
    return CompletableFuture.supplyAsync(
        () -> {
          if (!this.formattingProvider.canFormat(magikFile)) {
            LOGGER.warn("Cannot format due to syntax error");
            return Collections.emptyList();
          }

          final List<TextEdit> textEdits =
              this.formattingProvider.provideFormatting(magikFile, options);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} formatting, uri: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri());
          }
          return textEdits;
        });
  }

  @Override
  public CompletableFuture<SemanticTokens> semanticTokensFull(final SemanticTokensParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug("semanticTokensFull, uri: {}", textDocument.getUri());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    return CompletableFuture.supplyAsync(
        () -> {
          final SemanticTokens semanticTokens;
          if (openedFile == null) {
            semanticTokens = null;
          } else if (openedFile instanceof ProductDefFile productDefFile) {
            semanticTokens = this.semanticTokenProver.provideSemanticTokensFull(productDefFile);
          } else if (openedFile instanceof ModuleDefFile moduleDefFile) {
            semanticTokens = this.semanticTokenProver.provideSemanticTokensFull(moduleDefFile);
          } else if (openedFile instanceof MagikTypedFile magikFile) {
            semanticTokens = this.semanticTokenProver.provideSemanticTokensFull(magikFile);
          } else {
            throw new UnsupportedOperationException();
          }

          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} semanticTokensFull, uri: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri());
          }
          return semanticTokens;
        });
  }

  @Override
  public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>>
      prepareRename(final PrepareRenameParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug(
        "prepareRename, uri: {}, position: {},{}",
        textDocument.getUri(),
        params.getPosition().getLine(),
        params.getPosition().getCharacter());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(() -> null);
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    final Position position = params.getPosition();
    return CompletableFuture.supplyAsync(
        () -> {
          final Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> prepareRename =
              this.renameProvider.providePrepareRename(magikFile, position);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} prepareRename, uri: {}, position: {},{}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri(),
                params.getPosition().getLine(),
                params.getPosition().getCharacter());
          }
          return prepareRename;
        });
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(final RenameParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug(
        "rename, uri: {}, position: {},{}",
        textDocument.getUri(),
        params.getPosition().getLine(),
        params.getPosition().getCharacter());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(() -> null);
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    final Position position = params.getPosition();
    final String newName = params.getNewName();
    return CompletableFuture.supplyAsync(
        () -> {
          final WorkspaceEdit rename =
              this.renameProvider.provideRename(magikFile, position, newName);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} rename, uri: {}, position: {},{}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri(),
                params.getPosition().getLine(),
                params.getPosition().getCharacter());
          }
          return rename;
        });
  }

  @Override
  public CompletableFuture<List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>>>
      documentSymbol(final DocumentSymbolParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug("documentSymbol, uri: {}", textDocument.getUri());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(Collections::emptyList);
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    return CompletableFuture.supplyAsync(
        () -> {
          final List<Either<SymbolInformation, DocumentSymbol>> documentSymbols =
              this.documentSymbolProvider.provideDocumentSymbols(magikFile);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} documentSymbol, uri: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri());
          }
          return documentSymbols;
        });
  }

  @Override
  public CompletableFuture<List<SelectionRange>> selectionRange(final SelectionRangeParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug("selectionRange, uri: {}", textDocument.getUri());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(Collections::emptyList);
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    final List<nl.ramsolutions.sw.magik.Position> positions =
        params.getPositions().stream().map(Lsp4jConversion::positionFromLsp4j).toList();
    return CompletableFuture.supplyAsync(
        () -> {
          final List<SelectionRange> selectionRanges =
              this.selectionRangeProvider.provideSelectionRanges(magikFile, positions);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} selectionRange, uri: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri());
          }
          return selectionRanges;
        });
  }

  @Override
  public CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(
      final TypeHierarchyPrepareParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    LOGGER.debug(
        "prepareTypeHierarchy, uri: {}, position: {},{}",
        textDocument.getUri(),
        params.getPosition().getLine(),
        params.getPosition().getCharacter());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(() -> null);
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    final Position position = params.getPosition();

    return CompletableFuture.supplyAsync(
        () -> {
          final List<TypeHierarchyItem> typeHierarchy =
              this.typeHierarchyProvider.prepareTypeHierarchy(magikFile, position);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} prepareTypeHierarchy, uri: {}, position: {},{}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri(),
                params.getPosition().getLine(),
                params.getPosition().getCharacter());
          }
          return typeHierarchy;
        });
  }

  @Override
  public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySubtypes(
      final TypeHierarchySubtypesParams params) {
    final long start = System.nanoTime();

    final TypeHierarchyItem item = params.getItem();
    LOGGER.debug("typeHierarchySubtypes, item: {}", item.getName());

    return CompletableFuture.supplyAsync(
        () -> {
          final List<TypeHierarchyItem> subtypes =
              this.typeHierarchyProvider.typeHierarchySubtypes(item);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} didOpen, typeHierarchySubtypes, item: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                item.getName());
          }
          return subtypes;
        });
  }

  @Override
  public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySupertypes(
      final TypeHierarchySupertypesParams params) {
    final long start = System.nanoTime();

    final TypeHierarchyItem item = params.getItem();
    LOGGER.debug("typeHierarchySupertypes, item: {}", item.getName());

    return CompletableFuture.supplyAsync(
        () -> {
          final List<TypeHierarchyItem> supertypes =
              this.typeHierarchyProvider.typeHierarchySupertypes(item);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} didOpen, typeHierarchySupertypes, item: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                item.getName());
          }
          return supertypes;
        });
  }

  @Override
  public CompletableFuture<List<InlayHint>> inlayHint(final InlayHintParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    final Range range = params.getRange();
    LOGGER.debug(
        "inlayHint, uri: {}, range: {},{}-{},{}",
        textDocument.getUri(),
        range.getStart().getLine(),
        range.getStart().getCharacter(),
        range.getEnd().getLine(),
        range.getEnd().getCharacter());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(Collections::emptyList);
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    return CompletableFuture.supplyAsync(
        () -> {
          List<InlayHint> inlayHints = this.inlayHintProvider.provideInlayHints(magikFile, range);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} inlayHint, uri: {}, range: {},{}-{},{}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri(),
                range.getStart().getLine(),
                range.getStart().getCharacter(),
                range.getEnd().getLine(),
                range.getEnd().getCharacter());
          }
          return inlayHints;
        });
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(
      final CodeActionParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    final Range range = params.getRange();
    LOGGER.debug(
        "codeAction, uri: {}, range: {},{}-{},{}",
        textDocument.getUri(),
        range.getStart().getLine(),
        range.getStart().getCharacter(),
        range.getEnd().getLine(),
        range.getEnd().getCharacter());

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof MagikTypedFile)) {
      return CompletableFuture.supplyAsync(Collections::emptyList);
    }

    final MagikTypedFile magikFile = (MagikTypedFile) openedFile;
    final nl.ramsolutions.sw.magik.Range magikRange = Lsp4jConversion.rangeFromLsp4j(range);
    final CodeActionContext context = params.getContext();
    return CompletableFuture.supplyAsync(
        () -> {
          final List<nl.ramsolutions.sw.magik.CodeAction> codeActions =
              this.codeActionProvider.provideCodeActions(magikFile, magikRange, context);
          final List<Either<Command, CodeAction>> codeActionsLsp4j =
              codeActions.stream()
                  .map(
                      codeAction ->
                          Lsp4jUtils.createCodeAction(
                              magikFile, codeAction.getTitle(), codeAction.getEdits()))
                  .map(Either::<Command, CodeAction>forRight)
                  .toList();
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} codeAction, uri: {}, range: {},{}-{},{}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri(),
                range.getStart().getLine(),
                range.getStart().getCharacter(),
                range.getEnd().getLine(),
                range.getEnd().getCharacter());
          }
          return codeActionsLsp4j;
        });
  }

  @JsonRequest("custom/getAllChecks")
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completionsForLintFile(
      CompletionParams params) {
    // TODO put this into the completionProvider
    final long start = System.nanoTime();
    LOGGER.debug("completionsForLintFile");

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    final Position position = params.getPosition();
    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    if (!(openedFile instanceof LintPropertiesFile lintPropertiesFile)) {
      return CompletableFuture.supplyAsync(() -> Either.forLeft(new ArrayList<>()));
    }

    String completionType = "properties";

    List<String> lines = lintPropertiesFile.getSource().lines().toList();
    int lineNo = position.getLine();
    int charPos = position.getCharacter();
    String line = "";

    int tempStartIndex = 0;

    if (lineNo < lines.size() && (line = lines.get(lineNo)) != null) {
      int eqPosition = line.indexOf('=');

      if (eqPosition != -1) {
        String property = line.substring(0, eqPosition);

        // TODO add completion for enabled
        if (property.contains(MagikChecksConfiguration.KEY_DISABLED_CHECKS)
            || property.contains(MagikChecksConfiguration.KEY_ENABLED_CHECKS)
            || property.contains(MagikChecksConfiguration.KEY_IGNORED_PATHS)) {
          tempStartIndex = eqPosition + 1;
          completionType = "rules";
        } else if (eqPosition < charPos) {
          completionType = "none";
        }
      }
    }

    List<Class<? extends MagikCheck>> checks = CheckList.getChecks();

    String finalLine = line == null ? "" : line;

    switch (completionType) {
      case "rules":
        {
          int finalStartIndex = tempStartIndex;
          return CompletableFuture.supplyAsync(
              () -> {
                int startIndex = finalStartIndex;
                int endIndex = finalLine.length() - 1;
                Set<Character> startSeparators = Set.of('=', ',');

                for (int i = startIndex; i < finalLine.length(); i++) {
                  char c = finalLine.charAt(i);
                  if (i < charPos && startSeparators.contains(c)) {
                    startIndex = ++i;
                  } else if (i >= charPos && c == ',' || i + 1 == finalLine.length()) {
                    endIndex = i;
                  }
                }

                if (startIndex > endIndex) {
                  startIndex = endIndex;
                }

                String toReplaceStr = finalLine.substring(startIndex, endIndex);
                Range toReplace =
                    new org.eclipse.lsp4j.Range(
                        new Position(lineNo, startIndex), new Position(lineNo, endIndex));

                int finalStartIndex1 = startIndex;
                List<CompletionItem> completions =
                    checks.stream()
                        .map(
                            check -> {
                              String rule = MagikChecksConfiguration.checkKey(check);
                              CompletionItem item = new CompletionItem(rule);

                              item.setInsertText(rule);
                              item.setKind(CompletionItemKind.Property);
                              item.setTextEdit(
                                  Either.forRight(
                                      new InsertReplaceEdit(
                                          item.getInsertText(),
                                          toReplace,
                                          new org.eclipse.lsp4j.Range(
                                              new Position(lineNo, finalStartIndex1),
                                              new Position(
                                                  lineNo,
                                                  finalStartIndex1
                                                      + item.getInsertText().length())))));
                              // TODO find out why the completion items do not get rendered if the
                              // toReplaceStr is empty
                              // item.setDocumentation(); // TODO add description to @Rule
                              // annotations

                              return item;
                            })
                        .filter(item -> item.getInsertText().contains(toReplaceStr))
                        .sorted(Comparator.comparing(CompletionItem::getLabel))
                        .toList();

                LOGGER_DURATION.trace(
                    "Duration: {} completionsForLintFile/rules, suggestions: {}",
                    (System.nanoTime() - start) / 1000000000.0,
                    completions.size());
                return Either.forLeft(completions);
              });
        }
      case "properties":
        {
          return CompletableFuture.supplyAsync(
              () -> {
                List<CompletionItem> completions =
                    checks.stream()
                        .flatMap(
                            c -> {
                              String checkKey = MagikChecksConfiguration.checkKey(c);

                              return Arrays.stream(c.getFields())
                                  .map(field -> field.getAnnotation(RuleProperty.class))
                                  .filter(Objects::nonNull)
                                  .map(
                                      property -> {
                                        final String propertyKey =
                                            MagikChecksConfiguration.propertyKey(property);
                                        final String configKey = checkKey + "." + propertyKey;
                                        CompletionItem item = new CompletionItem(configKey);

                                        item.setInsertText(
                                            configKey + "=" + property.defaultValue());

                                        String builder =
                                            "Type: `"
                                                + property.type()
                                                + "` Default: `"
                                                + property.defaultValue();
                                        item.setDetail(builder);

                                        item.setDocumentation(
                                            new MarkupContent(
                                                MarkupKind.MARKDOWN,
                                                "*" + property.description() + "*"));
                                        item.setKind(CompletionItemKind.Property);
                                        item.setFilterText(finalLine);
                                        item.setInsertTextMode(InsertTextMode.AdjustIndentation);
                                        setInsertEditForProperty(item, position, finalLine);

                                        return item;
                                      });
                            })
                        .filter(item -> item.getLabel().contains(finalLine))
                        .sorted(Comparator.comparing(CompletionItem::getLabel))
                        .toList();

                if (MagikChecksConfiguration.KEY_DISABLED_CHECKS.contains(finalLine)) {
                  completions = new ArrayList<>(completions);

                  CompletionItem disabledItem = new CompletionItem("disabled");
                  disabledItem.setInsertText("disabled");
                  disabledItem.setDetail("Disables rules completely");
                  disabledItem.setKind(CompletionItemKind.Property);
                  disabledItem.setFilterText(finalLine);
                  disabledItem.setInsertTextMode(InsertTextMode.AdjustIndentation);
                  setInsertEditForProperty(disabledItem, position, finalLine);

                  completions.add(disabledItem);
                }

                if (MagikChecksConfiguration.KEY_ENABLED_CHECKS.contains(finalLine)) {
                  completions = new ArrayList<>(completions);

                  CompletionItem disabledItem = new CompletionItem("enabled");
                  disabledItem.setInsertText("enabled");
                  disabledItem.setDetail("Enables rules");
                  disabledItem.setKind(CompletionItemKind.Property);
                  disabledItem.setFilterText(finalLine);
                  disabledItem.setInsertTextMode(InsertTextMode.AdjustIndentation);
                  setInsertEditForProperty(disabledItem, position, finalLine);

                  completions.add(disabledItem);
                }

                LOGGER_DURATION.trace(
                    "Duration: {} completionsForLintFile/properties",
                    (System.nanoTime() - start) / 1000000000.0);
                return Either.forLeft(completions);
              });
        }
      case "none":
        return CompletableFuture.supplyAsync(() -> Either.forLeft(new ArrayList<>()));
    }
    throw new UnsupportedOperationException("Unsupported completion type: " + completionType);
  }

  private void setInsertEditForProperty(CompletionItem item, Position position, String finalLine) {
    item.setTextEdit(
        Either.forRight(
            new InsertReplaceEdit(
                item.getInsertText(),
                new org.eclipse.lsp4j.Range(
                    new Position(position.getLine(), 0),
                    new Position(position.getLine(), finalLine.length())),
                new org.eclipse.lsp4j.Range(
                    new Position(position.getLine(), 0),
                    new Position(position.getLine(), item.getInsertText().length())))));
  }

  @JsonRequest(value = "custom/addLintIgnore")
  public CompletableFuture<Boolean> addLintIgnore(JsonArray array) {
    if (array.isEmpty()) {
      LOGGER.error("addLintIgnore, no AddLintIgnoreParams object");
      return CompletableFuture.completedFuture(false);
    }

    LintIgnoreParams params = new Gson().fromJson(array.get(0), LintIgnoreParams.class);
    LOGGER.trace("addLintIgnore, uri: {}", params.getUri());

    String uri = params.getUri();

    OpenedFile file = this.openedFiles.get(new TextDocumentIdentifier(uri));
    if (file instanceof MagikTypedFile typedFile) {
      this.diagnosticsProvider.addIgnoredUri(uri);
      this.publishDiagnostics(typedFile);
    }
    return CompletableFuture.completedFuture(this.diagnosticsProvider.isIgnoredUri(uri));
  }

  @JsonRequest(value = "custom/removeLintIgnore")
  public CompletableFuture<Boolean> removeLintIgnore(JsonArray array) {
    if (array.isEmpty()) {
      LOGGER.error("removeLintIgnore, no LintIgnoreParams object");
      return CompletableFuture.completedFuture(false);
    }

    LintIgnoreParams params = new Gson().fromJson(array.get(0), LintIgnoreParams.class);
    LOGGER.trace("removeLintIgnore, uri: {}", params.getUri());

    final String uri = params.getUri();
    OpenedFile file = this.openedFiles.get(new TextDocumentIdentifier(uri));
    if (file instanceof MagikTypedFile typedFile) {
      this.diagnosticsProvider.removeIgnoredUri(uri);
      this.publishDiagnostics(typedFile);
    }

    return CompletableFuture.completedFuture(this.diagnosticsProvider.isIgnoredUri(uri));
  }

  @JsonRequest(value = "custom/isLintIgnored")
  public CompletableFuture<Boolean> isLintIgnored(JsonArray array) {
    if (array.isEmpty()) {
      LOGGER.error("isLintIgnored, no LintIgnoreParams object");
      return CompletableFuture.completedFuture(false);
    }

    LintIgnoreParams params = new Gson().fromJson(array.get(0), LintIgnoreParams.class);
    LOGGER.trace("isLintIgnored, uri: {}", params.getUri());
    final String uri = params.getUri();

    return CompletableFuture.completedFuture(this.diagnosticsProvider.isIgnoredUri(uri));
  }

  @Override
  public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(
      final CallHierarchyPrepareParams params) {
    final long start = System.nanoTime();

    final TextDocumentIdentifier textDocument = params.getTextDocument();
    final Position position = params.getPosition();
    LOGGER.debug(
        "prepareCallHierarchy, uri: {}, position: {},{}",
        textDocument.getUri(),
        position.getLine(),
        position.getCharacter());

    final nl.ramsolutions.sw.magik.Position magikPosition =
        Lsp4jConversion.positionFromLsp4j(position);

    final OpenedFile openedFile = this.openedFiles.get(textDocument);
    return CompletableFuture.supplyAsync(
        () -> {
          final List<CallHierarchyItem> items =
              openedFile instanceof MagikTypedFile magikFile
                  ? this.callHierarchyProvider.prepareCallHierarchy(magikFile, magikPosition)
                  : null;
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} prepareCallHierarchy, uri: {}, position: {},{}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                textDocument.getUri(),
                position.getLine(),
                position.getCharacter());
          }
          return items;
        });
  }

  @Override
  public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
      final CallHierarchyIncomingCallsParams params) {
    final long start = System.nanoTime();

    final CallHierarchyItem item = params.getItem();
    LOGGER.debug("callHierarchyIncomingCalls, item: {}", item.getName());

    return CompletableFuture.supplyAsync(
        () -> {
          final List<CallHierarchyIncomingCall> items =
              this.callHierarchyProvider.callHierarchyIncomingCalls(item);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} callHierarchyIncomingCalls, item: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                item.getName());
          }
          return items;
        });
  }

  @Override
  public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
      final CallHierarchyOutgoingCallsParams params) {
    final long start = System.nanoTime();

    final CallHierarchyItem item = params.getItem();
    LOGGER.debug("callHierarchyOutgoingCalls, item: {}", item.getName());

    return CompletableFuture.supplyAsync(
        () -> {
          final List<CallHierarchyOutgoingCall> items =
              this.callHierarchyProvider.callHierarchyOutgoingCalls(item);
          if (LOGGER_DURATION.isTraceEnabled()) {
            LOGGER_DURATION.trace(
                "Duration: {} callHierarchyOutgoingCalls, item: {}",
                String.format("%.3f", (System.nanoTime() - start) / 1000000000.0),
                item.getName());
          }
          return items;
        });
  }
}
