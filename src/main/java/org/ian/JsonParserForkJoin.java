package org.ian;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class JsonParserForkJoin {

    private final ForkJoinPool forkJoinPool;
    private final ExecutionMonitor monitor;

    // Umbral para decidir cuándo dividir el trabajo
    private static final int THRESHOLD_MAP_SIZE = 5;
    private static final int THRESHOLD_LIST_SIZE = 10;

    public JsonParserForkJoin() {
        this(ForkJoinPool.commonPool());
    }

    public JsonParserForkJoin(int parallelism) {
        this.forkJoinPool = new ForkJoinPool(parallelism);
        this.monitor = new ExecutionMonitor();
    }

    public JsonParserForkJoin(ForkJoinPool pool) {
        this.forkJoinPool = pool;
        this.monitor = new ExecutionMonitor();
    }

    // Convertir JSON a XML usando ForkJoin
    public String toXML(String json) {
        monitor.reset();
        monitor.startExecution();

        try {
            JsonParseTask parseTask = new JsonParseTask(json.trim(), monitor);
            Object parsed = forkJoinPool.invoke(parseTask);

            XmlConversionTask conversionTask = new XmlConversionTask(parsed, "root", 1, monitor);
            String xmlContent = forkJoinPool.invoke(conversionTask);

            StringBuilder result = new StringBuilder();
            result.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            result.append("<root>\n");
            result.append(xmlContent);
            result.append("</root>");

            return result.toString();
        } finally {
            monitor.endExecution();
        }
    }

    // Obtener monitor de ejecución
    public ExecutionMonitor getMonitor() {
        return monitor;
    }

    // Obtener estadísticas del pool
    public PoolStats getPoolStats() {
        return new PoolStats(forkJoinPool);
    }

    // Cerrar el pool
    public void shutdown() {
        if (!forkJoinPool.isShutdown()) {
            forkJoinPool.shutdown();
            try {
                if (!forkJoinPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    forkJoinPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                forkJoinPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ========== TAREA DE PARSEO JSON ==========
    private static class JsonParseTask extends RecursiveTask<Object> {
        private final String json;
        private final ExecutionMonitor monitor;
        private int pos;

        public JsonParseTask(String json, ExecutionMonitor monitor) {
            this.json = json;
            this.monitor = monitor;
            this.pos = 0;
        }

        @Override
        protected Object compute() {
            monitor.incrementTasksCreated();
            monitor.incrementActiveThreads();

            try {
                return parseValue();
            } finally {
                monitor.decrementActiveThreads();
                monitor.incrementTasksCompleted();
            }
        }

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

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();

            if (pos < json.length() && json.charAt(pos) == '}') {
                pos++;
                return map;
            }

            List<RecursiveTask<Map.Entry<String, Object>>> subtasks = new ArrayList<>();

            while (pos < json.length()) {
                skipWhitespace();

                if (json.charAt(pos) != '"') break;
                String key = parseString();

                skipWhitespace();
                if (pos >= json.length() || json.charAt(pos) != ':') break;
                pos++;
                skipWhitespace();

                int valueStart = pos;
                Object value = parseValue();

                map.put(key, value);

                skipWhitespace();
                if (pos >= json.length()) break;

                char next = json.charAt(pos);
                if (next == ',') {
                    pos++;
                } else if (next == '}') {
                    pos++;
                    break;
                }
            }

            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();

            if (pos < json.length() && json.charAt(pos) == ']') {
                pos++;
                return list;
            }

            while (pos < json.length()) {
                skipWhitespace();
                Object value = parseValue();
                list.add(value);

                skipWhitespace();
                if (pos >= json.length()) break;

                char next = json.charAt(pos);
                if (next == ',') {
                    pos++;
                } else if (next == ']') {
                    pos++;
                    break;
                }
            }

            return list;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            pos++;

            while (pos < json.length()) {
                char c = json.charAt(pos);

                if (c == '"') {
                    pos++;
                    break;
                } else if (c == '\\') {
                    pos++;
                    if (pos < json.length()) {
                        char escaped = json.charAt(pos);
                        switch (escaped) {
                            case 'n': sb.append('\n'); break;
                            case 't': sb.append('\t'); break;
                            case 'r': sb.append('\r'); break;
                            case '\\': sb.append('\\'); break;
                            case '"': sb.append('"'); break;
                            default: sb.append(escaped);
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

        private Object parseNull() {
            if (json.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return null;
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }
    }

    // ========== TAREA DE CONVERSIÓN A XML ==========
    private static class XmlConversionTask extends RecursiveTask<String> {
        private final Object value;
        private final String tagName;
        private final int level;
        private final ExecutionMonitor monitor;

        public XmlConversionTask(Object value, String tagName, int level, ExecutionMonitor monitor) {
            this.value = value;
            this.tagName = tagName;
            this.level = level;
            this.monitor = monitor;
        }

        @Override
        protected String compute() {
            monitor.incrementTasksCreated();
            monitor.incrementActiveThreads();

            try {
                if (value == null) {
                    return "";
                }

                if (value instanceof Map) {
                    return convertMap((Map<String, Object>) value);
                } else if (value instanceof List) {
                    return convertList((List<Object>) value);
                } else {
                    return escapeXML(String.valueOf(value));
                }
            } finally {
                monitor.decrementActiveThreads();
                monitor.incrementTasksCompleted();
            }
        }

        private String convertMap(Map<String, Object> map) {
            StringBuilder xml = new StringBuilder();

            if (map.size() > THRESHOLD_MAP_SIZE) {
                // Procesamiento paralelo
                List<RecursiveTask<String>> subtasks = new ArrayList<>();

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String key = sanitizeTagName(entry.getKey());
                    Object val = entry.getValue();

                    RecursiveTask<String> task = new RecursiveTask<String>() {
                        @Override
                        protected String compute() {
                            StringBuilder sb = new StringBuilder();
                            sb.append(indent(level));
                            sb.append("<").append(key).append(">");

                            if (val instanceof Map) {
                                sb.append("\n");
                                XmlConversionTask subtask = new XmlConversionTask(val, key, level + 1, monitor);
                                sb.append(subtask.compute());
                                sb.append(indent(level));
                                sb.append("</").append(key).append(">\n");
                            } else if (val instanceof List) {
                                sb.append("\n");
                                sb.append(convertListWithName((List<Object>) val, key));
                                sb.append(indent(level));
                                sb.append("</").append(key).append(">\n");
                            } else {
                                XmlConversionTask subtask = new XmlConversionTask(val, key, level, monitor);
                                sb.append(subtask.compute());
                                sb.append("</").append(key).append(">\n");
                            }

                            return sb.toString();
                        }
                    };

                    subtasks.add(task);
                    task.fork();
                }

                for (RecursiveTask<String> task : subtasks) {
                    xml.append(task.join());
                }
            } else {
                // Procesamiento secuencial
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String key = sanitizeTagName(entry.getKey());
                    Object val = entry.getValue();

                    xml.append(indent(level));
                    xml.append("<").append(key).append(">");

                    if (val instanceof Map) {
                        xml.append("\n");
                        XmlConversionTask subtask = new XmlConversionTask(val, key, level + 1, monitor);
                        xml.append(subtask.compute());
                        xml.append(indent(level));
                        xml.append("</").append(key).append(">\n");
                    } else if (val instanceof List) {
                        xml.append("\n");
                        xml.append(convertListWithName((List<Object>) val, key));
                        xml.append(indent(level));
                        xml.append("</").append(key).append(">\n");
                    } else {
                        XmlConversionTask subtask = new XmlConversionTask(val, key, level, monitor);
                        xml.append(subtask.compute());
                        xml.append("</").append(key).append(">\n");
                    }
                }
            }

            return xml.toString();
        }

        private String convertList(List<Object> list) {
            return convertListWithName(list, "item");
        }

        private String convertListWithName(List<Object> list, String propertyName) {
            StringBuilder xml = new StringBuilder();
            String singularName = toSingular(propertyName);

            if (list.size() > THRESHOLD_LIST_SIZE) {
                // Procesamiento paralelo
                List<RecursiveTask<String>> subtasks = new ArrayList<>();

                for (Object item : list) {
                    RecursiveTask<String> task = new RecursiveTask<String>() {
                        @Override
                        protected String compute() {
                            StringBuilder sb = new StringBuilder();
                            sb.append(indent(level + 1));
                            sb.append("<").append(singularName).append(">");

                            if (item instanceof Map || item instanceof List) {
                                sb.append("\n");
                                XmlConversionTask subtask = new XmlConversionTask(item, singularName, level + 2, monitor);
                                sb.append(subtask.compute());
                                sb.append(indent(level + 1));
                                sb.append("</").append(singularName).append(">\n");
                            } else {
                                XmlConversionTask subtask = new XmlConversionTask(item, singularName, level + 1, monitor);
                                sb.append(subtask.compute());
                                sb.append("</").append(singularName).append(">\n");
                            }

                            return sb.toString();
                        }
                    };

                    subtasks.add(task);
                    task.fork();
                }

                for (RecursiveTask<String> task : subtasks) {
                    xml.append(task.join());
                }
            } else {
                // Procesamiento secuencial
                for (Object item : list) {
                    xml.append(indent(level + 1));
                    xml.append("<").append(singularName).append(">");

                    if (item instanceof Map || item instanceof List) {
                        xml.append("\n");
                        XmlConversionTask subtask = new XmlConversionTask(item, singularName, level + 2, monitor);
                        xml.append(subtask.compute());
                        xml.append(indent(level + 1));
                        xml.append("</").append(singularName).append(">\n");
                    } else {
                        XmlConversionTask subtask = new XmlConversionTask(item, singularName, level + 1, monitor);
                        xml.append(subtask.compute());
                        xml.append("</").append(singularName).append(">\n");
                    }
                }
            }

            return xml.toString();
        }

        private String indent(int lvl) {
            return "  ".repeat(lvl);
        }

        private String sanitizeTagName(String name) {
            return name.replaceAll("[^a-zA-Z0-9_-]", "_");
        }

        private String escapeXML(String text) {
            return text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }

        private String toSingular(String plural) {
            if (plural == null || plural.isEmpty()) {
                return "item";
            }

            plural = plural.toLowerCase();

            if (plural.endsWith("ies")) {
                return plural.substring(0, plural.length() - 3) + "y";
            } else if (plural.endsWith("es")) {
                return plural.substring(0, plural.length() - 2);
            } else if (plural.endsWith("s")) {
                return plural.substring(0, plural.length() - 1);
            }

            return plural;
        }
    }

    // ========== MONITOR DE EJECUCIÓN ==========
    public static class ExecutionMonitor {
        private final AtomicInteger tasksCreated = new AtomicInteger(0);
        private final AtomicInteger tasksCompleted = new AtomicInteger(0);
        private final AtomicInteger activeThreads = new AtomicInteger(0);
        private final AtomicLong startTime = new AtomicLong(0);
        private final AtomicLong endTime = new AtomicLong(0);
        private volatile boolean isExecuting = false;

        public void reset() {
            tasksCreated.set(0);
            tasksCompleted.set(0);
            activeThreads.set(0);
            startTime.set(0);
            endTime.set(0);
            isExecuting = false;
        }

        public void startExecution() {
            isExecuting = true;
            startTime.set(System.currentTimeMillis());
        }

        public void endExecution() {
            endTime.set(System.currentTimeMillis());
            isExecuting = false;
        }

        void incrementTasksCreated() {
            tasksCreated.incrementAndGet();
        }

        void incrementTasksCompleted() {
            tasksCompleted.incrementAndGet();
        }

        void incrementActiveThreads() {
            activeThreads.incrementAndGet();
        }

        void decrementActiveThreads() {
            activeThreads.decrementAndGet();
        }

        public int getTasksCreated() {
            return tasksCreated.get();
        }

        public int getTasksCompleted() {
            return tasksCompleted.get();
        }

        public int getActiveThreads() {
            return activeThreads.get();
        }

        public long getExecutionTimeMs() {
            if (isExecuting) {
                return System.currentTimeMillis() - startTime.get();
            }
            return endTime.get() - startTime.get();
        }

        public boolean isExecuting() {
            return isExecuting;
        }

        public double getProgress() {
            int created = tasksCreated.get();
            if (created == 0) return 0.0;
            return (double) tasksCompleted.get() / created * 100.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "ExecutionMonitor[tareas=%d/%d, activos=%d, tiempo=%dms, progreso=%.1f%%]",
                    tasksCompleted.get(), tasksCreated.get(), activeThreads.get(),
                    getExecutionTimeMs(), getProgress()
            );
        }
    }

public static class PoolStats {
    private final int parallelism;
    private final int poolSize;
    private final int activeThreadCount;
    private final int runningThreadCount;
    private final long queuedSubmissionCount;
    private final long queuedTaskCount;
    private final long stealCount;
    private final boolean isQuiescent;

    public PoolStats(ForkJoinPool pool) {
        this.parallelism = pool.getParallelism();
        this.poolSize = pool.getPoolSize();
        this.activeThreadCount = pool.getActiveThreadCount();
        this.runningThreadCount = pool.getRunningThreadCount();
        this.queuedSubmissionCount = pool.getQueuedSubmissionCount();
        this.queuedTaskCount = pool.getQueuedTaskCount();
        this.stealCount = pool.getStealCount();
        this.isQuiescent = pool.isQuiescent();
    }

    public int getParallelism() { return parallelism; }
    public int getPoolSize() { return poolSize; }
    public int getActiveThreadCount() { return activeThreadCount; }
    public int getRunningThreadCount() { return runningThreadCount; }
    public long getQueuedSubmissionCount() { return queuedSubmissionCount; }
    public long getQueuedTaskCount() { return queuedTaskCount; }
    public long getStealCount() { return stealCount; }
    public boolean isQuiescent() { return isQuiescent; }

    @Override
    public String toString() {
        return String.format(
                "PoolStats[parallelism=%d, poolSize=%d, active=%d, running=%d, " +
                        "queuedSubmissions=%d, queuedTasks=%d, steals=%d, quiescent=%s]",
                parallelism, poolSize, activeThreadCount, runningThreadCount,
                queuedSubmissionCount, queuedTaskCount, stealCount, isQuiescent
        );
    }
}}