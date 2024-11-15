package nl.ramsolutions.sw.magik.formatting;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.GenericTokenType;
import com.sonar.sslr.api.Token;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.ramsolutions.sw.magik.TextEdit;
import nl.ramsolutions.sw.magik.api.MagikGrammar;
import nl.ramsolutions.sw.magik.api.MagikKeyword;
import nl.ramsolutions.sw.magik.api.MagikPunctuator;

/** Standard formatting strategy. */
class StandardFormattingStrategy extends FormattingStrategy {
  private static final Pattern TYPE_DOC_PATTERN =
      Pattern.compile("([^#]*)(##+)\\s*(@(param|slot|return|loop))\\s*(\\{([^}]*)})?\\s*(.*)");
  public static final Pattern INLINE_TYPE_PATTERN =
      Pattern.compile("^#\\s*((iter-)?type):\\s*(.*)$");

  private static final List<String> KEYWORDS = List.of(MagikKeyword.keywordValues());

  // We cannot base indenting purely on AstNodes (BODY/PARAMETERS/ARGUMENTS/SIMPLE_VECTOR/...),
  // as bodies and tokens don't play that well together.
  // Tokens surround the AstNodes, e.g.: '(', pre PARAMETERS, post PARAMETERS, ')', or
  // '_method', '...', pre BODY, ..., post BODY, '# comment', '_endmethod'.
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

  private int indent;
  private AstNode currentNode;

  StandardFormattingStrategy(final FormattingOptions options) {
    super(options);
  }

  @Override
  TextEdit walkCommentToken(final Token token) {
    return this.walkToken(token);
  }

  @Override
  public TextEdit walkEolToken(final Token token) {
    // Don't touch syntax errors.
    if (this.currentNode.is(MagikGrammar.SYNTAX_ERROR)) {
      return null;
    }

    TextEdit textEdit = null;
    if (this.options.isTrimTrailingWhitespace()
        && this.lastToken != null
        && this.lastToken.getType() == GenericTokenType.WHITESPACE) {
      textEdit = this.editToken(this.lastToken, "");
    }
    return textEdit;
  }

  @Override
  TextEdit walkToken(final Token token) {
    this.trackIndentPre(token);

    TextEdit textEdit;
    if (this.lastTextToken != null) {
      final boolean isOnNewline = this.lastTextToken.getLine() != token.getLine();
      if (isOnNewline) {
        textEdit = this.ensureIndenting(token);
      } else {
        textEdit = this.validateWhitespacingBefore(token);
      }
    } else {
      // First token, should not contain any pre-whitespace/indenting.
      textEdit = this.editNoWhitespaceBefore(token);
    }

    if (token.getType().equals(GenericTokenType.COMMENT)) {
      textEdit = this.validateComment(token, textEdit);
    }

    this.trackIndentPost(token);
    return textEdit;
  }

  private @Nullable TextEdit validateComment(Token token, @Nullable TextEdit textEdit) {
    TextEdit newTextEdit = textEdit;
    String commentValue = token.getValue();
    final boolean isSpacedBraces = this.options.isSpacedBraces();

    if (textEdit != null) {
      commentValue = textEdit.getNewText();
    }

    if (commentValue.trim().startsWith("##")) {
      Matcher matcher = TYPE_DOC_PATTERN.matcher(commentValue);
      if (!matcher.find()) {
        return newTextEdit;
      }

      if (matcher.groupCount() < 7) {
        return newTextEdit;
      }

      String type = matcher.group(6);
      if (type == null) {
        return newTextEdit;
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

      newTextEdit = this.editToken(token, builder.toString());
    } else {
      Matcher matcher = INLINE_TYPE_PATTERN.matcher(commentValue);
      if (!matcher.find()) {
        return textEdit;
      }

      if (matcher.groupCount() < 3) {
        return textEdit;
      }

      newTextEdit = this.editToken(token, "# " + matcher.group(1) + ": " + matcher.group(3));
    }

    return newTextEdit;
  }

  private TextEdit validateWhitespacingBefore(final Token token) {
    TextEdit textEdit;

    if (this.requireWhitespaceBefore(token)) {
      textEdit = this.editWhitespaceBefore(token);
    } else if (this.requireNoWhitespaceBefore(token)) {
      textEdit = this.editNoWhitespaceBefore(token);
    } else {
      textEdit = this.editWhitespaceBefore(token);
    }

    return textEdit;
  }

  private boolean requireWhitespaceBefore(final Token token) {
    final String tokenValue = token.getOriginalValue().toLowerCase();

    if (SPACED_BRACES_R.contains(tokenValue)
        && SPACED_BRACES_L.contains(this.lastTextToken.getValue())) {
      return this.options.isSpacedBracesOnEmpty() && this.options.isSpacedBraces();
    }

    final boolean bracesSpacing =
        this.options.isSpacedBraces()
            && (SPACED_BRACES_R.contains(tokenValue)
                || SPACED_BRACES_L.contains(this.lastTextToken.getValue()));

    if (bracesSpacing) {
      return true;
    }

    return this.lastTextToken != null // Don't string keywords: _if _not, _method obj, obj _andif..
        && this.lastTextToken.getLine() == token.getLine()
        && (KEYWORDS.contains(this.lastTextToken.getOriginalValue().toLowerCase())
            || KEYWORDS.contains(tokenValue)
            || "<<".equals(tokenValue) // Not really part of stringing keywords.
            || "^<<".equals(tokenValue))
        && !".".equals(tokenValue)
        && !",".equals(tokenValue)
        && !"]".equals(tokenValue)
        && !"[".equals(this.lastTextToken.getValue());
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

    return "]".equals(tokenValue)
        || ",".equals(tokenValue)
        || this.nodeIsSlot()
        || this.lastTokenIs("@", "(", "{", "[")
        || this.currentNode.is(MagikGrammar.ARGUMENTS)
        || this.currentNode.is(MagikGrammar.PARAMETERS)
        || this.nodeIsMethodDefinition()
        || this.nodeIsInvocation()
        || this.nodeIsUnaryExpression();
  }

  private boolean lastTokenIs(final String... values) {
    final Set<String> valuesSet = Set.of(values);
    final String lastTokenValue = this.lastTextToken.getOriginalValue();
    return valuesSet.contains(lastTokenValue);
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
  public void walkPreNode(final AstNode node) {
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
  public void walkPostNode(final AstNode node) {
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
    // Indenting.
    if (this.indent == 0 && this.lastToken.getType() != GenericTokenType.WHITESPACE) {
      return null;
    }

    final String indentText = this.indentText();
    if (this.lastToken.getType() != GenericTokenType.WHITESPACE) {
      return this.insertBeforeToken(token, indentText);
    } else if (!this.lastToken.getOriginalValue().equals(indentText)) {
      return this.editToken(this.lastToken, indentText);
    }

    return null;
  }

  private String indentText() {
    final int tabSize = this.options.getTabSize();
    final String indentText = this.options.isInsertSpaces() ? " ".repeat(tabSize) : "\t";

    return indentText.repeat(this.indent);
  }

  private void trackIndentPre(final Token token) {
    if (token.getType() != GenericTokenType.COMMENT
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
