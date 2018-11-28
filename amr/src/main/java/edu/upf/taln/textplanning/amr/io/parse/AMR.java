package edu.upf.taln.textplanning.amr.io.parse;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class AMR extends Grammar {
    public AMR(String input, Actions actions) {
        this.input = input;
        this.inputSize = input.length();
        this.actions = actions;
        this.offset = 0;
        this.cache = new EnumMap<>(Label.class);
        this.failure = 0;
        this.expected = new ArrayList<>();
    }

    public static TreeNode parse(String input, Actions actions) throws ParseError {
        AMR parser = new AMR(input, actions);
        return parser.parse();
    }

    public static TreeNode parse(String input) throws ParseError {
        return parse(input, null);
    }

    private static String formatError(String input, int offset, List<String> expected) {
        String[] lines = input.split("\n");
        int lineNo = 0, position = 0;
        while (position <= offset) {
            position += lines[lineNo].length() + 1;
            lineNo += 1;
        }
        StringBuilder message = new StringBuilder("Line " + lineNo + ": expected " + expected + "\n");
        String line = lines[lineNo - 1];
        message.append(line).append("\n");
        position -= line.length() + 1;
        while (position < offset) {
            message.append(" ");
            position += 1;
        }
        return message + "^";
    }

    private TreeNode parse() throws ParseError {
        TreeNode tree = _read_x();
        if (tree != FAILURE && offset == inputSize) {
            return tree;
        }
        if (expected.isEmpty()) {
            failure = offset;
            expected.add("<EOF>");
        }
        throw new ParseError(formatError(input, failure, expected));
    }
}
