package nl.ramsolutions.sw.magik.formatting;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.GenericTokenType;
import com.sonar.sslr.api.Token;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.ramsolutions.sw.magik.TextEdit;
import nl.ramsolutions.sw.magik.api.MagikGrammar;
import nl.ramsolutions.sw.magik.api.MagikKeyword;
import nl.ramsolutions.sw.magik.api.MagikOperator;
import nl.ramsolutions.sw.magik.api.MagikPunctuator;

/** Standard formatting strategy. */
class MagikFormattingStrategy extends FormattingStrategy {
  private static final Pattern TYPE_DOC_PATTERN =
      Pattern.compile("([^#]*)(##+)\\s*(@(param|slot|return|loop))\\s*(\\{([^}]*)})?\\s*(.*)");
  public static final Pattern INLINE_TYPE_PATTERN =
      Pattern.compile("^#\\s*((iter-)?type):\\s*(.*)$");

  private static final List<String> KEYWORDS = List.of(MagikKeyword.keywordValues());

  // We cannot base indenting purely on AstNodes
  // (BODY/PARAMETERS/ARGUMENTS/SIMPLE_VECTOR/...),
  // as bodies and tokens don't play that well together.
  // Tokens surround the AstNodes, e.g.: '(', pre PARAMETERS, post PARAMETERS,
  // ')', or '_method', '...', pre BODY, ..., post BODY, '# comment',
  // '_endmethod'.
  private static final Set<String> INDENT_INCREASE =
      Set.of(
          // MagikPunctuator.PAREN_L.getValue(),
          MagikPunctuator.BRACE_L.getValue(),
          MagikPunctuator.SQUARE_L.getValue(),
          MagikKeyword.PROC.getValue(),
          MagikKeyword.METHOD.getValue(),
          MagikKeyword.BLOCK.getValue(),
          MagikKeyword.TRY.getValue(),
          MagikKeyword.WHEN.getValue(),
          MagikKeyword.PROTECT.getValue(),
          MagikKeyword.PROTECTION.getValue(),
          MagikKeyword.CATCH.getValue(),
          MagikKeyword.LOCK.getValue(),
          MagikKeyword.THEN.getValue(),
          MagikKeyword.ELSE.getValue(),
          MagikKeyword.LOOP.getValue(),
          MagikKeyword.FINALLY.getValue());

  private static final Set<String> INDENT_DECREASE =
      Set.of(
          // MagikPunctuator.PAREN_R.getValue(),
          MagikPunctuator.BRACE_R.getValue(),
          MagikPunctuator.SQUARE_R.getValue(),
          MagikKeyword.ENDPROC.getValue(),
          MagikKeyword.ENDMETHOD.getValue(),
          MagikKeyword.ENDBLOCK.getValue(),
          MagikKeyword.ENDTRY.getValue(),
          MagikKeyword.WHEN.getValue(),
          MagikKeyword.PROTECTION.getValue(),
          MagikKeyword.ENDPROTECT.getValue(),
          MagikKeyword.ENDCATCH.getValue(),
          MagikKeyword.ENDLOCK.getValue(),
          MagikKeyword.ELSE.getValue(),
          MagikKeyword.ELIF.getValue(),
          MagikKeyword.ENDIF.getValue(),
          MagikKeyword.ENDLOOP.getValue(),
          MagikKeyword.FINALLY.getValue());

  private static final Set<String> SPACED_BRACES_L =
      Set.of(MagikPunctuator.BRACE_L.getValue(), MagikPunctuator.PAREN_L.getValue());
  private static final Set<String> SPACED_BRACES_R =
      Set.of(MagikPunctuator.BRACE_R.getValue(), MagikPunctuator.PAREN_R.getValue());

  private static final Set<String> AUGMENTED_ASSIGNMENT_TOKENS =
      Set.of(
          MagikKeyword.IS.getValue(),
          MagikKeyword.ISNT.getValue(),
          MagikKeyword.ANDIF.getValue(),
          MagikKeyword.AND.getValue(),
          MagikKeyword.ORIF.getValue(),
          MagikKeyword.OR.getValue(),
          MagikKeyword.XOR.getValue(),
          MagikKeyword.DIV.getValue(),
          MagikKeyword.MOD.getValue(),
          MagikKeyword.CF.getValue(),
          MagikOperator.PLUS.getValue(),
          MagikOperator.MINUS.getValue(),
          MagikOperator.STAR.getValue(),
          MagikOperator.DIV.getValue(),
          MagikOperator.EXP.getValue(),
          MagikOperator.EQ.getValue(),
          MagikOperator.NEQ.getValue());

