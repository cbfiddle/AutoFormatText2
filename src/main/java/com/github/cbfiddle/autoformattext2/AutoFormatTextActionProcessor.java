package com.github.cbfiddle.autoformattext2;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

class AutoFormatTextActionProcessor
{
    private FileType fileType;
    private int tabSize;

    public AutoFormatTextActionProcessor(AnActionEvent event, int specifiedLineWidth)
    {
        Editor editor = event.getData(CommonDataKeys.EDITOR);

        // Don't do anything on read-only documents
        if (editor == null || !editor.getDocument().isWritable()) {
            return;
        }

        Project project = event.getData(CommonDataKeys.PROJECT);

        VirtualFile virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (virtualFile == null) {
            return;
        }

        fileType = FileTypeManager.getInstance().getFileTypeByFile(virtualFile);

        // The default line width is the right margin as determined by the editor/project settings.

        int lineWidth = specifiedLineWidth;
        EditorSettings settings = editor.getSettings();
        if (lineWidth == 0) {
            lineWidth = settings.getRightMargin(project);
        }

        tabSize = settings.getTabSize(project);
        if (tabSize < 1) {
            tabSize = 1;
        }

        SelectionModel selectionModel = editor.getSelectionModel();

        int startPosition;
        int endPosition;
        String reformattedText;
        int newCaretPosition;

        if (selectionModel.hasSelection()) {
            // We have something selected, use that
            String bufferContent = editor.getDocument().getText();

            // Compute start and end position for block
            startPosition = bufferContent.lastIndexOf('\n', selectionModel.getSelectionStart() - 1) + 1;
            int blockEnd = selectionModel.getSelectionEnd();

            // If the selection ends at the end of a line, exclude the terminating newline of the selection from the
            // block. Otherwise, if the selection ends in the middle of a line, extend the block to include the rest of
            // the line, excluding the terminating newline. The result should be that the end position of the block
            // either points to a newline or points at the end of the buffer.

            if (blockEnd > 0 && bufferContent.charAt(blockEnd-1) == '\n') {
                // exclude that last newline
                blockEnd--;
            } else {
                // include the rest of the current line (up to the newline)
                blockEnd = bufferContent.indexOf('\n', selectionModel.getSelectionEnd());
            }
            endPosition = blockEnd == -1 ? bufferContent.length() : blockEnd;

            reformattedText = reformat(bufferContent.substring(startPosition, endPosition), lineWidth);
            newCaretPosition = startPosition + reformattedText.length();
        } else {
            // Nothing selected, try to find "current block"
            int currentCaretPosition = editor.getCaretModel().getOffset();

            String bufferContent = editor.getDocument().getText();
            int currentLineStart = bufferContent.lastIndexOf('\n', currentCaretPosition - 1) + 1;
            int currentLineEnd = bufferContent.indexOf('\n', currentCaretPosition);
            if (currentLineEnd == -1) {
                currentLineEnd = bufferContent.length();
            }
            String currentLine = bufferContent.substring(currentLineStart, currentLineEnd);
            if (currentLineStart == currentLineEnd) {
                return;
            }
            String currentLeftMargin = leftMargin(currentLine, false);

            // Find start of block
            int lineStart = currentLineStart;
            int lineEnd = currentLineEnd;
            while (true) {
                if (lineStart == 0) {
                    break;
                }
                if (!leftMargin(bufferContent.substring(lineStart, lineEnd), true)
                        .equals(currentLeftMargin)) {
                    // This is a "first line", don't go further up
                    break;
                }
                int newLineEnd = lineStart - 1;
                int newLineStart = bufferContent.lastIndexOf('\n', newLineEnd - 1) + 1;
                if (newLineStart == newLineEnd) {
                    break;
                }
                String newLine = bufferContent.substring(newLineStart, newLineEnd);
                String leftMargin = leftMargin(newLine, false);
                if (!currentLeftMargin.equals(leftMargin)) {
                    break;
                }
                if (newLine.substring(leftMargin.length()).trim().isEmpty()) {
                    break;
                }
                lineStart = newLineStart;
                lineEnd = newLineEnd;
            }
            startPosition = lineStart;

            // Find end of block
            lineStart = currentLineStart;
            lineEnd = currentLineEnd;
            while (true) {
                if (lineEnd == bufferContent.length()) {
                    break;
                }
                int newLineStart = lineEnd + 1;
                int newLineEnd = bufferContent.indexOf('\n', newLineStart);
                if (newLineEnd == -1)
                    newLineEnd = bufferContent.length();
                if (newLineStart == newLineEnd) {
                    break;
                }
                String newLine = bufferContent.substring(newLineStart, newLineEnd);
                String leftMargin = leftMargin(newLine, false);
                String firstLeftMargin = leftMargin(newLine, true);
                if (!currentLeftMargin.equals(leftMargin) || !leftMargin.equals(firstLeftMargin)) {
                    break;
                }
                if (newLine.substring(leftMargin.length()).trim().isEmpty()) {
                    break;
                }
                lineStart = newLineStart;
                lineEnd = newLineEnd;
            }
            endPosition = lineEnd;

            String text = bufferContent.substring(startPosition, endPosition);
            text = addMarkerAtCaretPosition(text, currentCaretPosition - startPosition);
            String reformattedTextWithMarkers = reformat(text, lineWidth);
            newCaretPosition = startPosition + findMarker(reformattedTextWithMarkers);
            reformattedText = removeMarker(reformattedTextWithMarkers);
        }

        CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
            selectionModel.setSelection(startPosition, endPosition);
            EditorModificationUtil.insertStringAtCaret(editor, reformattedText, true, false);
            selectionModel.setSelection(newCaretPosition, newCaretPosition);
            editor.getCaretModel().moveToOffset(newCaretPosition);
        }), null, null);
    }

    /**
      Returns position of 'C' marker (taking into account escaped characters with 'E').
    */
    private int findMarker(String text)
    {
        int position = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == 'E') {
                i++;
            } else {
                if (text.charAt(i) == 'C')
                    return position;
            }
            position++;
        }
        return position;
    }

    private String removeMarker(String text)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == 'C') {
                continue;
            }
            if (text.charAt(i) == 'E') {
                i++;
            }
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    /**
        Insert a 'C' at caret position. We escape with the 'E' character.
    */
    private String addMarkerAtCaretPosition(String text, int position)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            // Mark position
            if (i == position) {
                sb.append('C');
            }
            // Escape #
            if (text.charAt(i) == 'C' || text.charAt(i) == 'E') {
                sb.append('E');
            }
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    /**
      Reformat the specified text so that its width does not exceed the specified width.
    */

    private @NotNull String reformat(String text, int width)
    {
        // Compute the list of words and identify left margins. The first line of formatted text may be given a
        // different left margin than the remaining lines. In particular, lines other than the first line will not
        // contain bullet text. If the input text has more than one line, the left margin of the second line is used to
        // determine the left margin of lines other than the first line. Otherwise, the left margin of the first line
        // is used.

        List<String> words = new ArrayList<>();
        String firstLineMargin = null;
        String secondLineMargin = null;

        StringTokenizer lineTokenizer = new StringTokenizer(text, "\n\r");
        while (lineTokenizer.hasMoreTokens()) {

            // Remove left margin from line
            String line = lineTokenizer.nextToken();
            String leftMargin = leftMargin(line, firstLineMargin == null);
            line = line.substring(leftMargin.length());

            if (firstLineMargin == null) {
                firstLineMargin = leftMargin;
            } else if (secondLineMargin == null) {
                secondLineMargin = leftMargin;
            }

            // Add words for this line to the list of words
            StringTokenizer wordTokenizer = new StringTokenizer(line);
            while (wordTokenizer.hasMoreTokens()) {
                words.add(wordTokenizer.nextToken());
            }
        }

        if (firstLineMargin == null) {
            firstLineMargin = "";
        }

        if (secondLineMargin == null) {
            secondLineMargin = leftMargin(firstLineMargin, false);
        }

        int firstLineMarginWidth = getSingleLineTextWidth(firstLineMargin);
        int otherLinesMarginWidth = getSingleLineTextWidth(secondLineMargin);

        StringBuilder sb = new StringBuilder();
        sb.append(firstLineMargin);

        int currentLineWidth = firstLineMarginWidth;
        int currentLineMarginWidth = firstLineMarginWidth;

        while (!words.isEmpty()) {
            String word = words.get(0);
            if (currentLineWidth + 1 + word.length() <= width) {
                // Enough space to add word to this line
                if (currentLineWidth > currentLineMarginWidth) {
                    sb.append(' ');
                    currentLineWidth++;
                }
                sb.append(word);
                currentLineWidth += word.length();
                words.remove(0);
            } else if (currentLineWidth == currentLineMarginWidth && word.length() > width - currentLineMarginWidth) {
                // Word too long, but we are at the beginning of the line, so place it anyway
                sb.append(word);
                currentLineWidth += word.length();
                words.remove(0);
            } else {
                // Not enough space, create new line
                sb.append('\n');
                sb.append(secondLineMargin);
                currentLineWidth = otherLinesMarginWidth;
                currentLineMarginWidth = otherLinesMarginWidth;
            }
        }

        // Add final end-line if there was one originally
        if (text.endsWith("\n")) {
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
      Identify the left margin of the specified text line. The left margin might include a comment starting token
      or an asterisk or bullet text, depending upon the file type. If {@code isFirstLine} is false, bullet text is
      converted to spaces.

      @return the left margin text, with bullets possibly converted to an equivalent number of spaces.
    */

    private @NotNull String leftMargin(String text, boolean isFirstLine)
    {
        StringTokenizer lineTokenizer = new StringTokenizer(text, "\n\r");
        String firstLine = lineTokenizer.nextToken();

        // Compute margin on selected line
        StringBuilder margin = new StringBuilder();
        boolean foundJavaStar = false;
        boolean foundJavaSlashSlash = false;
        boolean foundTextBullet = false;

        for (int i = 0; i < firstLine.length(); i++) {
            char c = firstLine.charAt(i);
            if (c == ' ' || c == '\t') {
                margin.append(c);
            } else if (fileType == StdFileTypes.JAVA && !foundJavaStar && c == '*') {
                // Ignore first '*' if we are in Java mode
                foundJavaStar = true;
                margin.append(c);
            } else if (fileType == StdFileTypes.JAVA && !foundJavaSlashSlash
                    && c == '/' && i < firstLine.length() - 1 && firstLine.charAt(i + 1) == '/') {
                // Ignore first "//" if we are in Java mode
                foundJavaSlashSlash = true;
                margin.append("//");
                i++;
            } else if (!foundTextBullet && (c == '#' || c == '*' || c == '-' || c == '@')) {
                // Ignore "bullets" on first file
                foundTextBullet = true;
                margin.append(isFirstLine ? c : ' ');
            } else if (!foundTextBullet) {
                // Try to match more complex enumerations, like 1) or (a)
                String rest = firstLine.substring(i);
                Matcher matcher;
                matcher = Pattern.compile("(\\(?[0-9][0-9]?\\)).*").matcher(rest);
                if (!matcher.matches())
                    matcher = Pattern.compile("(\\(?[a-z][a-z]?\\)).*").matcher(rest);
                if (!matcher.matches())
                    matcher = Pattern.compile("(\\(?[A-Z][A-Z]?\\)).*").matcher(rest);
                if (matcher.matches()) {
                    foundTextBullet = true;
                    String matchedText = matcher.group(1);
                    if (isFirstLine) {
                        margin.append(matchedText);
                    } else {
                        margin.append(" ".repeat(matchedText.length()));
                    }
                    i = i + matchedText.length() - 1;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return margin.toString();
    }

    /**
      Return the width of the text, taking tabs into account. Only single lines are supported.
    */

    private int getSingleLineTextWidth(String text)
    {
        int col = 0;

        int count = text.length();

        for (int i = 0; i < count; i++) {
            char ch = text.charAt(i);
            if (ch == '\t') {
                while ((++col % tabSize) != 0);
            } else if (ch == '\n') {
                break;
            } else {
                col++;
            }
        }

        return col;
    }
}
