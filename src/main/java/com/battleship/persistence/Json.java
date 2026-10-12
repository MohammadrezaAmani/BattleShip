package com.battleship.persistence;

import java.util.*;

/** Small, dependency-free JSON implementation for bounded local save files. */
public final class Json {
  private Json() {}

  public static String stringify(Object o) {
    StringBuilder sb = new StringBuilder();
    write(o, sb);
    return sb.toString();
  }

  private static void write(Object o, StringBuilder sb) {
    if (o == null) {
      sb.append("null");
      return;
    }
    if (o instanceof String s) {
      sb.append('"');
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
          case '"' -> sb.append("\\\"");
          case '\\' -> sb.append("\\\\");
          case '\n' -> sb.append("\\n");
          case '\r' -> sb.append("\\r");
          case '\t' -> sb.append("\\t");
          default -> {
            if (c < 32) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
          }
        }
      }
      sb.append('"');
      return;
    }
    if (o instanceof Number || o instanceof Boolean) {
      sb.append(o);
      return;
    }
    if (o instanceof Map<?, ?> m) {
      sb.append('{');
      boolean first = true;
      for (var e : m.entrySet()) {
        if (!(e.getKey() instanceof String))
          throw new IllegalArgumentException("Object key must be string");
        if (!first) sb.append(',');
        first = false;
        write(e.getKey(), sb);
        sb.append(':');
        write(e.getValue(), sb);
      }
      sb.append('}');
      return;
    }
    if (o instanceof Iterable<?> list) {
      sb.append('[');
      boolean first = true;
      for (Object v : list) {
        if (!first) sb.append(',');
        first = false;
        write(v, sb);
      }
      sb.append(']');
      return;
    }
    throw new IllegalArgumentException("Cannot serialize " + o.getClass());
  }

  public static Object parse(String s) {
    Parser p = new Parser(s);
    Object o = p.value(0);
    p.whitespace();
    if (p.pos != s.length()) throw new IllegalArgumentException("Trailing JSON content");
    return o;
  }

  public static Map<String, Object> object(Object o) {
    if (!(o instanceof Map<?, ?> m)) throw new IllegalArgumentException("Expected JSON object");
    Map<String, Object> result = new LinkedHashMap<>();
    m.forEach(
        (k, v) -> {
          if (!(k instanceof String)) throw new IllegalArgumentException("Invalid key");
          result.put((String) k, v);
        });
    return result;
  }

  public static List<Object> array(Object o) {
    if (!(o instanceof List<?> l)) throw new IllegalArgumentException("Expected JSON array");
    return new ArrayList<>(l);
  }

  public static String string(Object o) {
    if (!(o instanceof String s)) throw new IllegalArgumentException("Expected JSON string");
    return s;
  }

  public static int integer(Object o) {
    if (!(o instanceof Byte || o instanceof Short || o instanceof Integer || o instanceof Long))
      throw new IllegalArgumentException("Expected integer");
    long n = ((Number) o).longValue();
    if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Integer out of range");
    return (int) n;
  }

  public static long longNumber(Object o) {
    if (!(o instanceof Byte || o instanceof Short || o instanceof Integer || o instanceof Long))
      throw new IllegalArgumentException("Expected long");
    return ((Number) o).longValue();
  }

  public static boolean bool(Object o) {
    if (!(o instanceof Boolean b)) throw new IllegalArgumentException("Expected boolean");
    return b;
  }

  private static final class Parser {
    final String input;
    int pos = 0;

    Parser(String s) {
      input = s;
    }

    void whitespace() {
      while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    char take() {
      if (pos >= input.length()) throw error("Unexpected end");
      return input.charAt(pos++);
    }

    IllegalArgumentException error(String msg) {
      return new IllegalArgumentException(msg + " at " + pos);
    }

    Object value(int depth) {
      if (depth > 50) throw error("JSON too deeply nested");
      whitespace();
      if (pos == input.length()) throw error("Missing value");
      char c = input.charAt(pos);
      if (c == '"') return quoted();
      if (c == '{') {
        take();
        Map<String, Object> m = new LinkedHashMap<>();
        whitespace();
        if (pos < input.length() && input.charAt(pos) == '}') {
          pos++;
          return m;
        }
        do {
          whitespace();
          if (pos >= input.length() || input.charAt(pos) != '"') throw error("Expected key");
          String k = quoted();
          whitespace();
          if (take() != ':') throw error("Expected colon");
          if (m.containsKey(k)) throw error("Duplicate key");
          m.put(k, value(depth + 1));
          whitespace();
          char e = take();
          if (e == '}') return m;
          if (e != ',') throw error("Expected comma");
        } while (true);
      }
      if (c == '[') {
        take();
        List<Object> list = new ArrayList<>();
        whitespace();
        if (pos < input.length() && input.charAt(pos) == ']') {
          pos++;
          return list;
        }
        do {
          list.add(value(depth + 1));
          whitespace();
          char e = take();
          if (e == ']') return list;
          if (e != ',') throw error("Expected comma");
        } while (true);
      }
      for (String atom : List.of("true", "false", "null"))
        if (input.startsWith(atom, pos)) {
          pos += atom.length();
          return switch (atom) {
            case "true" -> true;
            case "false" -> false;
            default -> null;
          };
        }
      int start = pos;
      if (c == '-') pos++;
      while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
      if (pos == start || (pos == start + 1 && c == '-')) throw error("Unexpected character");
      if (pos < input.length()
          && (input.charAt(pos) == '.' || input.charAt(pos) == 'e' || input.charAt(pos) == 'E'))
        throw error("Floating point not supported");
      try {
        return Long.parseLong(input.substring(start, pos));
      } catch (NumberFormatException e) {
        throw error("Number out of range");
      }
    }

    String quoted() {
      if (take() != '"') throw error("Expected quote");
      StringBuilder sb = new StringBuilder();
      while (true) {
        char c = take();
        if (c == '"') return sb.toString();
        if (c < 32) throw error("Unescaped control character");
        if (c != '\\') {
          sb.append(c);
          continue;
        }
        char escaped = take();
        switch (escaped) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'u' -> {
            if (pos + 4 > input.length()) throw error("Bad unicode escape");
            try {
              sb.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
              pos += 4;
            } catch (NumberFormatException ex) {
              throw error("Bad unicode escape");
            }
          }
          default -> throw error("Bad escape");
        }
      }
    }
  }
}
