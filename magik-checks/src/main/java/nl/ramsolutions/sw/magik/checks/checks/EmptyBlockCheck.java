package nl.ramsolutions.sw.magik.checks.checks;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nl.ramsolutions.sw.magik.analysis.helpers.MethodDefinitionNodeHelper;
import nl.ramsolutions.sw.magik.api.MagikGrammar;
import nl.ramsolutions.sw.magik.checks.MagikCheck;
import nl.ramsolutions.sw.magik.parser.MagikCommentExtractor;
import org.sonar.check.Rule;

/** Check for empty bodies. */
@Rule(key = EmptyBlockCheck.CHECK_KEY)
public class EmptyBlockCheck extends MagikCheck {

  @SuppressWarnings("checkstyle:JavadocVariable")
  public static final String CHECK_KEY = "EmptyBlock";

  private static final String MESSAGE = "Block is empty.";

  @Override
  protected void walkPreBody(final AstNode node) {
    // Ensure not in abstract method.
    if (this.isAbstractMethodBody(node)) {
      return;
    }

    boolean hasChildren =
        node.getChildren().stream()
            .filter(childNode -> !childNode.getTokenValue().trim().isEmpty())
            .anyMatch(childNode -> true);

    AstNode parentNode = node.getParent();
    List<Token> comments = new ArrayList<>();
    if (!hasChildren && parentNode.is(MagikGrammar.IF)) {
      AstNode nextNode = node.getNextSibling();
      if (nextNode != null) {
        comments = MagikCommentExtractor.extractComments(nextNode).toList();
      }
    }

    if (!hasChildren && parentNode.is(MagikGrammar.WHEN)) {
      // _when statement can be left empty for empty error checks, add configuration?
      hasChildren = true;
    }

    if (!hasChildren && parentNode.is(MagikGrammar.METHOD_DEFINITION)) {
      comments = MagikCommentExtractor.extractComments(parentNode).toList();
    }

    if (!hasChildren && parentNode.is(MagikGrammar.TRY)) {
      Optional<AstNode> tryToken =
          parentNode.getChildren().stream()
              .filter(child -> child.hasToken() && child.getTokenValue().equals("_try"))
              .findFirst();
      if (tryToken.isPresent()) {
        parentNode = tryToken.get();
      }
    }

    if (!hasChildren && comments.isEmpty()) {
      this.addIssue(parentNode, MESSAGE);
    }
  }

  private boolean isAbstractMethodBody(final AstNode node) {
    final AstNode parentNode = node.getParent();
    if (parentNode == null || parentNode.isNot(MagikGrammar.METHOD_DEFINITION)) {
      return false;
    }
    final MethodDefinitionNodeHelper helper = new MethodDefinitionNodeHelper(parentNode);
    return helper.isAbstractMethod();
  }
}
