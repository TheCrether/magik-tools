package nl.ramsolutions.sw.magik.languageserver.typehierarchy;

import com.sonar.sslr.api.AstNode;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import nl.ramsolutions.sw.magik.Location;
import nl.ramsolutions.sw.magik.MagikTypedFile;
import nl.ramsolutions.sw.magik.Range;
import nl.ramsolutions.sw.magik.analysis.AstQuery;
import nl.ramsolutions.sw.magik.analysis.definitions.ExemplarDefinition;
import nl.ramsolutions.sw.magik.analysis.definitions.IDefinitionKeeper;
import nl.ramsolutions.sw.magik.analysis.helpers.MethodDefinitionNodeHelper;
import nl.ramsolutions.sw.magik.analysis.typing.ExpressionResultString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeString;
import nl.ramsolutions.sw.magik.analysis.typing.TypeStringResolver;
import nl.ramsolutions.sw.magik.analysis.typing.reasoner.LocalTypeReasonerState;
import nl.ramsolutions.sw.magik.api.MagikGrammar;
import nl.ramsolutions.sw.magik.languageserver.Lsp4jConversion;
import nl.ramsolutions.sw.magik.languageserver.MagikSettings;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Type Hierarchy Provider. */
public class TypeHierarchyProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(TypeHierarchyProvider.class);

  private final IDefinitionKeeper definitionKeeper;

  /**
   * Constructor.
   *
   * @param definitionKeeper {@link IDefinitionKeeper}.
   */
  public TypeHierarchyProvider(final IDefinitionKeeper definitionKeeper) {
    this.definitionKeeper = definitionKeeper;
  }

  /**
   * Set capabilities.
   *
   * @param capabilities
   */
  public void setCapabilities(final ServerCapabilities capabilities) {
    capabilities.setTypeHierarchyProvider(true);
  }

  /**
   * Prepare type hierarchy items.
   *
   * @param magikFile File to work on.
   * @param position Position in file.
   * @return List of type hierarchy items.
   */
  public List<TypeHierarchyItem> prepareTypeHierarchy(
      final MagikTypedFile magikFile, final Position position) {
    LOGGER.info("prepareTypeHierarchy: {}", magikFile);
    // Parse magik.
    final AstNode topNode = magikFile.getTopNode();

    // Should always be on an identifier.
    final AstNode tokenNode =
        AstQuery.nodeAt(
            topNode, Lsp4jConversion.positionFromLsp4j(position), MagikGrammar.IDENTIFIER);
    if (tokenNode == null) {
      return null; // NOSONAR: LSP requires null.
    }

    // Ensure it is on a class identifier, or a variable.
    final TypeStringResolver resolver = new TypeStringResolver(this.definitionKeeper);
    final AstNode methodDefinitionNode =
        AstQuery.getParentFromChain(
            tokenNode,
            MagikGrammar.IDENTIFIER,
            MagikGrammar.EXEMPLAR_NAME,
            MagikGrammar.METHOD_DEFINITION);
    final AstNode atomNode =
        AstQuery.getParentFromChain(tokenNode, MagikGrammar.IDENTIFIER, MagikGrammar.ATOM);
    if (methodDefinitionNode != null) {
      final MethodDefinitionNodeHelper methodDefinitionNodeHelper =
          new MethodDefinitionNodeHelper(methodDefinitionNode);
      final TypeString typeStr = methodDefinitionNodeHelper.getTypeString();
      final ExemplarDefinition definition = resolver.getExemplarDefinition(typeStr);
      if (definition == null) {
        return null; // NOSONAR: LSP requires null.
      }

      final TypeHierarchyItem item = this.toTypeHierarchyItem(definition);
      return List.of(item);
    } else if (atomNode != null) {
      // Get type from node.
      final LocalTypeReasonerState reasonerState = magikFile.getTypeReasonerState();
      final ExpressionResultString result = reasonerState.getNodeType(atomNode);
      final TypeString typeStr = result.get(0, null);
      if (typeStr != null && !typeStr.isUndefined()) {
        final ExemplarDefinition definition = resolver.getExemplarDefinition(typeStr);
        Objects.requireNonNull(definition);
        final TypeHierarchyItem item = this.toTypeHierarchyItem(definition);
        return List.of(item);
      }
    }

    return null; // NOSONAR: LSP requires null.
  }

  /**
   * Get sub types.
   *
   * @param item Item to get sub types for.
   * @return List of sub types.
   */
  @CheckForNull
  public List<TypeHierarchyItem> typeHierarchySubtypes(final TypeHierarchyItem item) {
    final TypeStringResolver resolver = new TypeStringResolver(this.definitionKeeper);
    final String itemName = item.getName();
    final TypeString typeString = TypeString.ofIdentifier(itemName, "sw");
    final ExemplarDefinition definition = resolver.getExemplarDefinition(typeString);
    if (definition == null) {
      return null; // NOSONAR: LSP requires null.
    }

    // Find children.
    final TypeString searchedTypeString = definition.getTypeString();
    final Comparator<TypeHierarchyItem> byName = Comparator.comparing(TypeHierarchyItem::getName);
    return this.definitionKeeper.getExemplarDefinitions().stream()
        .filter(def -> resolver.getParents(def.getTypeString()).contains(searchedTypeString))
        .map(this::toTypeHierarchyItem)
        .sorted(byName)
        .toList();
  }

  /**
   * Get super types.
   *
   * @param item Item to get super types for.
   * @return List of super types.
   */
  @CheckForNull
  public List<TypeHierarchyItem> typeHierarchySupertypes(final TypeHierarchyItem item) {
    final TypeStringResolver resolver = new TypeStringResolver(this.definitionKeeper);
    final String itemName = item.getName();
    final TypeString typeString = TypeString.ofIdentifier(itemName, "sw");
    final ExemplarDefinition definition = resolver.getExemplarDefinition(typeString);
    if (definition == null) {
      return null; // NOSONAR: LSP requires null.
    }

    final Comparator<TypeHierarchyItem> byName = Comparator.comparing(TypeHierarchyItem::getName);
    return resolver.getParents(typeString).stream()
        .map(resolver::getExemplarDefinition)
        .filter(Objects::nonNull)
        .map(this::toTypeHierarchyItem)
        .sorted(byName)
        .toList();
  }

  private TypeHierarchyItem toTypeHierarchyItem(final ExemplarDefinition definition) {
    final TypeString typeStr = definition.getTypeString();
    final Location typeLocation = definition.getLocation();
    final Location location =
        Location.validLocation(typeLocation, MagikSettings.INSTANCE.getPathMappings());
    final Range range = location.getRange();
    Objects.requireNonNull(range);
    return new TypeHierarchyItem(
        typeStr.getFullString(),
        SymbolKind.Class,
        location.getUri().toString(),
        Lsp4jConversion.rangeToLsp4j(range),
        Lsp4jConversion.rangeToLsp4j(range));
  }
}
