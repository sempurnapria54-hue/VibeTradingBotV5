package com.example.tradingbot.spec;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Вычислитель скалярных выражений исполнимой спецификации.
 *
 * <p>Язык намеренно мал: арифметика, сравнения, булева логика, доступ к полям
 * через точку и несколько функций. Всё, что сложнее, выражается именованными
 * величинами спецификации, а не расширением языка.
 *
 * <p>Грамматика (по убыванию приоритета):
 * <pre>
 *   primary := number | 'string' | true | false | null | ident('.'ident)* | '(' expr ')' | func '(' args ')'
 *   unary   := ('!' | '-') unary | primary
 *   mul     := unary (('*' | '/') unary)*
 *   add     := mul (('+' | '-') mul)*
 *   cmp     := add (('&lt;' | '&lt;=' | '&gt;' | '&gt;=') add)*
 *   eq      := cmp (('==' | '!=') cmp)*
 *   and     := eq ('&amp;&amp;' eq)*
 *   or      := and ('||' and)*
 * </pre>
 *
 * <p>Функции: {@code min max abs floorTo coalesce if isNull notNull in not}.
 *
 * <p>Префикс {@code ?} перед идентификатором делает его необязательным:
 * отсутствующее поле даёт пустоту вместо отказа. Нужен там, где предмет
 * проверки — само наличие поля.
 */
public final class SpecExpression {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final String source;

    private int position;

    private SpecExpression(String source) {
        this.source = source;
        this.position = 0;
    }

    /** Разбирает выражение и возвращает вычислимый узел. */
    public static Node parse(String source) {
        SpecExpression parser = new SpecExpression(source);
        Node node = parser.or();
        parser.skipSpaces();
        if (parser.position < parser.source.length()) {
            throw new SpecException("Лишний текст в выражении: " + source.substring(parser.position));
        }
        return node;
    }

    /** Узел выражения. Резолвер отдаёт значение идентификатора. */
    public interface Node {

        Object eval(Function<String, Object> resolver);
    }

    // ---------------------------------------------------------------- парсер

    private Node or() {
        Node left = and();
        while (match("||")) {
            Node right = and();
            Node l = left;
            left = resolver -> truth(l.eval(resolver)) || truth(right.eval(resolver));
        }
        return left;
    }

    private Node and() {
        Node left = equality();
        while (match("&&")) {
            Node right = equality();
            Node l = left;
            left = resolver -> truth(l.eval(resolver)) && truth(right.eval(resolver));
        }
        return left;
    }

    private Node equality() {
        Node left = comparison();
        while (true) {
            if (match("==")) {
                Node right = comparison();
                Node l = left;
                left = resolver -> equal(l.eval(resolver), right.eval(resolver));
            } else if (match("!=")) {
                Node right = comparison();
                Node l = left;
                left = resolver -> !equal(l.eval(resolver), right.eval(resolver));
            } else {
                return left;
            }
        }
    }

    private Node comparison() {
        Node left = additive();
        while (true) {
            String operator = matchAny("<=", ">=", "<", ">");
            if (operator == null) {
                return left;
            }
            Node right = additive();
            Node l = left;
            left = resolver -> compare(operator, number(l.eval(resolver)), number(right.eval(resolver)));
        }
    }

    private Node additive() {
        Node left = multiplicative();
        while (true) {
            String operator = matchAny("+", "-");
            if (operator == null) {
                return left;
            }
            Node right = multiplicative();
            Node l = left;
            left = resolver -> "+".equals(operator)
                    ? number(l.eval(resolver)).add(number(right.eval(resolver)), MC)
                    : number(l.eval(resolver)).subtract(number(right.eval(resolver)), MC);
        }
    }

    private Node multiplicative() {
        Node left = unary();
        while (true) {
            String operator = matchAny("*", "/");
            if (operator == null) {
                return left;
            }
            Node right = unary();
            Node l = left;
            left = resolver -> "*".equals(operator)
                    ? number(l.eval(resolver)).multiply(number(right.eval(resolver)), MC)
                    : number(l.eval(resolver)).divide(number(right.eval(resolver)), MC);
        }
    }

    private Node unary() {
        if (match("!")) {
            Node operand = unary();
            return resolver -> !truth(operand.eval(resolver));
        }
        if (matchUnaryMinus()) {
            Node operand = unary();
            return resolver -> number(operand.eval(resolver)).negate();
        }
        return primary();
    }

