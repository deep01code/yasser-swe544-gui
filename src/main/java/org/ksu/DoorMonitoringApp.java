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
import java.awt.List;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.*;
public class DoorMonitoringApp {
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String GROUP_ID = "2";
    private static final String RUN_SCRIPT = "./restartInstance.sh";
    private static final String RUN_SCRIPT_RUN_CLUSTER = "./runCluster.sh";
    private static final String RUN_SCRIPT_SHUTDOWNALL = "./shutdownCluster.sh";
    private static ZooKeeper zooKeeper;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Map<Integer, JTextField> masterInstanceFields = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) throws InterruptedException {

        runCluster();
        Thread.sleep(3000);
        // Initialize Zookeeper connection in a separate thread
        executorService.submit(() -> {
            try {
                zooKeeper = new ZooKeeper("localhost:2181", 30000, null);
            } catch (Exception e) {
                System.err.println("Failed to connect to Zookeeper: " + e.getMessage());
            }
        });

        // Create and display the GUI
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Door Monitoring System");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Set frame to take up 90% of screen size
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int width = (int) (screenSize.width * 0.9);
            int height = (int) (screenSize.height * 0.9);
            frame.setSize(width, height);
            frame.setLayout(new BorderLayout(10, 10));

            // Main panel for door panels
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new GridLayout(1, 4, 20, 0));
            for (int i = 1; i <= 4; i++) {
                mainPanel.add(createDoorPanel(i));
            }
            frame.add(new JScrollPane(mainPanel), BorderLayout.CENTER);

            // Buttons panel
            JPanel buttonsPanel = new JPanel();
            buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));

            // Add Restart Cluster buttons
            for (int i = 1; i <= 4; i++) {
                int doorNumber = i; // Capture doorNumber for lambda
                JButton button = new JButton("Restart Cluster Master  " + doorNumber);
                button.addActionListener(e -> restartMasterInstance(doorNumber));
                buttonsPanel.add(button);
            }

            // Add new buttons for scripts
            JButton buttonScript1 = new JButton("Run All Nodes");
            buttonScript1.setPreferredSize(new Dimension(150, 40));
            buttonScript1.addActionListener(e -> runCluster());

            JButton buttonScript2 = new JButton("Shutdown All Nodes");
            buttonScript2.setPreferredSize(new Dimension(150, 40));
            buttonScript2.addActionListener(e -> shutdownAllClusters());

            buttonsPanel.add(buttonScript1);
            buttonsPanel.add(buttonScript2);

            frame.add(buttonsPanel, BorderLayout.SOUTH);

            // Add the process monitor panel
            JPanel processMonitorPanel = createProcessMonitorPanel();
            frame.add(processMonitorPanel, BorderLayout.EAST);

            // Ensure components resize properly
            frame.setMinimumSize(new Dimension(800, 600));
            frame.setVisible(true);
        });


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownAllClusters();
            //put your logic here

            try {
                if (zooKeeper != null) {
                    zooKeeper.close();
                }
                executorService.shutdownNow();
                scheduler.shutdownNow();
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));
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

        // Save the masterInstanceField for this doorNumber in the map
        masterInstanceFields.put(doorNumber, masterInstanceField);

        String masterInstanceNodePath = "/door" + doorNumber;
        String carCountNodePath = "/door" + doorNumber + "counter";

        // Set up persistent watchers and initialize fields
        executorService.submit(() -> addPersistentWatcher(masterInstanceNodePath, masterInstanceField));
        executorService.submit(() -> addPersistentWatcher(carCountNodePath, carCountField));

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

    private static void addPersistentWatcher(String nodePath, JTextField field) {
        try {
            // Fetch initial value to populate the field
            byte[] initialData = zooKeeper.getData(nodePath, false, new Stat());
            SwingUtilities.invokeLater(() -> field.setText(new String(initialData)));

            // Add persistent watcher
            zooKeeper.addWatch(nodePath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDataChanged
                        || event.getType() == Watcher.Event.EventType.NodeCreated
                        || event.getType() == Watcher.Event.EventType.NodeDeleted
                ) {
                    try {
                        byte[] updatedData = zooKeeper.getData(nodePath, false, new Stat());
                        SwingUtilities.invokeLater(() -> field.setText(new String(updatedData)));
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> field.setText("Error: " + e.getMessage()));
                    }
                }
            }, AddWatchMode.PERSISTENT);
        } catch (KeeperException.NoNodeException e) {
            SwingUtilities.invokeLater(() -> field.setText("Node not found"));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> field.setText("Error: " + e.getMessage()));
        }
    }

    private static void restartMasterInstance(int doorNumber) {
        executorService.submit(() -> {
            try {
                // Dynamically fetch the content of the associated masterInstanceField
                JTextField masterInstanceField = masterInstanceFields.get(doorNumber);
                if (masterInstanceField == null) {
                    System.err.println("No master instance field found for Door " + doorNumber);
                    return;
                }

                String masterInstanceNumber = masterInstanceField.getText();
                if (masterInstanceNumber == null || masterInstanceNumber.isEmpty()) {
                    System.err.println("Master instance value is empty for Door " + doorNumber);
                    return;
                }

                System.out.println("Attempting to restart instance: " + masterInstanceNumber);
                String PID_Identifier="door"+doorNumber+"-instance"+masterInstanceNumber;
                // Identify and kill the process
                String pid = findProcessByIdentifier(PID_Identifier);
                if (pid != null) {
                    System.out.println("Found process PID: " + pid + " for " + PID_Identifier);
                    //  killProcess(pid);
                } else {
                    System.out.println("No process found for " + masterInstanceNumber);
                }

                // Restart the instance
                restartInstance(PID_Identifier);
                System.out.println(masterInstanceNumber + " has been restarted.");
            } catch (Exception e) {
                System.err.println("Failed to restart instance: " + e.getMessage());
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

    private static void runCluster() {
        try {
            new ProcessBuilder(RUN_SCRIPT_RUN_CLUSTER).inheritIO().start().waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void shutdownAllClusters()  {
        try {
            new ProcessBuilder(RUN_SCRIPT_SHUTDOWNALL).inheritIO().start().waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//todo

    private static JPanel createProcessMonitorPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Java Process Monitor"));
        panel.setLayout(new BorderLayout());

        JTextArea processOutput = new JTextArea();
        processOutput.setEditable(false);
        processOutput.setLineWrap(true);
        processOutput.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(processOutput);

        panel.add(scrollPane, BorderLayout.CENTER);

        // Schedule periodic updates every second
        scheduler.scheduleAtFixedRate(() -> {
            String processes = fetchJavaProcesses();
            SwingUtilities.invokeLater(() -> processOutput.setText(processes));
        }, 0, 1, TimeUnit.SECONDS);

        return panel;
    }

    private static String fetchJavaProcesses() {
        class ProcessInfo {
            String pid;
            String identifier;

            ProcessInfo(String pid, String identifier) {
                this.pid = pid;
                this.identifier = identifier;
            }

            @Override
            public String toString() {
                return "PID: " + pid + " | " + identifier;
            }
        }

        ArrayList<ProcessInfo> processes = new ArrayList<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", "ps aux | grep java | grep -v grep");
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 10) {
                        String pid = parts[1]; // PID is in the second column
                        String identifier = extractIdentifier(line); // Extract -DIDENTIFIER value
                        if (identifier != null) {
                            processes.add(new ProcessInfo(pid, identifier));
                        }
                    }
                }
            }

            // Sort the processes by PID (numeric order)
            processes.sort(Comparator.comparingInt(p -> Integer.parseInt(p.pid)));

        } catch (Exception e) {
            return "Error fetching processes: " + e.getMessage();
        }

        // Build the output string
        StringBuilder output = new StringBuilder();
        for (ProcessInfo process : processes) {
            output.append(process).append("\n");
        }

        return output.toString();
    }

    private static String extractIdentifier(String processLine) {
        String[] parts = processLine.split("\\s+");
        for (String part : parts) {
            if (part.startsWith("-DIDENTIFIER=")) {
                return part; // Return the entire -DIDENTIFIER=value
            }
        }
        return null;
    }

}