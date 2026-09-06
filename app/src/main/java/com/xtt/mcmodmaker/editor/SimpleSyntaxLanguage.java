package com.xtt.mcmodmaker.editor;

import android.os.Bundle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.lang.styling.MappedSpans;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.text.TextRange;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * 轻量语法高亮语言（Python / JSON）。
 * 基于 sora-editor 的 SimpleAnalyzeManager + MappedSpans 实现。
 * 支持：关键字、注释、字符串、多行字符串（三引号）、数字、运算符、
 * 函数名（def / 调用 / 内置）、类名、装饰器（Python）、JSON 键名。
 */
public class SimpleSyntaxLanguage implements Language {

    private static final Set<String> PYTHON_KEYWORDS = new HashSet<>(Arrays.asList(
            "true", "false", "null", "def", "class", "import", "from", "return", "pass",
            "if", "else", "elif", "for", "while", "in", "not", "and", "or", "try", "except",
            "finally", "with", "as", "yield", "lambda", "break", "continue", "global",
            "nonlocal", "assert", "raise", "del", "is", "None", "True", "False"));

    private static final Set<String> JSON_KEYWORDS = new HashSet<>(Arrays.asList(
            "true", "false", "null", "True", "False", "None"));

    /** Python 内置函数 / 常用类型。 */
    private static final Set<String> PYTHON_BUILTINS = new HashSet<>(Arrays.asList(
            "abs", "all", "any", "ascii", "bin", "bool", "bytearray", "bytes", "callable",
            "chr", "classmethod", "compile", "complex", "delattr", "dict", "dir", "divmod",
            "enumerate", "eval", "exec", "filter", "float", "format", "frozenset", "getattr",
            "globals", "hasattr", "hash", "help", "hex", "id", "input", "int", "isinstance",
            "issubclass", "iter", "len", "list", "locals", "map", "max", "memoryview", "min",
            "next", "object", "oct", "open", "ord", "pow", "print", "property", "range",
            "repr", "reversed", "round", "set", "setattr", "slice", "sorted", "staticmethod",
            "str", "sum", "super", "tuple", "type", "vars", "zip", "Exception", "ValueError",
            "TypeError", "KeyError", "IndexError", "RuntimeError", "NotImplementedError"));

    /** sora-editor 绘制时无条件调用 formatter.isRunning()，必须返回非空实现。 */
    private static final Formatter EMPTY_FORMATTER = new Formatter() {
        @Override
        public void format(Content content, TextRange range) { }

        @Override
        public void formatRegion(Content content, TextRange range, TextRange region) { }

        @Override
        public void setReceiver(FormatResultReceiver receiver) { }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public void destroy() { }
    };

    private static final SymbolPairMatch EMPTY_SYMBOL_PAIRS = new SymbolPairMatch();

    private final boolean isPython;
    private final SimpleAnalyzeManager<Void> manager;

    public SimpleSyntaxLanguage(boolean isPython) {
        this.isPython = isPython;
        this.manager = new SimpleAnalyzeManager<Void>() {
            @Override
            protected Styles analyze(StringBuilder content, Delegate<Void> delegate) {
                return doAnalyze(content);
            }
        };
    }