    private Node primary() {
        skipSpaces();
        if (position >= source.length()) {
            throw new SpecException("Выражение оборвано: " + source);
        }
        char current = source.charAt(position);
        if (current == '(') {
            position++;
            Node inner = or();
            expect(')');
            return inner;
        }
        if (current == '\'') {
            return literal(readQuoted());
        }
        if (Character.isDigit(current)) {
            return literal(readNumber());
        }
        if (current == '?') {
            position++;
            String optional = readIdentifier();
            return resolver -> {
                try {
                    return resolver.apply(optional);
                } catch (SpecException absent) {
                    return null;
                }
            };
        }
        String identifier = readIdentifier();
        skipSpaces();
        if (position < source.length() && source.charAt(position) == '(') {
            position++;
            List<Node> arguments = readArguments();
            return call(identifier, arguments);
        }
        return switch (identifier) {
            case "true" -> literal(Boolean.TRUE);
            case "false" -> literal(Boolean.FALSE);
            case "null" -> literal(null);
            default -> resolver -> resolver.apply(identifier);
        };
    }

    private List<Node> readArguments() {
        List<Node> arguments = new ArrayList<>();
        skipSpaces();
        if (position < source.length() && source.charAt(position) == ')') {
            position++;
            return arguments;
        }
        while (true) {
            arguments.add(or());
            skipSpaces();
            if (match(",")) {
                continue;
            }
            expect(')');
            return arguments;
        }
    }

    private static Node call(String name, List<Node> arguments) {
        return switch (name) {
            case "min" -> resolver -> number(arguments.get(0).eval(resolver))
                    .min(number(arguments.get(1).eval(resolver)));
            case "max" -> resolver -> number(arguments.get(0).eval(resolver))
                    .max(number(arguments.get(1).eval(resolver)));
            case "abs" -> resolver -> number(arguments.get(0).eval(resolver)).abs();
            case "floorTo" -> resolver -> {
                BigDecimal value = number(arguments.get(0).eval(resolver));
                BigDecimal step = number(arguments.get(1).eval(resolver));
                if (step.signum() == 0) {
                    throw new SpecException("Шаг округления нулевой");
                }
                return value.divideToIntegralValue(step).multiply(step);
            };
            case "not" -> resolver -> !truth(arguments.get(0).eval(resolver));
            case "isNull" -> resolver -> arguments.get(0).eval(resolver) == null;
            case "notNull" -> resolver -> arguments.get(0).eval(resolver) != null;
            case "coalesce" -> resolver -> {
                for (Node argument : arguments) {
                    Object value = argument.eval(resolver);
                    if (value != null) {
                        return value;
                    }
                }
                return null;
            };
            case "if" -> resolver -> truth(arguments.get(0).eval(resolver))
                    ? arguments.get(1).eval(resolver)
                    : arguments.get(2).eval(resolver);
            case "in" -> resolver -> {
                Object subject = arguments.get(0).eval(resolver);
                for (int i = 1; i < arguments.size(); i++) {
                    if (equal(subject, arguments.get(i).eval(resolver))) {
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            };
            default -> throw new SpecException("Неизвестная функция: " + name);
        };
    }

    private static Node literal(Object value) {
        return resolver -> value;
    }

    // -------------------------------------------------------------- лексика

    private void skipSpaces() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
            position++;
        }
    }

    private boolean match(String token) {
        skipSpaces();
        if (source.startsWith(token, position)) {
            position += token.length();
            return true;
        }
        return false;
    }

    private String matchAny(String... tokens) {
        for (String token : tokens) {
            skipSpaces();
            if (source.startsWith(token, position) && notPartOfLongerOperator(token)) {
                position += token.length();
                return token;
            }
        }
        return null;
    }

    private boolean notPartOfLongerOperator(String token) {
        if (!"<".equals(token) && !">".equals(token)) {
            return true;
        }
        int next = position + token.length();
        return next >= source.length() || source.charAt(next) != '=';
    }

