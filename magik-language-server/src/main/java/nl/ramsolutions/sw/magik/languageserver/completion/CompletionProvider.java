package nl.ramsolutions.sw.magik.languageserver.completion;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Token;
import com.sonar.sslr.api.Trivia;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import nl.ramsolutions.sw.magik.analysis.scope.ScopeEntry;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.SelfHelper;
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
import nl.ramsolutions.sw.magik.languageserver.semantictokens.MagikSemanticTokenWalker;
import nl.ramsolutions.sw.magik.parser.MagikCommentExtractor;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Completion provider. */
public class CompletionProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(CompletionProvider.class);
  private static final Set<Character> REMOVAL_STOP_CHARS = new HashSet<>();
  private static final String TOPIC_DEPRECATED = "deprecated";
  private static final String TOPIC_RESTRICTED = "restricted";

  private final MagikToolsProperties properties;
  private final CompletionHelper completionHelper;

  private static final Pattern FOR_OVER_PATTERN =
      Pattern.compile("_for\\s+" + MagikGrammar.SIMPLE_IDENTIFIER_REGEXP + "\\s+_over\\s+");
  private static final Pattern TYPE_DOC_PATTERN =
      Pattern.compile("(@(param|slot|return|loop)\\s+\\{)([^}]*)}");

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
    completionOptions.setTriggerCharacters(List.of(".", "=", "{", "}"));
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
      final MagikTypedFile magikFile, final Position position, CancelChecker checker) {
    // Do our best to get a token value, and clean up the source while we're at it.
    final UsableMagikFileRecord usable = this.getUsableMagikFile(magikFile, position);
    final MagikTypedFile newMagikFile = usable.magikFile;
    final String removedPart = usable.cleanedToken;
    final Position cleanedPosition = usable.newPosition;
    final nl.ramsolutions.sw.magik.Position newPosition =
        Lsp4jConversion.positionFromLsp4j(
            new Position(
                cleanedPosition.getLine(), cleanedPosition.getCharacter() - removedPart.length()));
    final AstNode node = newMagikFile.getTopNode();

    final AstNode tokenNodeAt = AstQuery.nodeAt(node, newPosition);
    final AstNode tokenNodeBefore = AstQuery.nodeBefore(node, newPosition);
    final Token tokenBefore = tokenNodeBefore != null ? tokenNodeBefore.getToken() : null;
    final nl.ramsolutions.sw.magik.Position tokenBeforePosition =
        tokenBefore != null ? nl.ramsolutions.sw.magik.Position.fromTokenStart(tokenBefore) : null;
    final int tokenBeforeLine = tokenBeforePosition != null ? tokenBeforePosition.getLine() : -1;
    final AstNode tokenNode =
        tokenNodeAt != null
            ? tokenNodeAt
            : tokenBeforeLine == newPosition.getLine() ? tokenNodeBefore : null;

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Current token: {}", removedPart);
    }

    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    // Keyword completion: '_'.
    if (removedPart.startsWith("_")) {
      LOGGER.debug("Providing keyword completions");
      return this.provideKeywordCompletions();
    }

    CompletionResponse response = new CompletionResponse();
    completionHelper.setUriField(response, newMagikFile.getUri());
    List<CompletionItem> completionItems = new ArrayList<>();

    // Ensure not in comment.
    if (this.inComment(node, newPosition)) {
      List<CompletionItem> items =
          this.provideCommentCompletions(response, newMagikFile, newPosition, checker);
      if (checker.isCanceled()) {
        return Collections.emptyList();
      }

      if (!items.isEmpty()) {
        CompletionResponses.store(response);
      }
      return items;
    }

    AstNode methodInvocationNode = null, methodInvocationOnSlotNode = null;
    if (tokenNode != null) {
      methodInvocationNode =
          AstQuery.getParentFromChain(
              tokenNode,
              MagikGrammar.IDENTIFIER,
              MagikGrammar.METHOD_NAME,
              MagikGrammar.METHOD_INVOCATION);
      methodInvocationOnSlotNode =
          AstQuery.getParentFromChain(
              tokenNode, MagikGrammar.IDENTIFIER, MagikGrammar.SLOT, MagikGrammar.ATOM);
    }

    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    if (tokenNode == null && removedPart.equals(".")
        || tokenNode != null && tokenNode.getTokenOriginalValue().equals(".")) {
      // only '.' or starts with '.' -> slot invocations
      String searchedText = removedPart;
      if (removedPart.equals(".")) {
        searchedText = "";
      }
      completionItems =
          this.provideSlotCompletion(response, newMagikFile, newPosition, searchedText, checker);
    } else if (tokenNode != null) {
      boolean tokenNodeHasType = false;
      final AstNode tokenAtomNode =
          AstQuery.getParentFromChain(tokenNode, MagikGrammar.IDENTIFIER, MagikGrammar.ATOM);
      if (tokenAtomNode != null) {
        final ExpressionResultString nodeType =
            newMagikFile.getTypeReasonerState().getNodeType(tokenAtomNode);
        tokenNodeHasType =
            nodeType != null
                && !nodeType.equals(ExpressionResultString.EMPTY)
                && !nodeType.equals(ExpressionResultString.UNDEFINED)
                && (!nodeType.isEmpty()
                    && !nodeType.get(0, TypeString.UNDEFINED).equals(TypeString.UNDEFINED));
      }
      // Method completion: METHOD_INVOCATION
      if (methodInvocationOnSlotNode != null) {
        AstNode identifier = AstQuery.getParentFromChain(tokenNode, MagikGrammar.IDENTIFIER);
        if (identifier != null) {
          completionItems =
              this.provideMethodInvocationCompletion(
                  response, newMagikFile, identifier, removedPart, checker, cleanedPosition);
        }
      } else if ((tokenNodeHasType || methodInvocationNode != null)
          && (removedPart.startsWith(".") || removedPart.isEmpty())) {
        completionItems =
            this.provideMethodInvocationCompletion(
                response, newMagikFile, tokenNode, removedPart, checker, cleanedPosition);
      } else {
        completionItems =
            this.provideGlobalCompletion(response, newMagikFile, newPosition, tokenNode, checker);
      }
    } else if (!removedPart.equals(":")) {
      completionItems =
          this.provideGlobalCompletion(response, newMagikFile, newPosition, tokenNode, checker);
    }

    if (checker.isCanceled()) {
      return Collections.emptyList();
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
      HoverProvider.buildTypeSignatureDoc(
          exemplarDef.getTypeString(), magikTypedFile, exemplarDef, docBuilder, this.properties);
    } else if (definition instanceof SlotDefinition slotDef) {
      // TODO implement correct documentation for SlotDefinitions here and in HoverProvider
      if (slotDef.getDoc() != null) {
        docBuilder.append(slotDef.getDoc());
      } else {
        docBuilder.append("No documentation found");
      }
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
  private boolean inComment(final AstNode node, final nl.ramsolutions.sw.magik.Position position) {
    return MagikCommentExtractor.extractComments(node)
        .anyMatch(
            token ->
                position.getLine() == token.getLine() && position.getColumn() >= token.getColumn());
  }

  private List<CompletionItem> provideCommentCompletions(
      final CompletionResponse response,
      final MagikTypedFile magikFile,
      final nl.ramsolutions.sw.magik.Position position,
      CancelChecker checker) {
    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    AstNode surroundingNode = AstQuery.nodeSurrounding(magikFile.getTopNode(), position);
    if (surroundingNode == null || checker.isCanceled()) {
      return Collections.emptyList();
    }

    // if method body exists, get it for better performance
    final AstNode body = surroundingNode.getFirstChild(MagikGrammar.BODY);

    // trivia gets assigned to _endmethod if body is empty
    final AstNode endMethodNode = surroundingNode.getLastChild();
    if (checker.isCanceled()) {
      return Collections.emptyList();
    } else if (body != null && body.hasChildren()) {
      surroundingNode = body;
    } else if (body != null && !body.hasChildren()) {
      if (endMethodNode != null && endMethodNode.getTokenValue().equals("_endmethod")) {
        surroundingNode = endMethodNode;
      }
    }

    Optional<Token> commentTokenOpt =
        MagikCommentExtractor.extractComments(surroundingNode)
            .filter(
                token ->
                    position.getLine() == token.getLine()
                        && position.getColumn() >= token.getColumn())
            .findFirst();
    if (commentTokenOpt.isEmpty() && endMethodNode != null && surroundingNode != endMethodNode) {
      surroundingNode = endMethodNode;
      commentTokenOpt =
          MagikCommentExtractor.extractComments(surroundingNode)
              .filter(
                  token ->
                      position.getLine() == token.getLine()
                          && position.getColumn() >= token.getColumn())
              .findFirst();
    }
    if (commentTokenOpt.isEmpty() || checker.isCanceled()) {
      return Collections.emptyList();
    }

    final Token commentToken = commentTokenOpt.get();
    final String comment = commentToken.getOriginalValue();

    final Matcher typeDocMatcher = TYPE_DOC_PATTERN.matcher(comment);
    final Matcher typeMatcher = MagikSemanticTokenWalker.typeRegex.matcher(comment);
    final Matcher iterTypeMatcher = MagikSemanticTokenWalker.iterTypeRegex.matcher(comment);

    String currentType;
    int start, end;

    if (typeDocMatcher.find() && typeDocMatcher.groupCount() == 3) {
      currentType = typeDocMatcher.group(3).trim();
      start = typeDocMatcher.start(3);
      end = typeDocMatcher.end(3);
    } else if (typeMatcher.find()) {
      currentType = typeMatcher.group(1);
      start = typeMatcher.start(1);
      end = typeMatcher.end(1);
    } else if (iterTypeMatcher.find()) {
      currentType = iterTypeMatcher.group(1);
      start = iterTypeMatcher.start(1);
      end = iterTypeMatcher.end(1);
    } else if (comment.trim().endsWith("iter-type:") || comment.trim().endsWith("type:")) {
      currentType = "";
      start = comment.indexOf(':') + 1;
      end = comment.length();
    } else {
      return Collections.emptyList();
    }

    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    final int typeStart = start + commentToken.getColumn();
    final int typeEnd = end + commentToken.getColumn();

    if (!(typeStart <= position.getColumn() && position.getColumn() <= typeEnd)) {
      return Collections.emptyList();
    }

    List<CompletionItem> items =
        this.addExemplarCompletions(response, magikFile, body, checker, currentType);
    items =
        items.stream().filter(i -> i.getLabel().contains(currentType)).collect(Collectors.toList());

    return items;
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
      final nl.ramsolutions.sw.magik.Position position,
      final @Nullable AstNode tokenNode,
      final CancelChecker checker) {
    // Keyword entries.
    final List<CompletionItem> items = new ArrayList<>(this.provideKeywordCompletions());

    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    // Scope entries.
    final AstNode topNode = magikFile.getTopNode();
    AstNode scopeNode = AstQuery.nodeSurrounding(topNode, position);
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
                  return position.isAfterRange(range)
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

    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    items.addAll(addExemplarCompletions(response, magikFile, tokenNode, checker, null));
    return items;
  }

  private List<CompletionItem> addExemplarCompletions(
      final CompletionResponse response,
      final MagikTypedFile magikFile,
      final @Nullable AstNode tokenNode,
      final CancelChecker checker,
      final @Nullable String filter) {
    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    final List<MagikDefinition> definitions = response.getDefinitions();
    final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();
    final List<CompletionItem> items = new ArrayList<>();

    String currentPackage = "sw";
    if (tokenNode != null) {
      PackageNodeHelper helper = new PackageNodeHelper(tokenNode);
      currentPackage = helper.getCurrentPackage();
    }

    final String finalCurrentPackage = currentPackage;

    // Global types.
    final String identifierPart =
        filter != null ? filter : tokenNode != null ? tokenNode.getTokenValue() : "";
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

      Set<String> topics = exemplarDef.getTopics();
      if (topics.contains(TOPIC_DEPRECATED) || topics.contains(TOPIC_RESTRICTED)) {
        item.setTags(List.of(CompletionItemTag.Deprecated));
        item.setSortText("$$" + item.getLabel());
      }

      items.add(item);
    }

    if (checker.isCanceled()) {
      return Collections.emptyList();
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
      final String tokenValue,
      final CancelChecker checker,
      final Position position) {
    // Token -->
    // - parent: any --> parent: ATOM
    // - parent: IDENTIFIER --> parent: METHOD_NAME -> parent: METHOD_INVOCATION --> previous
    // sibling: ATOM
    // - parent: IDENTIFIER --> parent: METHOD_NAME -> parent: METHOD_INVOCATION --> previous
    // sibling: METHOD_INVOCATION
    final AstNode node = tokenNode.getParent();
    final AstNode parentNode = node.getParent();
    final AstNode parentParentNode = parentNode.getParent();
    final AstNode wantedNode;
    if (parentNode != null && parentNode.is(MagikGrammar.ATOM)) {
      // Asking the ATOM node.
      wantedNode = parentNode;
    } else if (parentParentNode != null
        && (parentParentNode.is(MagikGrammar.METHOD_INVOCATION)
            || parentParentNode.is(MagikGrammar.PROCEDURE_INVOCATION))) {
      // Asking the previous invocation.
      wantedNode = parentParentNode.getPreviousSibling();
    } else {
      return Collections.emptyList();
    }

    final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
    final ExpressionResultString result = reasonerState.getNodeType(wantedNode);
    final TypeString typeStrSelf = result.get(0, TypeString.UNDEFINED);

    final TypeStringResolver resolver = magikFile.getTypeStringResolver();

    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    final GlobalScope globalScope = magikFile.getGlobalScope();
    final Scope currentScope = globalScope.getScopeForNode(node);
    ScopeEntry localScopeEntry = null;
    if (currentScope != null) {
      localScopeEntry = currentScope.getScopeEntry(node.getTokenValue());
    }

    final String currentPackage = new PackageNodeHelper(node).getCurrentPackage();
    final boolean isExemplarInvocation =
        (localScopeEntry == null || localScopeEntry.isType(ScopeEntry.Type.GLOBAL))
            && resolver
                .resolve(TypeString.ofIdentifier(node.getTokenValue(), currentPackage))
                .stream()
                .anyMatch(def -> def instanceof ExemplarDefinition);

    final boolean isSelfInvocation = typeStrSelf.isSelf();
    final TypeString typeStr = SelfHelper.substituteSelf(typeStrSelf, wantedNode);

    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Providing method completions for type: {}", typeStr.getFullString());
    }

    final String methodNamePart = tokenValue.startsWith(".") ? tokenValue.substring(1) : tokenValue;
    final TypeString finalTypeStr = typeStr.getWithoutGenerics();
    final List<MagikDefinition> definitions = response.getDefinitions();

    final List<MethodDefinition> filteredMethods =
        new ArrayList<>(
            resolver.getMethodDefinitions(finalTypeStr).stream()
                .filter(methodDef -> methodDef.getMethodName().contains(methodNamePart))
                .filter(
                    methodDef -> {
                      if (!isSelfInvocation) {
                        return !methodDef
                            .getModifiers()
                            .contains(MethodDefinition.Modifier.PRIVATE);
                      }

                      return true;
                    })
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

    if (checker.isCanceled()) {
      return Collections.emptyList();
    }

    final List<CompletionItem> completionItems = new ArrayList<>();
    for (int i = 0; i < filteredMethods.size(); i++) {
      final MethodDefinition methodDef = filteredMethods.get(i);

      final CompletionItem item = new CompletionItem(methodDef.getMethodNameWithParameters());

      item.setInsertTextFormat(InsertTextFormat.Snippet);
      item.setInsertText(this.buildMethodInvocationSnippet(methodDef));
      item.setFilterText(methodDef.getMethodNameWithoutParentheses());

      if (methodDef.getMethodName().startsWith("[")) {
        System.out.println("does");
        org.eclipse.lsp4j.Range dotRange =
            new org.eclipse.lsp4j.Range(
                new Position(position.getLine(), position.getCharacter() - tokenValue.length()),
                position);
        item.setAdditionalTextEdits(List.of(new TextEdit(dotRange, "")));
      }

      Set<MethodDefinition.Modifier> modifiers = methodDef.getModifiers();
      if (modifiers.contains(MethodDefinition.Modifier.DB_TYPE)) {
        item.setKind(CompletionItemKind.Field);
      } else if (modifiers.contains(MethodDefinition.Modifier.SHARED_CONSTANT)) {
        item.setKind(CompletionItemKind.Constant);
      } else if (modifiers.contains(MethodDefinition.Modifier.SLOT)) {
        item.setKind(CompletionItemKind.Property);
      } else if (modifiers.contains(MethodDefinition.Modifier.SHARED_VARIABLE)) {
        item.setKind(CompletionItemKind.EnumMember);
      } else {
        item.setKind(CompletionItemKind.Method);
      }

      TypeString methodExemplarType = methodDef.getTypeName();

      CompletionItemLabelDetails labelDetails = new CompletionItemLabelDetails();
      ExpressionResultString returnTypes = methodDef.getReturnTypes();
      if (!returnTypes.getTypes().stream()
          .allMatch(retTypeStr -> retTypeStr.equals(TypeString.UNDEFINED))) {
        labelDetails.setDescription(returnTypes.getFullString());
      } else {
        labelDetails.setDetail(" on " + methodDef.getTypeName().getFullString());
      }
      item.setLabelDetails(labelDetails);

      String prefix = "$";
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
        item.setSortText(" ".repeat(3) + prefix + item.getLabel());
      } else {
        item.setSortText(prefix.repeat(3) + item.getLabel());
      }

      definitions.add(methodDef);
      item.setData(completionHelper.getCompletionData(response.getId(), i, magikFile.getUri()));

      Set<String> topics = methodDef.getTopics();
      String additionalPrefix = " ";
      if (topics.contains(TOPIC_DEPRECATED)
          || (topics.contains(TOPIC_RESTRICTED) && !isSelfInvocation)) {
        item.setTags(List.of(CompletionItemTag.Deprecated));
        additionalPrefix = "$";
      }
      item.setSortText(additionalPrefix.repeat(3) + item.getSortText());

      completionItems.add(item);
    }
    return completionItems;
  }

  private List<CompletionItem> provideSlotCompletion(
      final CompletionResponse response,
      final MagikTypedFile magikFile,
      final nl.ramsolutions.sw.magik.Position position,
      final String tokenValue,
      final CancelChecker checker) {
    List<CompletionItem> completionItems = new ArrayList<>();
    List<MagikDefinition> definitions = response.getDefinitions();

    final AstNode topNode = magikFile.getTopNode();
    AstNode scopeNode = AstQuery.nodeSurrounding(topNode, position);

    if (scopeNode == null) {
      return completionItems;
    }

    if (checker.isCanceled()) {
      return Collections.emptyList();
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

                  final CompletionItem item = new CompletionItem(slotName);
                  item.setInsertText(slotName);
                  item.setKind(CompletionItemKind.Property);

                  CompletionItemLabelDetails labelDetails = new CompletionItemLabelDetails();
                  TypeString type = slot.getTypeName();
                  if (!type.equals(TypeString.UNDEFINED)) {
                    labelDetails.setDescription(type.getFullString());
                  }
                  item.setLabelDetails(labelDetails);

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

    if (methodDef.getModifiers().contains(MethodDefinition.Modifier.SLOT)) {
      final int lastChevron = originalMethodName.lastIndexOf('<');
      String insertText = originalMethodName.substring(0, lastChevron + 1);
      insertText += " ${1:val}$0";

      return insertText;
    }

    if (originalMethodName.startsWith("[")) {
      final String keyName = originalMethodName.substring(1, originalMethodName.indexOf(']'));
      return "[${1:" + keyName + "}] << ${2:thing}$0";
    }

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

  private record UsableMagikFileRecord(
      MagikTypedFile magikFile, String cleanedToken, Position newPosition) {}

  private UsableMagikFileRecord getUsableMagikFile(
      final MagikTypedFile magikFile, final Position position) {
    nl.ramsolutions.sw.magik.Position nodePosition = Lsp4jConversion.positionFromLsp4j(position);

    final AstNode node = magikFile.getTopNode();
    final AstNode tokenNode = AstQuery.nodeAt(node, nodePosition);

    MagikTypedFile newMagikFile = magikFile;
    String cleanedToken = "";
    Position cleanedPosition = position;

    if (tokenNode != null
        && tokenNode.getParent() != null
        && tokenNode.getParent().is(MagikGrammar.SYNTAX_ERROR)) {
      final String source = magikFile.getSource();
      final URI uri = magikFile.getUri();
      final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();
      final String[] lines = source.split("\n");
      final String currentLine = lines[position.getLine()];
      final Matcher forOverMatcher = FOR_OVER_PATTERN.matcher(currentLine);

      final AstNode errorNode = tokenNode.getParent();
      final Token errorToken = errorNode.getToken();
      final int errorLineNo = errorToken.getLine();
      final String[] errorLines = errorToken.getOriginalValue().split("\n");
      final int erroringLinesBefore = nodePosition.getLine() - errorLineNo;

      int column = nodePosition.getColumn();
      int line = nodePosition.getLine();

      boolean inIfCondition = true;
      for (int i = 0; i < errorLines.length; i++) {
        final String errorLine = errorLines[i];
        final int currentErrorLineNo = errorLineNo + i;
        final int thenIndex = errorLine.indexOf("_then");
        if (thenIndex > -1
            && (currentErrorLineNo < line || (currentErrorLineNo == line && thenIndex < column))) {
          inIfCondition = false;
          break;
        }
      }

      if (errorNode.getParent() != null
          && errorNode.getParent().is(MagikGrammar.IF)
          && inIfCondition) {
        // clean `_if` structure, only works for the condition part at the moment
        final AstNode ifNode = errorNode.getParent();
        int fromIndex = ifNode.getFirstChild().getFromIndex();
        int toIndex = ifNode.getFirstChild().getToIndex();

        final Token ifToken = ifNode.getToken();
        if (ifToken.getLine() == line) {
          column = nodePosition.getColumn() - ifToken.getOriginalValue().length() - 1;
        }

        line -= erroringLinesBefore;

        for (int i = 0; i < erroringLinesBefore; i++) {
          toIndex += errorLines[i].length();
        }

        final String cleanedSource = source.substring(0, fromIndex) + source.substring(toIndex + 1);
        final MagikTypedFile tempMagikFile =
            new MagikTypedFile(uri, cleanedSource, definitionKeeper);

        final Position tempPosition =
            Lsp4jConversion.positionToLsp4j(new nl.ramsolutions.sw.magik.Position(line, column));

        UsableMagikFileRecord newCleaned = this.getUsableMagikFile(tempMagikFile, tempPosition);
        newMagikFile = newCleaned.magikFile;
        cleanedToken = newCleaned.cleanedToken;
        cleanedPosition = tempPosition;
      } else if (errorNode.getParent() != null && errorNode.getParent().is(MagikGrammar.LOOP)) {
        final AstNode loopNode = errorNode.getParent();
        final AstNode forNode =
            AstQuery.getParentFromChain(loopNode, MagikGrammar.OVER, MagikGrammar.FOR);
        final AstNode whileNode = AstQuery.getParentFromChain(loopNode, MagikGrammar.WHILE);
        final Trivia iterTypeToken =
            loopNode.getToken().getTrivia().stream()
                .filter(
                    (trivia ->
                        trivia.isComment()
                            && MagikSemanticTokenWalker.iterTypeRegex
                                .matcher(trivia.getToken().getOriginalValue())
                                .find()))
                .findFirst()
                .orElse(null);

        int fromIndex = -1;
        int endIndex = loopNode.getFirstChild().getToIndex();
        int wrapperLine = -1;
        if (forNode != null) {
          fromIndex = forNode.getFromIndex();
          wrapperLine = forNode.getToken().getLine();
        } else if (whileNode != null) {
          fromIndex = whileNode.getFromIndex();
          wrapperLine = whileNode.getToken().getLine();
        }
        line -= (loopNode.getFirstChild().getToken().getLine() - wrapperLine);

        if (iterTypeToken != null && forNode != null) {
          final AstNode variables = forNode.getFirstChild(MagikGrammar.FOR_VARIABLES);

          if (variables != null) {
            String varDef =
                "_local a << _unset # type: "
                    + iterTypeToken.getToken().getOriginalValue().split(":")[1];
            final String newSource =
                source.substring(0, fromIndex) + varDef + source.substring(endIndex + 1);
            final MagikTypedFile tempMagikFile =
                new MagikTypedFile(uri, newSource, definitionKeeper);

            final Position tempPosition =
                Lsp4jConversion.positionToLsp4j(
                    new nl.ramsolutions.sw.magik.Position(line, column));
            UsableMagikFileRecord newCleaned = this.getUsableMagikFile(tempMagikFile, tempPosition);
            newMagikFile = newCleaned.magikFile;
            cleanedToken = newCleaned.cleanedToken;
            cleanedPosition = tempPosition;
          }
        } else if (fromIndex > -1) {
          final String newSource = source.substring(0, fromIndex) + source.substring(endIndex + 1);
          final MagikTypedFile tempMagikFile = new MagikTypedFile(uri, newSource, definitionKeeper);

          final Position tempPosition =
              Lsp4jConversion.positionToLsp4j(new nl.ramsolutions.sw.magik.Position(line, column));
          UsableMagikFileRecord newCleaned = this.getUsableMagikFile(tempMagikFile, tempPosition);
          newMagikFile = newCleaned.magikFile;
          cleanedToken = newCleaned.cleanedToken;
          cleanedPosition = tempPosition;
        }
      } else if (forOverMatcher.find()) {
        MatchResult toReplace = forOverMatcher.toMatchResult();
        lines[position.getLine()] =
            currentLine.substring(0, toReplace.start()) + currentLine.substring(toReplace.end());

        final String newSource = String.join("\n", lines);
        final MagikTypedFile tempMagikFile = new MagikTypedFile(uri, newSource, definitionKeeper);
        final Position tempPosition =
            new Position(position.getLine(), column - (toReplace.end() - toReplace.start()));

        UsableMagikFileRecord newCleaned = this.getUsableMagikFile(tempMagikFile, tempPosition);
        newMagikFile = newCleaned.magikFile;
        cleanedToken = newCleaned.cleanedToken;
        cleanedPosition = tempPosition;
      } else {
        // Clean it up a bit and try to reparse.
        final String[] items = this.cleanSource(source, position);
        cleanedToken = items[1];
        final String cleanedSource = items[0];
        newMagikFile = new MagikTypedFile(uri, cleanedSource, definitionKeeper);
      }
    } else if (tokenNode != null && tokenNode.getParent() != null) {
      AstNode parent = tokenNode.getParent();
      if (parent.getParent().is(MagikGrammar.METHOD_INVOCATION)) {
        cleanedToken = ".";
      }
      cleanedToken += tokenNode.getTokenValue();
    }

    return new UsableMagikFileRecord(newMagikFile, cleanedToken, cleanedPosition);
  }

  private boolean shouldPrependPackage(TypeString typeString, String currentPackage) {
    String pakkage = typeString.getPakkage();
    if (currentPackage.equals(pakkage)) {
      return false;
    }

    return !currentPackage.equals("user") || !pakkage.equals("sw");
  }
}
