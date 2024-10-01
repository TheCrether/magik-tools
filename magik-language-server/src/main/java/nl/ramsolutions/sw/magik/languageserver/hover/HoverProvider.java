package nl.ramsolutions.sw.magik.languageserver.hover;

import com.sonar.sslr.api.AstNode;
import java.util.*;
import java.util.stream.Collectors;
import nl.ramsolutions.sw.MagikToolsProperties;
import nl.ramsolutions.sw.magik.MagikTypedFile;
import nl.ramsolutions.sw.magik.Range;
import nl.ramsolutions.sw.magik.analysis.AstQuery;
import nl.ramsolutions.sw.magik.analysis.definitions.*;
import nl.ramsolutions.sw.magik.analysis.helpers.MethodDefinitionNodeHelper;
import nl.ramsolutions.sw.magik.analysis.helpers.MethodInvocationNodeHelper;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.SelfHelper;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeStringResolver;
import nl.ramsolutions.sw.magik.analysis.typing.reasoner.LocalTypeReasonerState;
import nl.ramsolutions.sw.magik.api.MagikGrammar;
import nl.ramsolutions.sw.magik.languageserver.Lsp4jConversion;
import nl.ramsolutions.sw.magik.languageserver.MagikLanguageServerSettings;
import nl.ramsolutions.sw.moduledef.ModuleDefFile;
import nl.ramsolutions.sw.moduledef.ModuleDefinition;
import nl.ramsolutions.sw.moduledef.api.SwModuleDefinitionGrammar;
import nl.ramsolutions.sw.productdef.ProductDefFile;
import nl.ramsolutions.sw.productdef.ProductDefinition;
import nl.ramsolutions.sw.productdef.api.SwProductDefinitionGrammar;
import org.eclipse.lsp4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hover provider. */
public class HoverProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(HoverProvider.class);

  private static final String NBSP_NBSP = "&nbsp;&nbsp;";
  private static final String SECTION_END = "\n\n";
  private static final String BR =
      "  \n"; // will be interpreted as <br /> according to markdown spec
  private static final String SEPARATOR = SECTION_END + "---" + SECTION_END;

  private final MagikToolsProperties properties;

  public HoverProvider(MagikToolsProperties properties) {
    this.properties = properties;
  }

  /**
   * Set server capabilities.
   *
   * @param capabilities Server capabilities.
   */
  public void setCapabilities(final ServerCapabilities capabilities) {
    capabilities.setHoverProvider(true);
  }

  /**
   * Provide a hover at the given position.
   *
   * @param productDefFile Product.def file.
   * @param position Position in file.
   * @return Hover at position.
   */
  @SuppressWarnings("java:S3776")
  public Hover provideHover(final ProductDefFile productDefFile, final Position position) {
    final AstNode node = productDefFile.getTopNode();
    final AstNode hoveredTokenNode =
        AstQuery.nodeAt(node, Lsp4jConversion.positionFromLsp4j(position));
    if (hoveredTokenNode == null) {
      return null;
    }

    final AstNode productNameNode =
        AstQuery.getParentFromChain(
            hoveredTokenNode,
            SwProductDefinitionGrammar.IDENTIFIER,
            SwProductDefinitionGrammar.PRODUCT_NAME);
    if (productNameNode == null) {
      return null;
    }

    final StringBuilder builder = new StringBuilder();
    this.provideHoverProductName(productDefFile, productNameNode, builder);
    if (builder.isEmpty()) {
      return null;
    }

    final String content = builder.toString();
    final MarkupContent contents = new MarkupContent(MarkupKind.MARKDOWN, content);
    final Range range = new Range(hoveredTokenNode);
    final org.eclipse.lsp4j.Range rangeLsp4j = Lsp4jConversion.rangeToLsp4j(range);
    return new Hover(contents, rangeLsp4j);
  }

  /**
   * Provide a hover at the given position.
   *
   * @param moduleDefFile Module.def file.
   * @param position Position in file.
   * @return Hover at position.
   */
  @SuppressWarnings("java:S3776")
  public Hover provideHover(final ModuleDefFile moduleDefFile, final Position position) {
    final AstNode node = moduleDefFile.getTopNode();
    final AstNode hoveredTokenNode =
        AstQuery.nodeAt(node, Lsp4jConversion.positionFromLsp4j(position));
    if (hoveredTokenNode == null) {
      return null;
    }

    final AstNode moduleNameNode =
        AstQuery.getParentFromChain(
            hoveredTokenNode,
            SwModuleDefinitionGrammar.IDENTIFIER,
            SwModuleDefinitionGrammar.MODULE_NAME);
    if (moduleNameNode == null) {
      return null;
    }

    final StringBuilder builder = new StringBuilder();
    this.provideHoverModuleName(moduleDefFile, moduleNameNode, builder);
    if (builder.isEmpty()) {
      return null;
    }

    final String content = builder.toString();
    final MarkupContent contents = new MarkupContent(MarkupKind.MARKDOWN, content);
    final Range range = new Range(hoveredTokenNode);
    final org.eclipse.lsp4j.Range rangeLsp4j = Lsp4jConversion.rangeToLsp4j(range);
    return new Hover(contents, rangeLsp4j);
  }

  /**
   * Provide a hover at the given position.
   *
   * @param magikFile Magik file.
   * @param position Position in file.
   * @return Hover at position.
   */
  @SuppressWarnings("java:S3776")
  public Hover provideHover(final MagikTypedFile magikFile, final Position position) {
    // Parse and reason magik.
    final AstNode node = magikFile.getTopNode();
    final AstNode hoveredTokenNode =
        AstQuery.nodeAt(node, Lsp4jConversion.positionFromLsp4j(position));
    if (hoveredTokenNode == null) {
      return null;
    }

    final AstNode hoveredNode = hoveredTokenNode.getParent();
    LOGGER.debug("Hovering on: {}", hoveredNode.getTokenValue());

    // See what we should provide a hover for.
    final AstNode parentNode = hoveredNode.getParent();
    final AstNode parentParentNode = parentNode != null ? parentNode.getParent() : null;
    final AstNode parentNextSibling = parentNode != null ? parentNode.getNextSibling() : null;
    final StringBuilder builder = new StringBuilder();
    if (hoveredNode.is(MagikGrammar.PACKAGE_IDENTIFIER)) {
      this.provideHoverPackage(magikFile, hoveredNode, builder);
    } else if (parentNode != null) {
      if (parentNode.is(MagikGrammar.EXEMPLAR_NAME)) {
        this.provideHoverAtom(magikFile, parentNode, builder);
      } else if (parentNode.is(MagikGrammar.METHOD_NAME)) {
        this.provideHoverMethodDefinition(magikFile, hoveredNode, builder);
      } else if (hoveredNode.is(MagikGrammar.IDENTIFIER)
          && parentNextSibling != null
          && parentNextSibling.is(MagikGrammar.PROCEDURE_INVOCATION)) {
        this.provideHoverProcedureInvocation(magikFile, hoveredNode, builder);
      } else if (hoveredNode.is(MagikGrammar.IDENTIFIER)
          && parentNode.is(MagikGrammar.METHOD_INVOCATION)) {
        this.provideHoverMethodInvocation(magikFile, hoveredNode, builder);
      } else if (parentNode.is(MagikGrammar.ATOM) || parentNode.is(MagikGrammar.SLOT)) {
        final AstNode atomNode = hoveredNode.getFirstAncestor(MagikGrammar.ATOM);
        this.provideHoverAtom(magikFile, atomNode, builder);
      } else if (parentNode.is(MagikGrammar.PARAMETER)) {
        final AstNode parameterNode = hoveredNode.getParent();
        this.provideHoverAtom(magikFile, parameterNode, builder);
      } else if (parentNode.is(MagikGrammar.VARIABLE_DEFINITION)) {
        this.provideHoverAtom(magikFile, hoveredNode, builder);
      } else if (parentParentNode != null && parentParentNode.is(MagikGrammar.FOR_VARIABLES)) {
        this.provideHoverAtom(magikFile, hoveredNode, builder);
      } else if (parentNode.is(MagikGrammar.EXPRESSION)) {
        final AstNode expressionNode = hoveredNode.getParent();
        this.provideHoverExpression(magikFile, expressionNode, builder);
      } else if (parentNode.is(MagikGrammar.CONDITION_NAME)) {
        this.provideHoverCondition(magikFile, hoveredNode, builder);
      }
    }

    if (builder.isEmpty()) {
      return null;
    }

    final String content = builder.toString();
    final MarkupContent contents = new MarkupContent(MarkupKind.MARKDOWN, content);
    final Range range = new Range(hoveredTokenNode);
    final org.eclipse.lsp4j.Range rangeLsp4j = Lsp4jConversion.rangeToLsp4j(range);
    return new Hover(contents, rangeLsp4j);
  }

  private void provideHoverPackage(
      final MagikTypedFile magikFile, final AstNode hoveredNode, final StringBuilder builder) {
    final String packageName = hoveredNode.getTokenValue();
    final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();
    definitionKeeper.getPackageDefinitions(packageName).stream()
        .forEach(
            pakkageDef -> {
              // Name.
              appendCodeBlock(builder, false, true, pakkageDef.getName());

              // Doc.
              appendDoc(builder, pakkageDef);

              // Uses.
              this.addPackageHierarchy(magikFile, pakkageDef, builder, 0);
            });
  }

  private void addPackageHierarchy(
      final MagikTypedFile magikFile,
      final PackageDefinition pakkageDef,
      final StringBuilder builder,
      final int indent) {
    if (indent == 0) {
      builder.append(pakkageDef.getName()).append(BR);
    }

    final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();
    final String indentStr = NBSP_NBSP.repeat(indent);
    pakkageDef.getUses().stream()
        .sorted()
        .forEach(
            use -> {
              builder.append(indentStr).append(" ↳ ").append(use).append(BR);

              definitionKeeper
                  .getPackageDefinitions(use)
                  .forEach(
                      usePakkageDef ->
                          this.addPackageHierarchy(magikFile, usePakkageDef, builder, indent + 1));
            });

    if (indent == 0) {
      builder.append(SECTION_END);
    }
  }

  private void provideHoverCondition(
      final MagikTypedFile magikFile, final AstNode hoveredNode, final StringBuilder builder) {
    final String conditionName = hoveredNode.getTokenValue();
    final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();
    definitionKeeper
        .getConditionDefinitions(conditionName)
        .forEach(
            conditionDef -> {
              // Name.
              appendCodeBlock(builder, false, true, conditionDef.getName());

              // Doc.
              appendDoc(builder, conditionDef);

              // Taxonomy.
              builder.append("### Taxonomy:\n\n");
              this.addConditionTaxonomy(magikFile, conditionDef, builder, 0);
              builder.append(SECTION_END);

              // Data names.
              builder.append("### Data:\n");
              conditionDef
                  .getDataNames()
                  .forEach(dataName -> builder.append("* ").append(dataName).append("\n"));
            });
  }

  private void addConditionTaxonomy(
      final MagikTypedFile magikFile,
      final ConditionDefinition conditionDefinition,
      final StringBuilder builder,
      final int indent) {
    if (indent == 0) {
      builder.append(conditionDefinition.getName()).append(BR);
    }

    final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();
    final String indentStr = NBSP_NBSP.repeat(indent);
    final String parentConditionName = conditionDefinition.getParent();
    if (parentConditionName != null) {
      builder.append(indentStr).append(" ↳ ").append(parentConditionName).append(BR);

      definitionKeeper
          .getConditionDefinitions()
          .forEach(
              parentConditionDef ->
                  this.addConditionTaxonomy(magikFile, parentConditionDef, builder, indent + 1));
    }

    if (indent == 0) {
      builder.append(SECTION_END);
    }
  }

  private void provideHoverExpression(
      final MagikTypedFile magikFile, final AstNode expressionNode, final StringBuilder builder) {
    final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
    final ExpressionResultString result = reasonerState.getNodeTypeSilent(expressionNode);
    if (result != null) {
      LOGGER.debug("Providing hover for node: {}", expressionNode.getTokenValue()); // NOSONAR
      this.buildTypeDoc(magikFile, expressionNode, builder);
    }
  }

  /**
   * Provide hover for an atom.
   *
   * @param magikFile Magik file.
   * @param atomNode Atom node hovered on.
   * @param builder Builder to add text to.
   */
  private void provideHoverAtom(
      final MagikTypedFile magikFile, final AstNode atomNode, final StringBuilder builder) {
    final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
    final ExpressionResultString result = reasonerState.getNodeTypeSilent(atomNode);
    if (result != null) {
      LOGGER.debug("Providing hover for node: {}", atomNode.getTokenValue()); // NOSONAR
      this.buildTypeDoc(magikFile, atomNode, builder);
    }
  }

  /**
   * Provide hover for a procedure invocation.
   *
   * @param magikFile Magik file.
   * @param hoveredNode Hovered node.
   * @param builder Builder to add text to.
   */
  private void provideHoverProcedureInvocation(
      final MagikTypedFile magikFile, final AstNode hoveredNode, final StringBuilder builder) {
    final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
    final AstNode providingNode = hoveredNode.getParent();
    if (providingNode != null) {
      final ExpressionResultString result = reasonerState.getNodeType(providingNode);
      final TypeString typeStr = result.get(0, null);
      if (typeStr != null) {
        LOGGER.debug("Providing hover for node: {}", providingNode.getTokenValue()); // NOSONAR
        this.buildProcDoc(magikFile, providingNode, builder);
      }
    }
  }

  /**
   * Provide hover for a method invocation.
   *
   * @param magikFile Magik file.
   * @param hoveredNode Hovered node.
   * @param builder Builder to add text to.
   */
  private void provideHoverMethodInvocation(
      final MagikTypedFile magikFile, final AstNode hoveredNode, final StringBuilder builder) {
    final AstNode providingNode = hoveredNode.getParent();
    final AstNode previousSiblingNode = providingNode.getPreviousSibling();
    if (previousSiblingNode != null) {
      final MethodInvocationNodeHelper invocationHelper =
          new MethodInvocationNodeHelper(providingNode);
      final String methodName = invocationHelper.getMethodName();
      LOGGER.debug(
          "Providing hover for node: {}, method: {}",
          previousSiblingNode.getTokenValue(),
          methodName);
      if (methodName != null) {
        final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
        final ExpressionResultString result = reasonerState.getNodeType(previousSiblingNode);
        final TypeString resultTypeStr = result.get(0, TypeString.UNDEFINED);
        final TypeString typeStr = SelfHelper.substituteSelf(resultTypeStr, hoveredNode);

        final TypeStringResolver resolver = magikFile.getTypeStringResolver();

        resolver
            .tryToGetOneMethodDefinition(typeStr, methodName)
            // could be found, use it
            .forEach(methodDef -> buildMethodSignatureDoc(methodDef, builder, this.properties));
      }
    }
  }

  /**
   * Provide hover for a method definition.
   *
   * @param magikFile Magik file.
   * @param hoveredNode Hovered node.
   * @param builder Builder to add text to.
   */
  private void provideHoverMethodDefinition(
      final MagikTypedFile magikFile, final AstNode hoveredNode, final StringBuilder builder) {
    final AstNode methodDefNode = hoveredNode.getFirstAncestor(MagikGrammar.METHOD_DEFINITION);
    final AstNode exemplarNameNode = methodDefNode.getFirstChild(MagikGrammar.EXEMPLAR_NAME);
    final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
    final ExpressionResultString result = reasonerState.getNodeType(exemplarNameNode);
    final TypeString resultTypeStr = result.get(0, TypeString.UNDEFINED);
    final TypeString typeStr = SelfHelper.substituteSelf(resultTypeStr, hoveredNode);

    final MethodDefinitionNodeHelper methodDefHelper =
        new MethodDefinitionNodeHelper(methodDefNode);
    final String methodName = methodDefHelper.getMethodName();
    final TypeStringResolver resolver = magikFile.getTypeStringResolver();
    resolver
        .tryToGetOneMethodDefinition(typeStr, methodName)
        .forEach(methodDef -> buildMethodSignatureDoc(methodDef, builder, this.properties));
  }

  /**
   * Build hover text for type doc.
   *
   * @param magikFile Magik file.
   * @param node {@link AstNode} to get info from.
   * @param builder {@link StringBuilder} to fill.
   */
  private void buildTypeDoc(
      final MagikTypedFile magikFile, final AstNode node, final StringBuilder builder) {
    final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
    final ExpressionResultString result = reasonerState.getNodeType(node);
    final TypeString resultTypeStr = result.get(0, TypeString.UNDEFINED);
    final TypeString typeStr = SelfHelper.substituteSelf(resultTypeStr, node);

    final TypeStringResolver resolver = magikFile.getTypeStringResolver();
    resolver.resolve(typeStr).stream()
        .filter(ExemplarDefinition.class::isInstance)
        .map(ExemplarDefinition.class::cast)
        .forEach(
            exemplarDef -> buildTypeSignatureDoc(magikFile, exemplarDef, builder, this.properties));
  }

  private void buildProcDoc(
      final MagikTypedFile magikFile, final AstNode node, final StringBuilder builder) {
    final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
    final ExpressionResultString result = reasonerState.getNodeType(node);
    final TypeString typeStr = result.get(0, TypeString.UNDEFINED);
    final TypeStringResolver resolver = magikFile.getTypeStringResolver();
    final Collection<ITypeStringDefinition> typeDefs = resolver.resolve(typeStr);
    final ITypeStringDefinition typeDef = typeDefs.isEmpty() ? null : typeDefs.iterator().next();
    if (typeDef instanceof ProcedureDefinition procedureDefinition) {
      this.buildProcSignatureDoc(procedureDefinition, builder);
    }
  }

  private static void addSuperDoc(
      final MagikTypedFile magikFile,
      final ExemplarDefinition exemplarDef,
      final StringBuilder builder,
      final int indent) {
    final TypeString typeStr = exemplarDef.getTypeString();
    if (indent == 0) {
      builder.append(formatTypeString(typeStr)).append(BR);
    }

    final IDefinitionKeeper definitionKeeper = magikFile.getDefinitionKeeper();
    final String indentStr = NBSP_NBSP.repeat(indent);
    final TypeStringResolver resolver = magikFile.getTypeStringResolver();
    resolver
        .getParents(typeStr)
        .forEach(
            parentTypeStr -> {
              builder
                  .append(indentStr)
                  .append(" ↳ ")
                  .append(formatTypeString(parentTypeStr))
                  .append(BR);

              definitionKeeper
                  .getExemplarDefinitions(parentTypeStr)
                  .forEach(
                      parentExemplarDef ->
                          addSuperDoc(magikFile, parentExemplarDef, builder, indent + 1));
            });
  }

  public static void buildMethodSignatureDoc(
      final MethodDefinition methodDef,
      final StringBuilder builder,
      MagikToolsProperties properties) {
    appendCodeBlock(builder, true, false, "_method " + methodDef.getNameWithParameters());

    // return type
    final String callResultString = methodDef.getReturnTypes().getTypeNames(", ");
    appendResultType(builder, callResultString);

    // iterable
    if (methodDef.getModifiers().contains(MethodDefinition.Modifier.ITER)) {
      builder.append("⟳ Iterable ");
      final String iterResultString = methodDef.getLoopTypes().getTypeNames(", ");
      builder.append(formatTypeString(iterResultString));

      builder.append(SECTION_END);
    }

    builder.append(SEPARATOR);

    // parameters
    if (!methodDef.getParameters().isEmpty()) {
      builder.append("### Parameters:");
      for (final ParameterDefinition parameter : methodDef.getParameters()) {
        // TODO add definitionKeeper instance variable to HoverProvider for linking to types
        builder
            .append("\n  - **")
            .append(parameter.getName())
            .append("** *")
            .append(formatTypeString(parameter.getTypeName()))
            .append("*");
        if (parameter.getDoc() != null && !parameter.getDoc().isEmpty()) {
          builder.append(" – ").append(parameter.getDoc());
        }
      }

      builder.append(SECTION_END);
    }

    // Method doc.
    appendDoc(builder, methodDef);

    builder.append(SEPARATOR);

    // Method module.
    appendModuleName(builder, methodDef);

    // Method topics.
    final String topics = String.join(", ", methodDef.getTopics());
    appendTopics(builder, topics, properties);

    builder.append(SECTION_END);
  }

  private void buildProcSignatureDoc(
      final ProcedureDefinition procDef, final StringBuilder builder) {
    final TypeString typeStr = procDef.getTypeString();

    final String aliasName = typeStr.isAnonymous() ? "proc" : formatTypeString(typeStr);

    // proc name
    final String joiner = procDef.getName().startsWith("[") ? "" : ".";
    appendCodeBlock(builder, true, false, aliasName + joiner + procDef.getNameWithParameters());

    // return type
    final String callResultString = procDef.getReturnTypes().getTypeNames(", ");
    appendResultType(builder, callResultString);

    if (procDef.getModifiers().contains(ProcedureDefinition.Modifier.ITER)) {
      builder.append("⟳ Iterable ");
      final String iterResultString = procDef.getLoopTypes().getTypeNames(", ");
      builder.append(formatTypeString(iterResultString));
      builder.append(SECTION_END);
    }

    // Procedure module.
    appendModuleName(builder, procDef, true);

    // Procedure topics.
    final String topics = String.join(", ", procDef.getTopics());
    appendTopics(builder, topics, this.properties);

    // Procedure doc.
    appendDoc(builder, procDef);
  }

  public static void buildTypeSignatureDoc(
      final MagikTypedFile magikFile,
      final ExemplarDefinition exemplarDef,
      final StringBuilder builder,
      final MagikToolsProperties properties) {

    // type name
    final TypeString typeStr = exemplarDef.getTypeString();
    appendCodeBlock(builder, true, false, formatTypeString(typeStr));

    builder.append(SEPARATOR);

    final Collection<SlotDefinition> slots = exemplarDef.getSlots();
    if (!slots.isEmpty()) {
      builder.append("### Slots\n");
      slots.stream()
          .sorted(Comparator.comparing(SlotDefinition::getName))
          .forEach(
              slot -> {
                final TypeString slotType = slot.getTypeName();
                builder
                    .append("- ")
                    .append(slot.getName())
                    .append(": ")
                    .append(formatTypeString(slotType))
                    .append("\n");
              });
      builder.append(SEPARATOR);
    }

    appendDoc(builder, exemplarDef);

    appendModuleName(builder, exemplarDef);

    final String topics = String.join(", ", exemplarDef.getTopics());
    appendTopics(builder, topics, properties);

    builder.append(SECTION_END);

    final List<TypeString> generics = exemplarDef.getTypeString().getGenerics();
    if (!generics.isEmpty()) {
      builder.append("### Generic definitions\n");
      generics.forEach(
          genericTypeStr ->
              builder.append("* ").append(formatTypeString(genericTypeStr)).append("\n"));
      builder.append(SECTION_END);
    }

    final TypeStringResolver resolver = magikFile.getTypeStringResolver();
    if (!resolver.getParents(typeStr).isEmpty()) {
      builder.append("### Supers\n");
      addSuperDoc(magikFile, exemplarDef, builder, 0);
      builder.append(SECTION_END);
    }
  }

  private void provideHoverProductName(
      final ProductDefFile productDefFile, final AstNode node, final StringBuilder builder) {
    final IDefinitionKeeper definitionKeeper = productDefFile.getDefinitionKeeper();
    final String productName = node.getTokenValue().toLowerCase();
    definitionKeeper
        .getProductDefinitions(productName)
        .forEach(productDef -> this.buildProductDefDoc(productDef, builder));
  }

  private void buildProductDefDoc(final ProductDefinition productDef, final StringBuilder builder) {
    final String productName = productDef.getName();
    builder.append("## ").append(productName);

    final String title = productDef.getTitle();
    if (title != null) {
      final String titleMd = title.lines().map(String::trim).collect(Collectors.joining("\n\n"));
      builder.append("\n").append(titleMd);
    }
    builder.append(SECTION_END);

    final String version = Objects.requireNonNullElse(productDef.getVersion(), "");
    final String versionComment = Objects.requireNonNullElse(productDef.getVersionComment(), "");
    builder
        .append("Version: ")
        .append(version)
        .append(" ")
        .append(versionComment)
        .append(SECTION_END);

    final String description = productDef.getDescription();
    if (description != null) {
      builder.append("### Description").append("\n");
      final String typeDocMd =
          description
              .lines()
              .map(line -> line.trim().length() > 0 ? "*" + line.trim() + "*" : "")
              .collect(Collectors.joining(BR));
      builder.append(typeDocMd).append(SECTION_END);
    }
  }

  private void provideHoverModuleName(
      final ModuleDefFile moduleDefFile, final AstNode node, final StringBuilder builder) {
    final IDefinitionKeeper definitionKeeper = moduleDefFile.getDefinitionKeeper();
    final String moduleName = node.getTokenValue().toLowerCase();
    definitionKeeper
        .getModuleDefinitions(moduleName)
        .forEach(moduleDef -> this.buildModuleDefDoc(moduleDef, builder));
  }

  private void buildModuleDefDoc(final ModuleDefinition moduleDef, final StringBuilder builder) {
    final String moduleName = moduleDef.getName();
    appendCodeBlock(builder, false, true, moduleName);

    final String baseVersion = moduleDef.getBaseVersion();
    final String currentVersion = Objects.requireNonNullElse(moduleDef.getCurrentVersion(), "");
    builder
        .append("Version: ")
        .append(baseVersion)
        .append(" ")
        .append(currentVersion)
        .append(SECTION_END);

    final String description = moduleDef.getDescription();
    if (description != null) {
      builder.append("### Description").append("\n");
      final String typeDocMd =
          description
              .lines()
              .map(line -> line.trim().length() > 0 ? "*" + line.trim() + "*" : "")
              .collect(Collectors.joining(BR));
      builder.append(typeDocMd).append(SECTION_END);
    }
  }

  /**
   * append the module name of a definition, but not as a section end
   *
   * @param builder the string builder
   * @param def the definition
   */
  public static void appendModuleName(StringBuilder builder, MagikDefinition def) {
    appendModuleName(builder, def, false);
  }

  /**
   * append the module name of a definition
   *
   * @param builder the string builder
   * @param def the definition
   * @param end if the module name is a section end, if true -> add {@link
   *     HoverProvider#SECTION_END}
   */
  public static void appendModuleName(StringBuilder builder, MagikDefinition def, Boolean end) {
    final String moduleName = def.getModuleName();
    if (moduleName != null) {
      builder.append("Module: ").append(moduleName).append(end ? SECTION_END : BR);
    }
  }

  /**
   * append documentation of a definition in a consistent manner
   *
   * @param builder the string builder
   * @param def the definition
   */
  public static void appendDoc(StringBuilder builder, MagikDefinition def) {
    final String typeDoc = def.getDoc();
    if (typeDoc != null) {
      final String typeDocMd =
          typeDoc
              .lines()
              //              .map(line -> line.trim().length() > 0 ? "*" + line.trim() + "*" : "")
              //              .map(String::trim)
              .collect(Collectors.joining(BR));
      builder.append(typeDocMd).append(SECTION_END);
    }
  }

  /**
   * appends a Markdown code block
   *
   * @param builder the string builder
   * @param isMagik if the code block is a magik code block
   * @param end if the code block is a section end, if true -> add {@link HoverProvider#SECTION_END}
   *     after the code block
   * @param code the lines of code to be added
   */
  public static void appendCodeBlock(
      StringBuilder builder, boolean isMagik, boolean end, String... code) {
    builder
        .append("```")
        .append(isMagik ? "magik" : "")
        .append("\n")
        .append(String.join("\n", code))
        .append("\n```")
        .append(end ? SECTION_END : BR);
  }

  /**
   * append a result text to string builder and add {@link HoverProvider#SECTION_END}
   *
   * @param builder the string builder
   * @param typeString the type string which should be used
   */
  private static void appendResultType(StringBuilder builder, String typeString) {
    builder.append("→ ");
    builder.append(typeString.trim().isEmpty() ? "_unset" : formatTypeString(typeString.trim()));

    builder.append(SECTION_END);
  }

  public static void appendTopics(
      StringBuilder builder, String topicsStr, MagikToolsProperties properties) {
    final MagikLanguageServerSettings settings = new MagikLanguageServerSettings(properties);
    if (topicsStr.trim().length() > 0 && settings.getShowTopicsOnHover()) {
      builder.append("Topics: ").append(topicsStr);
    }
  }

  /**
   * format a type string: replace <> with []
   *
   * @param typeStr the type string to format
   * @return formatted type string
   */
  public static String formatTypeString(final TypeString typeStr) {
    return formatTypeString(typeStr.getFullString());
  }

  /**
   * format a type string: replace <> with []
   *
   * @param typeStr the type string to format
   * @return formatted type string
   */
  public static String formatTypeString(final String typeStr) {
    return typeStr.replace("<", "[").replace(">", "]");
  }
}
