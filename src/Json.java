
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private Json() {
    }

    public static Object parse(String input) {
        return new Parser(input).parseValue();
    }

    public static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof JobApplication) {
            return stringify(((JobApplication) value).toMap());
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?>) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(stringify(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?>) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(stringify(item));
            }
            return out.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' ->
                    out.append("\\\\");
                case '"' ->
                    out.append("\\\"");
                case '\n' ->
                    out.append("\\n");
                case '\r' ->
                    out.append("\\r");
                case '\t' ->
                    out.append("\\t");
                default ->
                    out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static final class Parser {

        private final String input;
        private int position;

        Parser(String input) {
            this.input = input;
        }

        Object parseValue() {
            skipWhitespace();
            if (position >= input.length()) {
                throw error("Expected a JSON value");
            }
            char c = input.charAt(position);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (input.startsWith("null", position)) {
                position += 4;
                return null;
            }
            if (input.startsWith("true", position)) {
                position += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", position)) {
                position += 5;
                return Boolean.FALSE;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            position++;
            skipWhitespace();
            if (consume(']')) {
                throw error("Unexpected closing bracket");
            }
            if (consume('}')) {
                return result;
            }
            while (true) {
                skipWhitespace();
                if (position >= input.length() || input.charAt(position) != '"') {
                    throw error("Object keys must be strings");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            position++;
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char c = input.charAt(position++);
                if (c == '"') {
                    return result.toString();
                }
                if (c != '\\') {
                    result.append(c);
                    continue;
                }
                if (position >= input.length()) {
                    throw error("Invalid escape");
                }
                char escaped = input.charAt(position++);
                switch (escaped) {
                    case '"' ->
                        result.append('"');
                    case '\\' ->
                        result.append('\\');
                    case '/' ->
                        result.append('/');
                    case 'b' ->
                        result.append('\b');
                    case 'f' ->
                        result.append('\f');
                    case 'n' ->
                        result.append('\n');
                    case 'r' ->
                        result.append('\r');
                    case 't' ->
                        result.append('\t');
                    case 'u' -> {
                        if (position + 4 > input.length()) {
                            throw error("Invalid unicode escape");
                        
                        }result.append((char) Integer.parseInt(input.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default ->
                        throw error("Invalid escape sequence");
                }
            }
            throw error("Unterminated string");
        }

        private Number parseNumber() {
            int start = position;
            while (position < input.length() && "-+.0123456789eE".indexOf(input.charAt(position)) >= 0) {
                position++;
            }
            try {
                return Double.parseDouble(input.substring(start, position));
            } catch (NumberFormatException e) {
                throw error("Invalid number");
            }
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
        
            }}

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
        
            }}

        private boolean consume(char value) {
            if (position < input.length() && input.charAt(position) == value) {
                position++;
                return true;
            }
            return false;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + position);
        }
    }
}