  private int indent;
  private AstNode currentNode;

  MagikFormattingStrategy(final FormattingOptions options) {
    super(options);
  }

  @Override
  List<TextEdit> walkCommentToken(final Token token) {
    return this.walkToken(token);
  }

  @Override
  List<TextEdit> walkEolToken(final Token token) {
    // Don't touch syntax errors.
    if (this.currentNode.is(MagikGrammar.SYNTAX_ERROR)) {
      return Collections.emptyList();
    }

    // Test distance to lastTextToken, only single empty line allowed.
    final int emptyLineCount =
        this.lastTextToken != null ? token.getLine() - this.lastTextToken.getLine() : 0;
    if (emptyLineCount > 1) {
      // Add edit to remove empty line.
      final TextEdit textEdit = this.editNoNewline(token);
      return List.of(textEdit);
    } else if (this.options.isTrimTrailingWhitespace()
        && this.tokenIs(this.lastToken, GenericTokenType.WHITESPACE)) {
      final TextEdit textEdit = this.editToken(this.lastToken, "", "no whitespace after allowed");
      return List.of(textEdit);
    }

    return Collections.emptyList();
  }

  @Override
  List<TextEdit> walkToken(final Token token) {
    this.trackIndentPre(token);

    final boolean isFirstTextToken = this.lastTextToken == null;
    List<TextEdit> textEdits = new ArrayList<>();
    if (isFirstTextToken) {
      // First token, should not contain any pre-whitespace/indenting.
      final TextEdit textEdit = this.editNoWhitespaceBefore(token);
      textEdits.add(textEdit);
    } else {
      final boolean isOnNewline = !token.isOnSameLineThan(this.lastTextToken);
      if (isOnNewline) {
        if (this.requireNewlineBefore(token)) {
          if (this.tokenIs(this.lastToken, GenericTokenType.WHITESPACE)) {
            final TextEdit textEdit = this.editNewlineBefore(this.lastToken);
            textEdits.add(textEdit);
          } else {
            final TextEdit textEdit = this.editNewlineBefore(token);
            textEdits.add(textEdit);
          }
        }

        final TextEdit textEdit = this.ensureIndenting(token);
        textEdits.add(textEdit);
      } else {
        final TextEdit textEdit = this.validateWhitespacingBefore(token);
        textEdits.add(textEdit);
      }
    }

    if (token.getType().equals(GenericTokenType.COMMENT)) {
      textEdits = this.validateComment(token, textEdits);
    }

    this.trackIndentPost(token);
    return textEdits;
  }

  private List<TextEdit> validateComment(Token token, List<TextEdit> textEdits) {
    List<TextEdit> newTextEdits = new ArrayList<>(textEdits);
    for (int i = 0; i < newTextEdits.size(); i++) {
      TextEdit textEdit = newTextEdits.get(i);
      String commentValue = token.getValue();
      final boolean isSpacedBraces = this.options.isSpacedBraces();

      if (textEdit != null) {
        commentValue = textEdit.getNewText();
      }

      if (commentValue.trim().startsWith("##")) {
        Matcher matcher = TYPE_DOC_PATTERN.matcher(commentValue);
        if (!matcher.find() || matcher.groupCount() < 7) {
          continue;
        }

        String type = matcher.group(6);
        if (type == null) {
          continue;
        }

        final StringBuilder builder = new StringBuilder();

        builder
            .append(matcher.group(1))
            .append(matcher.group(2))
            .append(" ")
            .append(matcher.group(3))
            .append(" ");

        builder.append("{");
        if (isSpacedBraces) {
          builder.append(" ");
        }
        builder.append(type.trim());
        if (isSpacedBraces) {
          builder.append(" ");
        }
        builder.append("} ").append(matcher.group(7));

        newTextEdits.set(i, this.editToken(token, builder.toString(), "improper doc formatting"));
      } else {
        Matcher matcher = INLINE_TYPE_PATTERN.matcher(commentValue);
        if (!matcher.find() || matcher.groupCount() < 3) {
          continue;
        }

        newTextEdits.set(
            i,
            this.editToken(
                token,
                "# " + matcher.group(1) + ": " + matcher.group(3),
                "improper type formatting"));
      }
    }

    return newTextEdits;
  }