    private boolean matchUnaryMinus() {
        skipSpaces();
        if (position < source.length() && source.charAt(position) == '-') {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char symbol) {
        skipSpaces();
        if (position >= source.length() || source.charAt(position) != symbol) {
            throw new SpecException("Ожидался '" + symbol + "' в: " + source);
        }
        position++;
    }

    private String readQuoted() {
        position++;
        int start = position;
        while (position < source.length() && source.charAt(position) != '\'') {
            position++;
        }
        String value = source.substring(start, position);
        expect('\'');
        return value;
    }

    private BigDecimal readNumber() {
        int start = position;
        while (position < source.length()
                && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
            position++;
        }
        return new BigDecimal(source.substring(start, position));
    }

    private String readIdentifier() {
        int start = position;
        while (position < source.length()
                && (Character.isLetterOrDigit(source.charAt(position))
                || source.charAt(position) == '_'
                || source.charAt(position) == '.')) {
            position++;
        }
        if (start == position) {
            throw new SpecException("Ожидался идентификатор в: " + source.substring(position));
        }
        return source.substring(start, position);
    }

    // ------------------------------------------------------------ семантика

    /** Приводит значение к числу; пустота числом не подменяется. */
    public static BigDecimal number(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number numeric) {
            return new BigDecimal(numeric.toString());
        }
        if (value == null) {
            throw new SpecException("Пустой операнд в арифметике: подстановка нуля запрещена");
        }
        throw new SpecException("Не число: " + value);
    }

    /** Приводит значение к булеву; пустота благоприятным значением не становится. */
    public static boolean truth(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new SpecException("Не булево значение: " + value);
    }

    private static boolean equal(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number && right instanceof Number) {
            return number(left).compareTo(number(right)) == 0;
        }
        return left.toString().equals(right.toString());
    }

    private static boolean compare(String operator, BigDecimal left, BigDecimal right) {
        int sign = left.compareTo(right);
        return switch (operator) {
            case "<" -> sign < 0;
            case "<=" -> sign <= 0;
            case ">" -> sign > 0;
            case ">=" -> sign >= 0;
            default -> throw new SpecException("Неизвестное сравнение: " + operator);
        };
    }

    /**
     * Собирает элементы по пути с флэттенингом: сегмент {@code name[]} разворачивает
     * список, {@code name&#123;&#125;} — значения словаря. Путь без флэттенинга даёт
     * список из одного значения (сам список — как есть).
     */
    public static java.util.List<Object> collect(Map<String, Object> root, String path) {
        java.util.List<Object> current = new ArrayList<>();
        current.add(root);
        boolean flattened = false;
        for (String segment : path.split("[.]")) {
            String name = segment;
            java.util.List<String> operations = new ArrayList<>();
            while (name.endsWith("[]") || name.endsWith("{}")) {
                operations.add(0, name.substring(name.length() - 2));
                name = name.substring(0, name.length() - 2);
            }
            java.util.List<Object> next = new ArrayList<>();
            for (Object node : current) {
                if (!(node instanceof Map)) {
                    continue;
                }
                Object value = ((Map<?, ?>) node).get(name);
                if (value != null) {
                    next.add(value);
                }
            }
            for (String operation : operations) {
                java.util.List<Object> expanded = new ArrayList<>();
                for (Object node : next) {
                    if ("[]".equals(operation) && node instanceof java.util.List) {
                        expanded.addAll((java.util.List<Object>) node);
                    } else if ("{}".equals(operation) && node instanceof Map) {
                        expanded.addAll(((Map<String, Object>) node).values());
                    }
                }
                next = expanded;
                flattened = true;
            }
            current = next;
        }
        if (!flattened && current.size() == 1 && current.get(0) instanceof java.util.List) {
            return (java.util.List<Object>) current.get(0);
        }
        java.util.List<Object> result = new ArrayList<>();
        for (Object node : current) {
            if (node instanceof java.util.List) {
                result.addAll((java.util.List<Object>) node);
            } else {
                result.add(node);
            }
        }
        return result;
    }

    /** Достаёт значение по пути {@code a.b.c} из вложенных Map. */
    public static Object path(Map<String, Object> root, String dotted) {
        Object current = root;
        for (String part : dotted.split("\\.")) {
            if (!(current instanceof Map)) {
                return SpecScope.ABSENT;
            }
            Map<?, ?> map = (Map<?, ?>) current;
            if (!map.containsKey(part)) {
                return SpecScope.ABSENT;
            }
            current = map.get(part);
        }
        return current;
    }
}
