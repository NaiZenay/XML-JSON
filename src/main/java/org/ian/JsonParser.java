package org.ian;

import java.util.*;

public class JsonParser {
    private String json;
    private int pos;

    public JsonParser(String json) {
        this.json = json.trim();
        this.pos = 0;
    }

    public String toXML() {
        StringBuilder result = new StringBuilder();
        result.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        result.append("<root>\n");

        Object parsed = parseValue();
        result.append(valueToXML(parsed, 1));

        result.append("</root>");
        return result.toString();
    }

    // Parsear cualquier valor JSON
    private Object parseValue() {
        skipWhitespace();

        if (pos >= json.length()) {
            return null;
        }

        char c = json.charAt(pos);

        if (c == '{') {
            return parseObject();
        } else if (c == '[') {
            return parseArray();
        } else if (c == '"') {
            return parseString();
        } else if (c == 't' || c == 'f') {
            return parseBoolean();
        } else if (c == 'n') {
            return parseNull();
        } else {
            return parseNumber();
        }
    }

    // Parsear objeto JSON
    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // Saltar '{'
        skipWhitespace();

        if (pos < json.length() && json.charAt(pos) == '}') {
            pos++; // Objeto vacío
            return map;
        }

        while (pos < json.length()) {
            skipWhitespace();

            // Leer clave
            if (json.charAt(pos) != '"') {
                break;
            }
            String key = parseString();

            skipWhitespace();

            // Verificar ':'
            if (pos >= json.length() || json.charAt(pos) != ':') {
                break;
            }
            pos++; // Saltar ':'

            skipWhitespace();

            // Leer valor (recursivo)
            Object value = parseValue();
            map.put(key, value);

            skipWhitespace();

            // Verificar si hay más propiedades
            if (pos >= json.length()) {
                break;
            }

            char next = json.charAt(pos);
            if (next == ',') {
                pos++; // Saltar ','
            } else if (next == '}') {
                pos++; // Fin del objeto
                break;
            }
        }

