package org.puppylab.mypassword.cli;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    public static List<String> parse(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\"') {
                inQuotes = !inQuotes; // 切换引号状态
            } else if (Character.isWhitespace(c) && !inQuotes) {
                // 不在引号内的空格，意味着一个 token 结束
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }

        // 处理最后一个 token
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }

        return tokens;
    }
}
