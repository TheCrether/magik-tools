package nl.ramsolutions.sw.magik.languageserver.documentsymbols;

import com.sonar.sslr.api.AstNode;
import java.util.List;
import java.util.Objects;
import nl.ramsolutions.sw.magik.MagikTypedFile;
import nl.ramsolutions.sw.magik.Range;
import nl.ramsolutions.sw.magik.analysis.definitions.*;
import nl.ramsolutions.sw.magik.languageserver.Lsp4jConversion;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/** Document symbol provider. */
public class DocumentSymbolProvider {

  /**
   * Set server capabilities.
   *
   * @param capabilities Server capabilities.
   */
  public void setCapabilities(final ServerCapabilities capabilities) {
    capabilities.setDocumentSymbolProvider(true);
  }

  /**
   * Provide {@link DocumentSymbol}s.
   *
   * @param magikFile Magik file to provide symbols for.
   * @return {@link DocumentSymbol}s.
   */
  public List<Either<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>> provideDocumentSymbols(
      final MagikTypedFile magikFile) {
    // Convert definitions to DocumentSymbols.
    return magikFile.getMagikDefinitions().stream()
        .map(this::convertDefinition)
        .map(Either::<org.eclipse.lsp4j.SymbolInformation, DocumentSymbol>forRight)
        .toList();
  }

  private DocumentSymbol convertDefinition(final MagikDefinition definition) {
    final SymbolKind symbolKind = this.symbolKindForDefinition(definition);
    final AstNode definitionNode = definition.getNode();
    Objects.requireNonNull(definitionNode);
    final Range range = new Range(definitionNode);
    final DocumentSymbol documentSymbol =
        new DocumentSymbol(
            definition.getName(),
            symbolKind,
            Lsp4jConversion.rangeToLsp4j(range),
            Lsp4jConversion.rangeToLsp4j(range));
    if (definition instanceof ExemplarDefinition exemplarDefinition) {
      final List<DocumentSymbol> slotSymbols =
          this.convertedSlotsFromDefinition(exemplarDefinition);
      documentSymbol.setChildren(slotSymbols);
    }
    return documentSymbol;
  }

  private SymbolKind symbolKindForDefinition(final MagikDefinition definition) {
    if (definition instanceof PackageDefinition) {
      return SymbolKind.Namespace;
    } else if (definition instanceof BinaryOperatorDefinition) {
      return SymbolKind.Operator;
    } else if (definition instanceof GlobalDefinition) {
      return SymbolKind.Variable;
    } else if (definition instanceof ExemplarDefinition) {
      return SymbolKind.Class;
    } else if (definition instanceof ProcedureDefinition) {
      return SymbolKind.Event;
    } else if (definition instanceof MethodDefinition methodDef) {
      if (methodDef.getModifiers().contains(MethodDefinition.Modifier.SLOT)) {
        return SymbolKind.Property;
      }
      if (methodDef.getModifiers().contains(MethodDefinition.Modifier.SHARED_CONSTANT)) {
        return SymbolKind.Constant;
      }
      if (methodDef.getModifiers().contains(MethodDefinition.Modifier.SHARED_VARIABLE)) {
        return SymbolKind.EnumMember;
      }
      if (methodDef.getModifiers().contains(MethodDefinition.Modifier.DB_TYPE)) {
        return SymbolKind.Field;
      }
    }
    return SymbolKind.Method;
  }

  private List<DocumentSymbol> convertedSlotsFromDefinition(
      final ExemplarDefinition exemplarDefinition) {
    return exemplarDefinition.getSlots().stream()
        .map(
            slotDefinition ->
                new DocumentSymbol(
                    slotDefinition.getName(),
                    SymbolKind.Field,
                    Lsp4jConversion.rangeToLsp4j(new Range(slotDefinition.getNode())),
                    Lsp4jConversion.rangeToLsp4j(new Range(slotDefinition.getNode()))))
        .toList();
  }
}