        return map;
    }

    // Parsear array JSON
    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++; // Saltar '['
        skipWhitespace();

        if (pos < json.length() && json.charAt(pos) == ']') {
            pos++; // Array vacío
            return list;
        }

        while (pos < json.length()) {
            skipWhitespace();

            // Leer elemento (recursivo)
            Object value = parseValue();
            list.add(value);

            skipWhitespace();

            if (pos >= json.length()) {
                break;
            }

            char next = json.charAt(pos);
            if (next == ',') {
                pos++; // Saltar ','
            } else if (next == ']') {
                pos++; // Fin del array
                break;
            }
        }

        return list;
    }

    // Parsear string
    private String parseString() {
        StringBuilder sb = new StringBuilder();
        pos++; // Saltar '"' inicial

        while (pos < json.length()) {
            char c = json.charAt(pos);

            if (c == '"') {
                pos++; // Saltar '"' final
                break;
            } else if (c == '\\') {
                pos++;
                if (pos < json.length()) {
                    char escaped = json.charAt(pos);
                    switch (escaped) {
                        case 'n':
                            sb.append('\n');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '"':
                            sb.append('"');
                            break;
                        default:
                            sb.append(escaped);
                    }
                    pos++;
                }
            } else {
                sb.append(c);
                pos++;
            }
        }

        return sb.toString();
    }

    // Parsear número
    private Object parseNumber() {
        int start = pos;

        if (pos < json.length() && json.charAt(pos) == '-') {
            pos++;
        }

        while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
            pos++;
        }

        if (pos < json.length() && json.charAt(pos) == '.') {
            pos++;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
        }

        String numStr = json.substring(start, pos);

        try {
            if (numStr.contains(".")) {
                return Double.parseDouble(numStr);
            } else {
                return Long.parseLong(numStr);
            }
        } catch (NumberFormatException e) {
            return numStr;
        }
    }

    // Parsear boolean
    private Boolean parseBoolean() {
        if (json.startsWith("true", pos)) {
            pos += 4;
            return true;
        } else if (json.startsWith("false", pos)) {
            pos += 5;
            return false;
        }
        return null;
    }

    // Parsear null
    private Object parseNull() {
        if (json.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        return null;
    }

    // Saltar espacios en blanco
    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }

    // Convertir valor a XML (recursivo con nivel de indentación)
    private String valueToXML(Object value, int level) {
        if (value == null) {
            return "";
        }

        if (value instanceof Map) {
            return mapToXML((Map<String, Object>) value, level);
        } else if (value instanceof List) {
            return listToXML((List<Object>) value, level, "item");
        } else {
            return escapeXML(String.valueOf(value));
        }
    }

    // Convertir Map a XML (recursivo)
    private String mapToXML(Map<String, Object> map, int level) {
        StringBuilder xml = new StringBuilder();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = sanitizeTagName(entry.getKey());
            Object value = entry.getValue();

            xml.append(indent(level));
            xml.append("<").append(key).append(">");

            if (value instanceof Map) {
                xml.append("\n");
                xml.append(valueToXML(value, level + 1));
                xml.append(indent(level));
                xml.append("</").append(key).append(">\n");
            } else if (value instanceof List) {
                xml.append("\n");
                xml.append(listToXML((List<Object>) value, level + 1, key));
                xml.append(indent(level));
                xml.append("</").append(key).append(">\n");
            } else {
                xml.append(valueToXML(value, level));
                xml.append("</").append(key).append(">\n");
            }
        }

        return xml.toString();
    }

    // Convertir List a XML (recursivo) con nombre de propiedad en singular
    private String listToXML(List<Object> list, int level, String propertyName) {
        StringBuilder xml = new StringBuilder();
        String singularName = toSingular(propertyName);

        for (Object item : list) {
            xml.append(indent(level));
            xml.append("<").append(singularName).append(">");

            if (item instanceof Map || item instanceof List) {
                xml.append("\n");
                xml.append(valueToXML(item, level + 1));
                xml.append(indent(level));
                xml.append("</").append(singularName).append(">\n");
            } else {
                xml.append(valueToXML(item, level));
                xml.append("</").append(singularName).append(">\n");
            }
        }

        return xml.toString();
    }

    // Convertir plural a singular (reglas básicas en español e inglés)
    private String toSingular(String plural) {
        if (plural == null || plural.isEmpty()) {
            return "item";
        }

        plural = plural.toLowerCase();

        // Reglas en español
        if (plural.endsWith("es")) {
            // hobbies -> hobby, caracteres -> caracter
            if (plural.endsWith("ies")) {
                return plural.substring(0, plural.length() - 3) + "y";
            }
            return plural.substring(0, plural.length() - 2);
        } else if (plural.endsWith("s")) {
            // empleados -> empleado, libros -> libro
            return plural.substring(0, plural.length() - 1);
        }

        // Si no termina en s/es, devolver como está
        return plural;
    }

    // Crear indentación
    private String indent(int level) {
        return "  ".repeat(level);
    }

    // Sanitizar nombre de etiqueta XML
    private String sanitizeTagName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // Escapar caracteres especiales XML
    private String escapeXML(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // Método de depuración para ver propiedades parseadas
    public void printProperties() {
        Object parsed = parseValue();
        printValue(parsed, 0);
    }

    private void printValue(Object value, int level) {
        String indent = "  ".repeat(level);

        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            System.out.println(indent + "{");
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                System.out.print(indent + "  \"" + entry.getKey() + "\": ");
                printValue(entry.getValue(), level + 1);
            }
            System.out.println(indent + "}");
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            System.out.println(indent + "[");
            for (Object item : list) {
                printValue(item, level + 1);
            }
            System.out.println(indent + "]");
        } else {
            System.out.println(value);
        }
    }

    public static boolean validateJSON(String json){
        if(json.isEmpty()){
            return false;
        }
        try {
            new org.json.JSONObject(json);
            return true;
        } catch (org.json.JSONException e1) {
            try {
                new org.json.JSONArray(json);
                return true;
            } catch (org.json.JSONException e2) {
                return false;
            }
        }
    }

}