  private TextEdit validateWhitespacingBefore(final Token token) {
    if (this.requireWhitespaceBefore(token)) {
      return this.editWhitespaceBefore(token);
    } else if (this.requireNoWhitespaceBefore(token)) {
      return this.editNoWhitespaceBefore(token);
    }

    return this.editWhitespaceBefore(token);
  }

  private boolean requireNewlineBefore(final Token token) {
    return this.tokenIs(this.lastTextToken, "$")
        && this.lastTextToken.getLine() + 1 == token.getLine();
  }

  private boolean requireWhitespaceBefore(final Token token) {
    final String tokenValue = token.getOriginalValue().toLowerCase();

    final boolean prevBraceL =
        SPACED_BRACES_R.contains(tokenValue)
            && SPACED_BRACES_L.contains(this.lastTextToken.getValue());
    if (prevBraceL && this.options.isSpacedBraces() && this.options.isSpacedBracesOnEmpty()) {
      return true;
    }

    final boolean braces =
        SPACED_BRACES_R.contains(tokenValue)
            || SPACED_BRACES_L.contains(this.lastTextToken.getValue());

    if (braces) {
      return this.options.isSpacedBraces();
    }

    final String lastTextTokenValue =
        this.lastTextToken != null ? this.lastTextToken.getOriginalValue().toLowerCase() : null;
    return token.isOnSameLineThan(this.lastTextToken)
        && (KEYWORDS.contains(lastTextTokenValue) // Always whitespace after a keyword.
            || KEYWORDS.contains(tokenValue) // Always whitespace before a keyword.
            || this.tokenIs(token, "<<", "^<<"))
        && !(AUGMENTED_ASSIGNMENT_TOKENS.contains(
                lastTextTokenValue) // But no whitespace before augmented assignment.
            && (this.tokenIs(token, "<<", "^<<")))
        && !this.tokenIs(token, ".", ",", "]")
        && !this.tokenIs(this.lastToken, "[")
        && !this.tokenIs(
            this.lastTextToken,
            "_proc",
            "_loopbody",
            "_super"); // Except for _proc/_loopbody/_super.
  }

  private boolean requireNoWhitespaceBefore(final Token token) {
    final String tokenValue = token.getOriginalValue();

    if (token.getType() == GenericTokenType.COMMENT) {
      return false;
    }

    if (SPACED_BRACES_R.contains(tokenValue)
        && SPACED_BRACES_L.contains(this.lastTextToken.getValue())) {
      if (!this.options.isSpacedBraces()) {
        return true;
      }

      return !this.options.isSpacedBracesOnEmpty();
    }

    final boolean bracesSpacing =
        !this.options.isSpacedBraces()
            && (SPACED_BRACES_R.contains(tokenValue)
                || SPACED_BRACES_L.contains(this.lastTextToken.getValue()));
    if (bracesSpacing) {
      return true;
    }

    final String lastTextTokenValue =
        this.lastTextToken != null ? this.lastTextToken.getOriginalValue().toLowerCase() : null;
    return this.tokenIs(token, "]", ",")
        || this.tokenIs(this.lastTextToken, "@", "[", "_proc", "_loopbody", "_super")
        || this.nodeIsSlot()
        || this.currentNode.is(MagikGrammar.ARGUMENTS)
        || this.currentNode.is(MagikGrammar.PARAMETERS)
        || this.nodeIsMethodDefinition()
        || this.nodeIsInvocation()
        || this.nodeIsUnaryExpression()
        || AUGMENTED_ASSIGNMENT_TOKENS.contains(lastTextTokenValue)
            && this.tokenIs(token, "<<", "^<<");
  }

  private boolean nodeIsUnaryExpression() {
    final String lastTokenValue = this.lastTextToken.getOriginalValue();
    final AstNode unaryExprNode = this.currentNode.getFirstAncestor(MagikGrammar.UNARY_EXPRESSION);
    return unaryExprNode != null
        && unaryExprNode.getToken() == this.lastTextToken
        && ("-".equals(lastTokenValue) || "+".equals(lastTokenValue) || "~".equals(lastTokenValue));
  }

