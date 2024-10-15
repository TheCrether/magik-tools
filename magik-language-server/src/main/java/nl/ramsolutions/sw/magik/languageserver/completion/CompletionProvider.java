package nl.ramsolutions.sw.magik.languageserver.completion;

import com.sonar.sslr.api.AstNode;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import nl.ramsolutions.sw.MagikToolsProperties;
import nl.ramsolutions.sw.magik.MagikTypedFile;
import nl.ramsolutions.sw.magik.Range;
import nl.ramsolutions.sw.magik.analysis.AstQuery;
import nl.ramsolutions.sw.magik.analysis.definitions.*;
import nl.ramsolutions.sw.magik.analysis.helpers.MethodDefinitionNodeHelper;
import nl.ramsolutions.sw.magik.analysis.helpers.PackageNodeHelper;
import nl.ramsolutions.sw.magik.analysis.scope.GlobalScope;
import nl.ramsolutions.sw.magik.analysis.scope.Scope;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeStringResolver;
import nl.ramsolutions.sw.magik.analysis.typing.reasoner.LocalTypeReasonerState;
import nl.ramsolutions.sw.magik.api.MagikGrammar;
import nl.ramsolutions.sw.magik.api.MagikKeyword;
import nl.ramsolutions.sw.magik.api.MagikOperator;
import nl.ramsolutions.sw.magik.api.MagikPunctuator;
import nl.ramsolutions.sw.magik.languageserver.JSONUtility;
import nl.ramsolutions.sw.magik.languageserver.Lsp4jConversion;
import nl.ramsolutions.sw.magik.languageserver.hover.HoverProvider;
import nl.ramsolutions.sw.magik.parser.MagikCommentExtractor;
import org.eclipse.lsp4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Completion provider. */
public class CompletionProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(CompletionProvider.class);
  private static final Set<Character> REMOVAL_STOP_CHARS = new HashSet<>();
  private static final String TOPIC_DEPRECATED = "deprecated";

  private final MagikToolsProperties properties;
  private final CompletionHelper completionHelper;

  private static final Set<String> SCOPE_ENTRIES_TO_REMOVE = Set.of("def_slotted_exemplar");

  static {
    REMOVAL_STOP_CHARS.add(' ');
    REMOVAL_STOP_CHARS.add('\t');

    @SuppressWarnings("java:S1612")
    final Set<Character> punctuatorChars =
        Arrays.stream(MagikPunctuator.values())
            .map(MagikPunctuator::getValue)
            .flatMap(value -> value.chars().mapToObj(i -> (char) i))
            .collect(Collectors.toSet());
    REMOVAL_STOP_CHARS.addAll(punctuatorChars);

    @SuppressWarnings("java:S1612")
    final Set<Character> operatorChars =
        Arrays.stream(MagikOperator.values())
            .map(MagikOperator::getValue)
            .flatMap(value -> value.chars().mapToObj(i -> (char) i))
            .collect(Collectors.toSet());
    REMOVAL_STOP_CHARS.addAll(operatorChars);
  }

  public CompletionProvider(MagikToolsProperties properties) {
    this.properties = properties;
    this.completionHelper = new CompletionHelper(properties);
  }

  /**
   * Set server capabilities.
   *
   * @param capabilities Server capabilities.
   */
  public void setCapabilities(final ServerCapabilities capabilities) {
    final CompletionOptions completionOptions = new CompletionOptions();
    completionOptions.setTriggerCharacters(List.of(".", "="));
    completionOptions.setResolveProvider(true);
    capabilities.setCompletionProvider(completionOptions);
  }

  /**
   * Get a list of completions.
   *
   * @param magikFile Magik file.
   * @param position Position in file.
   * @return List of completions.
   */
  public List<CompletionItem> provideCompletions(
      final MagikTypedFile magikFile, final Position position) {
    // Do our best to get a token value, and clean up the source while we're at it.
    final Map.Entry<MagikTypedFile, String> usable = this.getUsableMagikFile(magikFile, position);
    final MagikTypedFile newMagikFile = usable.getKey();
    final String removedPart = usable.getValue();
    final Position newPosition =
        new Position(position.getLine(), position.getCharacter() - removedPart.length());
    final AstNode node = newMagikFile.getTopNode();
    final AstNode tokenNode = AstQuery.nodeAt(node, Lsp4jConversion.positionFromLsp4j(newPosition));

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Current token: {}", removedPart);
    }

    // Ensure not in comment.
    if (this.inComment(node, position)) {
      // TODO completion for @param, @slot etc
      return Collections.emptyList();
    }

    // Keyword completion: '_'.
    if (removedPart.startsWith("_")) {
      LOGGER.debug("Providing keyword completions");
      return this.provideKeywordCompletions();
    }

    CompletionResponse response = new CompletionResponse();
    completionHelper.setUriField(response, magikFile.getUri());
    List<CompletionItem> completionItems = new ArrayList<>();

    AstNode methodInvocationNode = null, methodInvocationOnSlotNode = null;
    if (tokenNode != null) {

      methodInvocationNode =
          AstQuery.getParentFromChain(
              tokenNode, MagikGrammar.IDENTIFIER, MagikGrammar.METHOD_INVOCATION);
      methodInvocationOnSlotNode =
          AstQuery.getParentFromChain(
              tokenNode, MagikGrammar.IDENTIFIER, MagikGrammar.SLOT, MagikGrammar.ATOM);
    }

    if (tokenNode == null && removedPart.equals(".")
        || tokenNode != null && tokenNode.getTokenOriginalValue().equals(".")) {
      // only '.' or starts with '.' -> slot invocations
      String searchedText = removedPart;
      if (removedPart.equals(".")) {
        searchedText = "";
      }
      completionItems = this.provideSlotCompletion(response, newMagikFile, position, searchedText);
    } else if (tokenNode != null) {
      // Method completion: METHOD_INVOCATION
      if (methodInvocationOnSlotNode != null) {
        AstNode identifier = AstQuery.getParentFromChain(tokenNode, MagikGrammar.IDENTIFIER);
        if (identifier != null) {
          completionItems =
              this.provideMethodInvocationCompletion(
                  response, newMagikFile, identifier, removedPart);
        }
      } else if (methodInvocationNode != null
          || removedPart.startsWith(".")
          || removedPart.isEmpty()) {
        completionItems =
            this.provideMethodInvocationCompletion(response, newMagikFile, tokenNode, removedPart);
      } else {
        completionItems = this.provideGlobalCompletion(response, newMagikFile, position, tokenNode);
      }
    } else if (!removedPart.equals(":")) {
      completionItems = this.provideGlobalCompletion(response, newMagikFile, position, tokenNode);
    }

    if (!completionItems.isEmpty()) {
      CompletionResponses.store(response);
    }

    return completionItems;
  }

  public CompletionItem provideCompletionItem(
      MagikTypedFile magikTypedFile, CompletionItem unresolved) {
    @SuppressWarnings("unchecked")
    Map<String, String> data = JSONUtility.toModel(unresolved.getData(), Map.class);
    // clean data
    unresolved.setData(null);

    String requestId = data.getOrDefault(CompletionHelper.REQUEST_ID_KEY, "");
    String definitionIndex = data.getOrDefault(CompletionHelper.INDEX_KEY, "");
    if (requestId.isEmpty() || definitionIndex.isEmpty()) {
      LOGGER.warn("Tried resolving non-cached completion item: {}", unresolved.getLabel());
      return unresolved;
    }

    long rId = Long.parseLong(requestId);
    int index = Integer.parseInt(definitionIndex);
    CompletionResponse response = CompletionResponses.get(rId);
    if (response == null) {
      LOGGER.warn("Tried resolving non-cached completion response for: {}", unresolved.getLabel());
      return unresolved;
    }

    MagikDefinition definition = response.getDefinitions().get(index);
    if (definition == null) {
      LOGGER.warn("Tried resolving MagikDefinition for: {}", unresolved.getLabel());
      return unresolved;
    }

    StringBuilder docBuilder = new StringBuilder();

    if (definition instanceof MethodDefinition methodDef) {
      HoverProvider.buildMethodSignatureDoc(methodDef, docBuilder, this.properties);
    } else if (definition instanceof ExemplarDefinition exemplarDef) {
      HoverProvider.buildTypeSignatureDoc(magikTypedFile, exemplarDef, docBuilder, this.properties);
    } else if (definition instanceof SlotDefinition slotDef) {
      // TODO implement correct documentation for SlotDefinitions here and in HoverProvider
      docBuilder.append(slotDef.getDoc());
    } else {
      docBuilder.append("No documentation found");
      LOGGER.warn(
          "resolving documentation for type {} not implemented",
          definition.getClass().getSimpleName());
    }

    unresolved.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, docBuilder.toString()));

    return unresolved;
  }

  /**
   * Test if position is in comment.
   *
   * @param node Top node.
   * @param position Position in file.
   * @return Returns
   */
  private boolean inComment(final AstNode node, final Position position) {
    final nl.ramsolutions.sw.magik.Position nativePosition =
        Lsp4jConversion.positionFromLsp4j(position);
    return MagikCommentExtractor.extractComments(node)
        .anyMatch(
            token ->
                nativePosition.getLine() == token.getLine()
                    && nativePosition.getColumn() >= token.getColumn());
  }

  /**
   * Provide global completion.
   *
   * @param magikFile MagikFile.
   * @param position Position in source.
   * @param tokenNode Current node.
   * @return Completions items.
   */
  @SuppressWarnings("checkstyle:NestedIfDepth")
  private List<CompletionItem> provideGlobalCompletion(
      final CompletionResponse response,
      final MagikTypedFile magikFile,
      final Position position,
      final @Nullable AstNode tokenNode) {
    final List<MagikDefinition> definitions = response.getDefinitions();
    final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();

    // Keyword entries.
    final List<CompletionItem> items = new ArrayList<>(this.provideKeywordCompletions());

    String currentPackage = "sw";
    if (tokenNode != null) {
      PackageNodeHelper helper = new PackageNodeHelper(tokenNode);
      currentPackage = helper.getCurrentPackage();
    }
    final String finalCurrentPackage = currentPackage;

    // Scope entries.
    final AstNode topNode = magikFile.getTopNode();
    AstNode scopeNode =
        AstQuery.nodeSurrounding(topNode, Lsp4jConversion.positionFromLsp4j(position));
    if (scopeNode != null) {
      if (scopeNode.getFirstChild(MagikGrammar.BODY) != null) {
        scopeNode = scopeNode.getFirstChild(MagikGrammar.BODY);
      }
      final GlobalScope globalScope = magikFile.getGlobalScope();
      final Scope scopeForNode = globalScope.getScopeForNode(scopeNode);
      if (scopeForNode != null) {
        scopeForNode.getSelfAndAncestorScopes().stream()
            .flatMap(scope -> scope.getScopeEntriesInScope().stream())
            .filter(
                scopeEntry -> {
                  final AstNode definingNode = scopeEntry.getDefinitionNode();
                  final Range range = new Range(definingNode);
                  return Lsp4jConversion.positionFromLsp4j(position).isAfterRange(range)
                      && !SCOPE_ENTRIES_TO_REMOVE.contains(scopeEntry.getIdentifier());
                })
            .map(
                scopeEntry -> {
                  final CompletionItem item = new CompletionItem(scopeEntry.getIdentifier());
                  item.setSortText("  " + item.getLabel());
                  item.setInsertText(scopeEntry.getIdentifier());
                  item.setDetail(scopeEntry.getIdentifier());
                  item.setKind(CompletionItemKind.Variable);
                  return item;
                })
            .forEach(items::add);
      }
    }

    // Global types.
    final String identifierPart = tokenNode != null ? tokenNode.getTokenValue() : "";
    List<ExemplarDefinition> exemplarDefinitions =
        definitionKeeper.getExemplarDefinitions().stream()
            .filter(
                exemplarDef -> exemplarDef.getTypeString().getFullString().contains(identifierPart))
            .toList();

    final int start = definitions.size();
    for (int i = 0; i < exemplarDefinitions.size(); i++) {
      final ExemplarDefinition exemplarDef = exemplarDefinitions.get(i);

      CompletionItem item = exemplarCompletion(exemplarDef, finalCurrentPackage);
      item.setSortText("##" + item.getLabel());
      item.setData(
          completionHelper.getCompletionData(response.getId(), start + i, magikFile.getUri()));
      definitions.add(exemplarDef);

      items.add(item);
    }

    return items;
  }

  private CompletionItem exemplarCompletion(ExemplarDefinition exemplarDef, String currentPackage) {
    boolean prependPackage = shouldPrependPackage(exemplarDef.getTypeString(), currentPackage);
    final TypeString typeString = exemplarDef.getTypeString();

    final CompletionItem item = new CompletionItem(typeString.getFullString());
    if (prependPackage) {
      item.setInsertText(typeString.getFullString());
    } else {
      item.setInsertText(typeString.getIdentifier());
    }

    item.setKind(CompletionItemKind.Class);
    if (exemplarDef.getTopics().contains(TOPIC_DEPRECATED)) {
      item.setTags(List.of(CompletionItemTag.Deprecated));
    }

    return item;
  }

  /**
   * Provide method invocation completions.
   *
   * @param magikFile MagikFile.
   * @param tokenNode Token node.
   * @param tokenValue Token value.
   * @return List with {@link CompletionItem}s.
   */
  private List<CompletionItem> provideMethodInvocationCompletion(
      final CompletionResponse response,
      final MagikTypedFile magikFile,
      final AstNode tokenNode,
      final String tokenValue) {
    // Token -->
    // - parent: any --> parent: ATOM
    // - parent: IDENTIFIER --> parent: METHOD_INVOCATION --> previous sibling: ATOM
    // - parent: IDENTIFIER --> parent: METHOD_INVOCATION --> previous sibling: METHOD_INVOCATION
    final AstNode node = tokenNode.getParent();
    final AstNode parentNode = node.getParent();
    final AstNode wantedNode;
    if (parentNode != null && parentNode.is(MagikGrammar.ATOM)) {
      // Asking the ATOM node.
      wantedNode = parentNode;
    } else if (parentNode != null
        && (parentNode.is(MagikGrammar.METHOD_INVOCATION)
            || parentNode.is(MagikGrammar.PROCEDURE_INVOCATION))) {
      // Asking the previous invocation.
      wantedNode = parentNode.getPreviousSibling();
    } else {
      return Collections.emptyList();
    }

    final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
    final ExpressionResultString result = reasonerState.getNodeType(wantedNode);
    TypeString typeStr = result.get(0, TypeString.UNDEFINED);

    final TypeStringResolver resolver = magikFile.getTypeStringResolver();

    // TODO look if the node token value can be found in the local scope (variable)
    // because for a variable called `product` it matches the sw:product -> `new()` is at the top
    final String currentPackage = new PackageNodeHelper(node).getCurrentPackage();
    final boolean isExemplarInvocation =
        !resolver.resolve(TypeString.ofIdentifier(node.getTokenValue(), currentPackage)).isEmpty();

    final boolean isSelfInvocation =
        typeStr.getCombinedTypes().stream().anyMatch(type -> type == TypeString.SELF);
    if (isSelfInvocation) {
      final AstNode methodDefNode = tokenNode.getFirstAncestor(MagikGrammar.METHOD_DEFINITION);
      final MethodDefinitionNodeHelper helper = new MethodDefinitionNodeHelper(methodDefNode);
      typeStr = helper.getTypeString();
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Providing method completions for type: {}", typeStr.getFullString());
    }

    final String methodNamePart = tokenValue.startsWith(".") ? tokenValue.substring(1) : tokenValue;
    final TypeString finalTypeStr = typeStr;
    final List<MagikDefinition> definitions = response.getDefinitions();

    // Convert all known methods to CompletionItems.
    final List<MethodDefinition> filteredMethods =
        new ArrayList<>(
            resolver.getMethodDefinitions(finalTypeStr).stream()
                .filter(methodDef -> methodDef.getMethodName().contains(methodNamePart))
                .toList());

    final List<MethodDefinition> dbFields =
        filteredMethods.stream()
            .filter(
                methodDef -> methodDef.getModifiers().contains(MethodDefinition.Modifier.DB_TYPE))
            .toList();
    if (dbFields.stream().anyMatch(f -> f.getTypeName().equals(finalTypeStr))) {
      for (MethodDefinition dbField : dbFields) {
        if (!dbField.getTypeName().equals(finalTypeStr)) {
          filteredMethods.remove(dbField);
        }
      }
    }

    final List<CompletionItem> completionItems = new ArrayList<>();
    for (int i = 0; i < filteredMethods.size(); i++) {
      final MethodDefinition methodDef = filteredMethods.get(i);

      final CompletionItem item = new CompletionItem(methodDef.getMethodNameWithParameters());

      item.setInsertTextFormat(InsertTextFormat.Snippet);
      item.setInsertText(this.buildMethodInvocationSnippet(methodDef));
      item.setFilterText(methodDef.getMethodNameWithoutParentheses());
      if (methodDef.getModifiers().contains(MethodDefinition.Modifier.DB_TYPE)) {
        item.setKind(CompletionItemKind.Field);
      } else {
        item.setKind(CompletionItemKind.Method);
      }

      TypeString methodExemplarType = methodDef.getTypeName();
      String prefix = "";
      if (!finalTypeStr.equals(TypeString.SW_OBJECT)) {
        if (methodExemplarType.equals(finalTypeStr)) {
          prefix = " ";
        } else if (!methodExemplarType.equals(TypeString.SW_OBJECT)) {
          prefix = "!";
        } else {
          prefix = "#";
        }
      }

      if (methodDef.getMethodName().startsWith("new") && isExemplarInvocation) {
        item.setSortText(" ".repeat(3) + item.getLabel());
      } else {
        item.setSortText(prefix.repeat(2) + item.getLabel());
      }

      definitions.add(methodDef);
      item.setData(completionHelper.getCompletionData(response.getId(), i, magikFile.getUri()));

      if (methodDef.getTopics().contains(TOPIC_DEPRECATED)) {
        item.setTags(List.of(CompletionItemTag.Deprecated));
      }

      completionItems.add(item);
    }
    return completionItems;
  }

  private List<CompletionItem> provideSlotCompletion(
      final CompletionResponse response,
      final MagikTypedFile magikFile,
      final Position position,
      final String tokenValue) {
    List<CompletionItem> completionItems = new ArrayList<>();
    List<MagikDefinition> definitions = response.getDefinitions();

    final AstNode topNode = magikFile.getTopNode();
    AstNode scopeNode =
        AstQuery.nodeSurrounding(topNode, Lsp4jConversion.positionFromLsp4j(position));

    if (scopeNode == null) {
      return completionItems;
    }

    final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();
    AstNode methodDefinitionNode = scopeNode;
    if (scopeNode.isNot(MagikGrammar.METHOD_DEFINITION)) {
      methodDefinitionNode = scopeNode.getFirstAncestor(MagikGrammar.METHOD_DEFINITION);
    }
    if (methodDefinitionNode != null) {
      final MethodDefinitionNodeHelper helper =
          new MethodDefinitionNodeHelper(methodDefinitionNode);
      final TypeString typeString = helper.getTypeString();
      definitionKeeper
          .getExemplarDefinitions(typeString)
          .forEach(
              exemplarDef -> {
                List<SlotDefinition> slots =
                    exemplarDef.getSlots().stream()
                        .filter(slot -> slot.getName().contains(tokenValue))
                        .toList();
                for (int i = 0; i < slots.size(); i++) {
                  SlotDefinition slot = slots.get(i);
                  final String slotName = slot.getName();

                  final String fullSlotName = typeString.getFullString() + "." + slot.getName();
                  final CompletionItem item = new CompletionItem(slotName);
                  item.setInsertText(slotName);
                  item.setDetail(fullSlotName);
                  item.setKind(CompletionItemKind.Property);

                  definitions.add(slot);
                  item.setData(
                      completionHelper.getCompletionData(response.getId(), i, magikFile.getUri()));

                  completionItems.add(item);
                }
              });
    }

    return completionItems;
  }

  /**
   * build the insert text for a method invocation as a snippet
   *
   * @param methodDef the method definition to build
   * @return the string that should be inserted
   */
  private String buildMethodInvocationSnippet(MethodDefinition methodDef) {
    final String originalMethodName = methodDef.getMethodNameWithParameters();
    if (!originalMethodName.endsWith(")")) {
      return originalMethodName;
    }

    final String methodName = methodDef.getMethodNameWithoutParentheses();
    final List<ParameterDefinition> parameters = methodDef.filteredParameters(false, false);

    String insertText = methodName + "(";

    AtomicInteger index = new AtomicInteger(1);
    insertText +=
        parameters.stream()
            .map(
                parameterDefinition ->
                    "${" + index.getAndIncrement() + ":" + parameterDefinition.getName() + "}")
            .collect(Collectors.joining(", "));

    insertText += ")$0";

    return insertText;
  }

  /**
   * Strip the current token at position.
   *
   * @param source Text to strip from.
   * @param position Position to strip.
   * @return Cleared source, removed token.
   */
  private String[] cleanSource(final String source, final Position position) {
    final int lineNo = position.getLine();
    final String[] lines = source.split("\n");
    final String line = lines[lineNo];

    // TODO clean source by first moving to the first space and then removing everything to the
    // left? like a for block?

    // Replace current token.
    // Scan left up to, including: whitespace, MagikOperator, MagikPunctuator
    // Scan right up to, excluding: whitespace, MagikOperator, MagikPunctuator
    final int characterNo =
        position.getCharacter() >= line.length() ? line.length() - 1 : position.getCharacter();
    int beginIndex = characterNo;
    for (; beginIndex >= 0; --beginIndex) {
      final char chr = line.charAt(beginIndex);
      if (CompletionProvider.REMOVAL_STOP_CHARS.contains(chr)) {
        break;
      }
    }
    beginIndex = Math.max(beginIndex, 0);
    int endIndex = characterNo;
    for (; endIndex < line.length(); ++endIndex) {
      final char chr = line.charAt(endIndex);
      if (CompletionProvider.REMOVAL_STOP_CHARS.contains(chr)) {
        ++endIndex;
        break;
      }
    }

    // Clean up by replacing the scanned part with whitespace.
    final String stripped = line.substring(beginIndex, endIndex);
    lines[lineNo] =
        line.substring(0, beginIndex) + " ".repeat(stripped.length()) + line.substring(endIndex);
    return new String[] {String.join("\n", lines), stripped.trim()};
  }

  /**
   * Provide keyword {@link CompletionItem}s.
   *
   * @return {@link CompletionItem}s.
   */
  private List<CompletionItem> provideKeywordCompletions() {
    return Arrays.stream(MagikKeyword.values())
        .map(
            keyword -> {
              final String name = keyword.toString().toLowerCase();
              final CompletionItem item = new CompletionItem(name);
              item.setSortText("**" + item.getLabel());
              item.setFilterText("_" + name);
              item.setKind(CompletionItemKind.Keyword);
              item.setInsertText(keyword.getValue());
              return item;
            })
        .toList();
  }

  /**
   * Get the current character at {@code position} in {@code text}.
   *
   * @param text Text to use.
   * @param position Position to get character from.
   * @return Character at {@code position}.
   */
  @CheckForNull
  private Character getCurrentChar(final String text, final Position position) {
    final int line = position.getLine();
    int character = position.getCharacter();
    final Optional<String> optionalLineStr = text.lines().skip(line).findFirst();
    if (optionalLineStr.isEmpty()) {
      return null;
    }
    final String lineStr = optionalLineStr.get();
    if (character >= lineStr.length()) {
      character = lineStr.length() - 1;
    }
    return lineStr.charAt(character);
  }

  private Map.Entry<MagikTypedFile, String> getUsableMagikFile(
      final MagikTypedFile magikFile, final Position position) {
    MagikTypedFile newMagikFile = magikFile;

    final AstNode node = magikFile.getTopNode();
    final AstNode tokenNode = AstQuery.nodeAt(node, Lsp4jConversion.positionFromLsp4j(position));
    String cleanedToken = "";
    if (tokenNode != null
        && tokenNode.getParent() != null
        && tokenNode.getParent().is(MagikGrammar.SYNTAX_ERROR)) {
      // Clean it up a bit and try to re-parse.
      final String source = magikFile.getSource();
      final String[] items = this.cleanSource(source, position);
      final String cleanedSource = items[0];
      cleanedToken = items[1];
      final URI uri = magikFile.getUri();
      final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();
      newMagikFile = new MagikTypedFile(uri, cleanedSource, definitionKeeper);
    } else if (tokenNode != null && tokenNode.getParent() != null) {
      AstNode parent = tokenNode.getParent();
      if (parent.getParent().is(MagikGrammar.METHOD_INVOCATION)) {
        cleanedToken = ".";
      }
      cleanedToken += tokenNode.getTokenValue();
    }

    return Map.entry(newMagikFile, cleanedToken);
  }

  private boolean shouldPrependPackage(TypeString typeString, String currentPackage) {
    String pakkage = typeString.getPakkage();
    if (currentPackage.equals(pakkage)) {
      return false;
    }

    if (currentPackage.equals("user") && pakkage.equals("sw")) {
      return false;
    }

    return true;
  }
}
