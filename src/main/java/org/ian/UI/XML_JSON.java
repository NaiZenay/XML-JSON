package org.ian.UI;

import org.ian.JsonParser;
import org.ian.JsonParserForkJoin;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public class XML_JSON extends JFrame{
    private JPanel JPMain;
    private JButton JBConvertion;
    private JTextArea JTAJson;
    private JLabel JLMessage;
    private JButton JBConcurrentConvertion;
    private JButton JBCopy;
    private JLabel JLNormalTime;
    private JLabel JLNormalStatus;
    private JLabel JLConcurrentTime;
    private JLabel JLConcurrentTaks;
    private JLabel JLConcurrentActive;
    private JLabel JLConcurrentProgress;
    private JLabel JLPoolParallelism;
    private JLabel JLPoolSize;
    private JLabel JLPoolActive;
    private JLabel JLPoolRunning;
    private JLabel JLPoolSteals;

    private String xml;
    private JsonParser jp;

    private JsonParserForkJoin jpFJ;


    private Timer monitoringTimer;
    private long normalStartTime;
    private long normalEndTime;
    private long concurrentStartTime;
    private long concurrentEndTime;


    public XML_JSON() throws HeadlessException {
        this.setContentPane(JPMain);
        this.setSize(1500,700);
        this.setLocationRelativeTo(null);
        this.setVisible(true);

        JBConvertion.setFocusable(false);
        this.setupListeners();
    }

    private void setupListeners() {
        JBConvertion.addActionListener(e -> convertNormal());
        JBConcurrentConvertion.addActionListener(e -> convertConcurrent());
        JBCopy.addActionListener(e -> copyToClipboard());
    }

    private void copyToClipboard(){
        try {
            StringSelection seleccion = new StringSelection(xml);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(seleccion, null);
            showMessage("Copiado");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void convertConcurrent() {
        if (!JsonParser.validateJSON(this.JTAJson.getText())) {
            showMessage("JSON inválido o vacío");
            return;
        }

        JBConvertion.setEnabled(false);
        JBConcurrentConvertion.setEnabled(false);

        // Resetear estadísticas
        resetConcurrentStats();

        // Ejecutar en hilo separado
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                jpFJ = new JsonParserForkJoin();
                concurrentStartTime = System.currentTimeMillis();

                // Iniciar monitoreo en tiempo real
                startMonitoring();

                String result = jpFJ.toXML(JTAJson.getText());
                concurrentEndTime = System.currentTimeMillis();

                return result;
            }

            @Override
            protected void done() {
                try {
                    xml = get();
                    stopMonitoring();

                    // Actualizar estadísticas finales
                    updateConcurrentStats(true);

                    showMessage("XML listo (Concurrente)");
                    JBCopy.setEnabled(true);
                } catch (Exception ex) {
                    stopMonitoring();
                    showMessage("Error en conversión: " + ex.getMessage());
                } finally {
                    JBConvertion.setEnabled(true);
                    JBConcurrentConvertion.setEnabled(true);
                    if (jpFJ != null) {
                        jpFJ.shutdown();
                    }
                }
            }
        };

        worker.execute();
    }
    private void convertNormal() {
        if (!JsonParser.validateJSON(this.JTAJson.getText())) {
            showMessage("JSON inválido o vacío");
            return;
        }

        JBConvertion.setEnabled(false);
        JBConcurrentConvertion.setEnabled(false);

        JLNormalStatus.setText("Estado: Ejecutando...");
        JLNormalStatus.setForeground(Color.ORANGE);

        // Ejecutar en hilo separado para no bloquear UI
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                normalStartTime = System.currentTimeMillis();
                jp = new JsonParser(JTAJson.getText());
                String result = jp.toXML();
                normalEndTime = System.currentTimeMillis();
                return result;
            }

            @Override
            protected void done() {
                try {
                    xml = get();
                    long duration = normalEndTime - normalStartTime;

                    JLNormalStatus.setText("Estado: Completado");
                    JLNormalStatus.setForeground(new Color(0, 128, 0));
                    JLNormalTime.setText("Tiempo: " + duration + " ms");

                    showMessage("XML listo (Normal)");
                    JBCopy.setEnabled(true);
                } catch (Exception ex) {
                    JLNormalStatus.setText("Estado: Error");
                    JLNormalStatus.setForeground(Color.RED);
                    showMessage("Error en conversión: " + ex.getMessage());
                } finally {
                    JBConvertion.setEnabled(true);
                    JBConcurrentConvertion.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void showMessage(String message){
        JLMessage.setText(message);
        Timer timer = new Timer(2000, e -> JLMessage.setText(""));
        timer.setRepeats(false);
        timer.start();

    }

    private void startMonitoring() {
        monitoringTimer = new Timer(50, e -> updateConcurrentStats(false));
        monitoringTimer.start();
    }

    private void stopMonitoring() {
        if (monitoringTimer != null) {
            monitoringTimer.stop();
        }
    }

    private void resetConcurrentStats() {
        JLConcurrentTime.setText("Tiempo: 0 ms");
        JLConcurrentTaks.setText("Tareas: 0/0");
        JLConcurrentActive.setText("Hilos activos: 0");
        JLConcurrentProgress.setText("Progreso: 0%");
        JLPoolParallelism.setText("Paralelismo: -");
        JLPoolSize.setText("Tamaño pool: -");
        JLPoolActive.setText("Activos: -");
        JLPoolRunning.setText("Ejecutando: -");
        JLPoolSteals.setText("Robos: -");
    }

    private void updateConcurrentStats(boolean isFinal) {
        if (jpFJ == null) return;

        JsonParserForkJoin.ExecutionMonitor monitor = jpFJ.getMonitor();
        JsonParserForkJoin.PoolStats poolStats = jpFJ.getPoolStats();

        long currentTime = isFinal ?
                (concurrentEndTime - concurrentStartTime) :
                monitor.getExecutionTimeMs();

        JLConcurrentTime.setText("Tiempo: " + currentTime + " ms");
        JLConcurrentTaks.setText("Tareas: " + monitor.getTasksCompleted() +
                "/" + monitor.getTasksCreated());
        JLConcurrentActive.setText("Hilos activos: " + monitor.getActiveThreads());

        double progress = monitor.getProgress();
        JLConcurrentProgress.setText(String.format("Progreso: %.1f%%", progress));
        // Estadísticas del pool
        JLPoolParallelism.setText("Paralelismo: " + poolStats.getParallelism());
        JLPoolSize.setText("Tamaño pool: " + poolStats.getPoolSize());
        JLPoolActive.setText("Activos: " + poolStats.getActiveThreadCount());
        JLPoolRunning.setText("Ejecutando: " + poolStats.getRunningThreadCount());
        JLPoolSteals.setText("Robos: " + poolStats.getStealCount());
    }

}