  private boolean nodeIsSlot() {
    if (this.currentNode == null || this.currentNode.getParent() == null) {
      return false;
    }

    return this.currentNode.getParent().is(MagikGrammar.SLOT);
  }

  private boolean nodeIsMethodDefinition() {
    if (this.currentNode == null || this.currentNode.getParent() == null) {
      return false;
    }

    return this.currentNode.is(MagikGrammar.METHOD_DEFINITION)
        || this.currentNode
            .getParent()
            .is(
                MagikGrammar.METHOD_DEFINITION,
                MagikGrammar.EXEMPLAR_NAME,
                MagikGrammar.METHOD_NAME);
  }

  private boolean nodeIsInvocation() {
    if (this.currentNode == null || this.currentNode.getParent() == null) {
      return false;
    }

    return this.currentNode.is(MagikGrammar.PROCEDURE_INVOCATION, MagikGrammar.METHOD_INVOCATION)
        || this.currentNode.is(MagikGrammar.IDENTIFIER)
            && this.currentNode.getParent().is(MagikGrammar.METHOD_INVOCATION);
  }

  @Override
  void walkPreNode(final AstNode node) {
    this.currentNode = node;

    if (node.is(MagikGrammar.TRANSMIT)) {
      // Reset indenting.
      this.indent = 0;
    } else if (node.is(
        MagikGrammar.VARIABLE_DEFINITION,
        MagikGrammar.VARIABLE_DEFINITION_MULTI,
        MagikGrammar.PROCEDURE_INVOCATION,
        MagikGrammar.METHOD_INVOCATION)) {
      this.indent += 1;
    }
  }

  @Override
  void walkPostNode(final AstNode node) {
    if (this.isBinaryExpression(node)
        || node.is(
            MagikGrammar.VARIABLE_DEFINITION,
            MagikGrammar.VARIABLE_DEFINITION_MULTI,
            MagikGrammar.PROCEDURE_INVOCATION,
            MagikGrammar.METHOD_INVOCATION)) {
      this.indent -= 1;
    }

    this.currentNode = this.currentNode.getParent();
  }

  private boolean isBinaryExpression(final AstNode node) {
    return node.is(
        MagikGrammar.ASSIGNMENT_EXPRESSION,
        MagikGrammar.AUGMENTED_ASSIGNMENT_EXPRESSION,
        MagikGrammar.OR_EXPRESSION,
        MagikGrammar.XOR_EXPRESSION,
        MagikGrammar.AND_EXPRESSION,
        MagikGrammar.EQUALITY_EXPRESSION,
        MagikGrammar.RELATIONAL_EXPRESSION,
        MagikGrammar.ADDITIVE_EXPRESSION,
        MagikGrammar.MULTIPLICATIVE_EXPRESSION,
        MagikGrammar.EXPONENTIAL_EXPRESSION);
  }

  @CheckForNull
  private TextEdit ensureIndenting(final Token token) {
    if (this.indent == 0 && !this.tokenIs(this.lastToken, GenericTokenType.WHITESPACE)) {
      return null;
    }

    final String indentText = this.indentText();
    final String reason = "improper indenting";
    if (!this.tokenIs(this.lastToken, GenericTokenType.WHITESPACE)) {
      return this.insertBeforeToken(token, indentText, reason);
    } else if (!this.lastToken.getOriginalValue().equals(indentText)) {
      return this.editToken(this.lastToken, indentText, reason);
    }

    return null;
  }

  private String indentText() {
    final int tabSize = this.options.getTabSize();
    final String indentText = this.options.isInsertSpaces() ? " ".repeat(tabSize) : "\t";

    return indentText.repeat(this.indent);
  }

  private void trackIndentPre(final Token token) {
    if (!this.tokenIs(token, GenericTokenType.COMMENT)
        && this.isBinaryExpression(this.currentNode)
        && this.currentNode.getChildren().get(1).getToken() == token) { // Only indent first.
      this.indent += 1;
    }

    final String tokenValue = token.getOriginalValue().toLowerCase();
    if (INDENT_DECREASE.contains(tokenValue)) {
      this.indent -= 1;
    }
  }

  private void trackIndentPost(final Token token) {
    final String tokenValue = token.getOriginalValue().toLowerCase();
    if (INDENT_INCREASE.contains(tokenValue)) {
      this.indent += 1;
    }
  }
}