    private Styles doAnalyze(StringBuilder content) {
        int lineCount = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') lineCount++;
        }
        MappedSpans.Builder builder = new MappedSpans.Builder(lineCount);
        boolean inLineComment = false;
        boolean inString = false;
        boolean inTriple = false;
        char stringChar = 0;
        String prevWord = "";
        int line = 0;
        int col = 0;
        int n = content.length();
        for (int i = 0; i < n; i++) {
            char c = content.charAt(i);
            if (c == '\n') {
                line++;
                col = 0;
                inLineComment = false;
                continue;
            }
            if (inLineComment) {
                col++;
                continue;
            }
            if (inTriple) {
                if (c == stringChar && i + 2 < n
                        && content.charAt(i + 1) == stringChar && content.charAt(i + 2) == stringChar) {
                    inTriple = false;
                    col += 3;
                    i += 2;
                } else {
                    col++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    col += 2;
                    i++;
                } else {
                    if (c == stringChar) inString = false;
                    col++;
                }
                continue;
            }
            // 行注释
            if (c == '#') {
                inLineComment = true;
                builder.addIfNeeded(line, col, styleOf(EditorColorScheme.COMMENT));
                col++;
                continue;
            }
            // 三引号多行字符串（Python）
            if (isPython && (c == '\'' || c == '"')
                    && i + 2 < n && content.charAt(i + 1) == c && content.charAt(i + 2) == c) {
                inTriple = true;
                stringChar = c;
                builder.addIfNeeded(line, col, styleOf(EditorColorScheme.LITERAL));
                col += 3;
                i += 2;
                continue;
            }
            // JSON 键名："key": 形式
            if (!isPython && (c == '\'' || c == '"')) {
                int end = findStringEnd(content, i);
                if (end > i) {
                    int k = end + 1;
                    while (k < n && (content.charAt(k) == ' ' || content.charAt(k) == '\t')) k++;
                    if (k < n && content.charAt(k) == ':') {
                        builder.addIfNeeded(line, col, styleOf(EditorColorScheme.ATTRIBUTE_NAME));
                        col += (end - i + 1);
                        i = end;
                        continue;
                    }
                }
                inString = true;
                stringChar = c;
                builder.addIfNeeded(line, col, styleOf(EditorColorScheme.LITERAL));
                col++;
                continue;
            }
            // 普通字符串
            if (c == '\'' || c == '"') {
                inString = true;
                stringChar = c;
                builder.addIfNeeded(line, col, styleOf(EditorColorScheme.LITERAL));
                col++;
                continue;
            }
            // 装饰器（Python @xxx）
            if (isPython && c == '@') {
                builder.addIfNeeded(line, col, styleOf(EditorColorScheme.ATTRIBUTE_NAME));
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(content.charAt(j)) || content.charAt(j) == '_')) j++;
                col += (j - i);
                i = j - 1;
                continue;
            }
            // 数字
            if (Character.isDigit(c)) {
                builder.addIfNeeded(line, col, styleOf(EditorColorScheme.LITERAL));
                col++;
                continue;
            }
            // 标识符
            if (Character.isLetter(c) || c == '_') {
                int start = col;
                int j = i;
                while (j < n && (Character.isLetterOrDigit(content.charAt(j)) || content.charAt(j) == '_')) j++;
                String word = content.substring(i, j);
                Set<String> kws = isPython ? PYTHON_KEYWORDS : JSON_KEYWORDS;
                if (kws.contains(word)) {
                    builder.addIfNeeded(line, start, styleOf(EditorColorScheme.KEYWORD));
                } else if (isPython && ("def".equals(prevWord) || "class".equals(prevWord))) {
                    // def 后的函数名 / class 后的类名
                    builder.addIfNeeded(line, start, styleOf(EditorColorScheme.FUNCTION_NAME));
                } else if (isPython && PYTHON_BUILTINS.contains(word)) {
                    builder.addIfNeeded(line, start, styleOf(EditorColorScheme.FUNCTION_NAME));
                } else {
                    // 函数调用：标识符后紧跟 '('
                    int k = j;
                    while (k < n && (content.charAt(k) == ' ' || content.charAt(k) == '\t')) k++;
                    if (k < n && content.charAt(k) == '(') {
                        builder.addIfNeeded(line, start, styleOf(EditorColorScheme.FUNCTION_NAME));
                    }
                }
                prevWord = word;
                i = j - 1;
                col = start + word.length();
                continue;
            }
            // 运算符
            if ("+-*/%=<>!&|^~?".indexOf(c) >= 0) {
                builder.addIfNeeded(line, col, styleOf(EditorColorScheme.OPERATOR));
                col++;
                continue;
            }
            col++;
        }
        builder.determine(lineCount);
        MappedSpans spans = builder.build();
        return new Styles(spans);
    }

    /** 从 start（引号位置）向后查找字符串结束位置（含转义），找不到返回 -1。 */
    private int findStringEnd(StringBuilder content, int start) {
        char quote = content.charAt(start);
        int n = content.length();
        for (int i = start + 1; i < n; i++) {
            char c = content.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            } else if (c == '\n') {
                return -1;
            }
        }
        return -1;
    }

    /** 将颜色 ID 编码为 sora Span 的 style（低 32 位颜色 ID，高 32 位样式位）。 */
    private long styleOf(int colorId) {
        return ((long) 0 << 32) | (colorId & 0xffffffffL);
    }

    @Override
    public SimpleAnalyzeManager<Void> getAnalyzeManager() {
        return manager;
    }

    @Override
    public int getInterruptionLevel() {
        return INTERRUPTION_LEVEL_NONE;
    }

    @Override
    public void requireAutoComplete(ContentReference content, CharPosition position,
                                    CompletionPublisher publisher, Bundle extraArguments)
            throws CompletionCancelledException {
        // 不提供自动补全
    }

    @Override
    public int getIndentAdvance(ContentReference content, int line, int column) {
        return 0;
    }

    @Override
    public boolean useTab() {
        return false;
    }

    @Override
    public Formatter getFormatter() {
        return EMPTY_FORMATTER;
    }

    @Override
    public SymbolPairMatch getSymbolPairs() {
        return EMPTY_SYMBOL_PAIRS;
    }

    @Override
    public NewlineHandler[] getNewlineHandlers() {
        return new NewlineHandler[0];
    }

    @Override
    public void destroy() {
        // 无需清理
    }
}