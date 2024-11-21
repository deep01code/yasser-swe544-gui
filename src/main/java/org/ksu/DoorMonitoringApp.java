package org.ksu;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DoorMonitoringApp {
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String GROUP_ID = "2";
    private static final String RUN_SCRIPT = "./restartInstance.sh";
    private static ZooKeeper zooKeeper;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        // Initialize Zookeeper connection in a separate thread
        executorService.submit(() -> {
            try {
                zooKeeper = new ZooKeeper("localhost:2181", 3000, null);
            } catch (Exception e) {
                System.err.println("Failed to connect to Zookeeper: " + e.getMessage());
            }
        });

        // Create and display the GUI
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Door Monitoring System");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1200, 800);
            frame.setLayout(new BorderLayout(10, 10));

            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new GridLayout(1, 4, 20, 0));
            frame.add(mainPanel, BorderLayout.CENTER);

            for (int i = 1; i <= 4; i++) {
                mainPanel.add(createDoorPanel(i));
            }

            JPanel buttonsPanel = new JPanel();
            buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));
            for (int i = 1; i <= 4; i++) {
                int doorNumber = i;
                JButton button = new JButton("Restart Door " + i + " Master");
                button.addActionListener(e -> restartMasterInstance(doorNumber));
                buttonsPanel.add(button);
            }
            frame.add(buttonsPanel, BorderLayout.SOUTH);

            frame.setVisible(true);
        });
    }

    private static JPanel createDoorPanel(int doorNumber) {
        JPanel doorPanel = new JPanel();
        doorPanel.setBorder(BorderFactory.createTitledBorder("Door " + doorNumber));
        doorPanel.setLayout(new BoxLayout(doorPanel, BoxLayout.Y_AXIS));
        doorPanel.setPreferredSize(new Dimension(200, 600));

        JLabel carCountLabel = new JLabel("Door " + doorNumber + " Car Count:");
        JTextField carCountField = new JTextField();
        carCountField.setEditable(false);
        carCountField.setMaximumSize(new Dimension(200, 30));

        JLabel masterInstanceLabel = new JLabel("Door " + doorNumber + " Master Instance:");
        JTextField masterInstanceField = new JTextField();
        masterInstanceField.setEditable(false);
        masterInstanceField.setMaximumSize(new Dimension(200, 30));

        String masterInstanceNodePath = "/door" + doorNumber;
        String carCountNodePath = "/door" + doorNumber + "counter";

        executorService.submit(() -> watchZookeeperNode(masterInstanceNodePath, masterInstanceField));
        executorService.submit(() -> watchZookeeperNode(carCountNodePath, carCountField));

        JLabel terminalLabel = new JLabel("Cars/Event Arrive -> Door" + doorNumber);
        JTextArea terminalOutput = new JTextArea();
        terminalOutput.setEditable(false);
        terminalOutput.setLineWrap(true);
        terminalOutput.setWrapStyleWord(true);
        JScrollPane terminalScrollPane = new JScrollPane(terminalOutput);
        terminalScrollPane.setPreferredSize(new Dimension(200, 150));

        String topic = "door" + doorNumber;
        executorService.submit(() -> startKafkaConsumer(topic, terminalOutput));

        doorPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        doorPanel.add(carCountLabel);
        doorPanel.add(carCountField);
        doorPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        doorPanel.add(masterInstanceLabel);
        doorPanel.add(masterInstanceField);
        doorPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        doorPanel.add(terminalLabel);
        doorPanel.add(terminalScrollPane);

        return doorPanel;
    }

    private static void startKafkaConsumer(String topic, JTextArea terminalOutput) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    SwingUtilities.invokeLater(() -> terminalOutput.append(record.value() + "\n"));
                    consumer.commitSync();
                }
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> terminalOutput.append("Error: " + e.getMessage() + "\n"));
        }
    }

    private static void watchZookeeperNode(String nodePath, JTextField field) {
        try {
            byte[] data = zooKeeper.getData(nodePath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
                    executorService.submit(() -> watchZookeeperNode(nodePath, field));
                }
            }, new Stat());

            SwingUtilities.invokeLater(() -> field.setText(new String(data)));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> field.setText("Error: " + e.getMessage()));
        }
    }

    private static void restartMasterInstance(int doorNumber) {
        executorService.submit(() -> {
            try {
                String masterInstance = "door" + doorNumber + "-instance1"; // Mock; replace with actual logic.
                String pid = findProcessByIdentifier(masterInstance);
                if (pid != null) {
                    killProcess(pid);
                    restartInstance(masterInstance);
                }
            } catch (Exception e) {
                System.err.println("Failed to restart: " + e.getMessage());
            }
        });
    }

    private static String findProcessByIdentifier(String identifier) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", "ps aux | grep " + identifier + " | grep -v grep");
        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 1) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    private static void killProcess(String pid) throws Exception {
        new ProcessBuilder("kill", "-9", pid).inheritIO().start().waitFor();
    }

    private static void restartInstance(String identifier) throws Exception {
        new ProcessBuilder(RUN_SCRIPT, identifier).inheritIO().start().waitFor();
    }
}
