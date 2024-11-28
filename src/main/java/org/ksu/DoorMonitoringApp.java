package org.ksu;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DoorMonitoringApp {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Parking Area Management");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set size to 80% of the screen width and height
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.8);
        int height = (int) (screenSize.height * 0.8);
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);

        // Create the main container with a border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top section for Current Cars Count
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel currentCarsLabel = new JLabel("Current Cars Count:");
        JTextField currentCarsField = new JTextField(40); // Wider text field
        currentCarsField.setText("0"); // Default value
        currentCarsField.setEditable(false);
        startKafkaConsumer(currentCarsField, "CARS_COUNTER");
        topPanel.add(currentCarsLabel);
        topPanel.add(currentCarsField);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center section for doors
        JPanel doorsPanel = new JPanel(new GridLayout(2, 2, 10, 10));

        // Create 4 door panels
        for (int i = 1; i <= 4; i++) {
            JPanel doorPanel = new JPanel();
            doorPanel.setBorder(BorderFactory.createTitledBorder("Door " + i));
            doorPanel.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Add "Last Cross IN" label and text field
            gbc.gridx = 0;
            gbc.gridy = 0;
            doorPanel.add(new JLabel("Last Cross IN:"), gbc);

            gbc.gridx = 1;
            gbc.gridy = 0;
            JTextField lastCrossInField = new JTextField(40); // Wider text field
            lastCrossInField.setEditable(false);
            lastCrossInField.setText("text value");
            startKafkaConsumer(lastCrossInField, "DOOR_" + i + "_CROSS_IN");
            doorPanel.add(lastCrossInField, gbc);

            // Add "Last Cross OUT" label and text field
            gbc.gridx = 0;
            gbc.gridy = 1;
            doorPanel.add(new JLabel("Last Cross OUT:"), gbc);

            gbc.gridx = 1;
            gbc.gridy = 1;
            JTextField lastCrossOutField = new JTextField(40); // Wider text field
            lastCrossOutField.setEditable(false);
            lastCrossOutField.setText("text value");
            startKafkaConsumer(lastCrossOutField, "DOOR_" + i + "_CROSS_OUT");
            doorPanel.add(lastCrossOutField, gbc);

            // Add a title for the notifications area
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            doorPanel.add(new JLabel("Notifications:"), gbc);

            // Add a scrollable area for notifications
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.gridwidth = 2;
            JTextArea notificationsArea = new JTextArea(5, 50);
            notificationsArea.setText("......");
            notificationsArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(notificationsArea);
            startKafkaConsumer(notificationsArea, "DOOR_" + i + "_NOTIFICATION");
            doorPanel.add(scrollPane, gbc);

            // Add the door panel to the doors panel
            doorsPanel.add(doorPanel);
        }

        mainPanel.add(doorsPanel, BorderLayout.CENTER);

        // Add main panel to frame and make it visible
        frame.add(mainPanel);
        frame.setVisible(true);
    }

    // ExecutorService with high-priority threads
    private static final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setPriority(Thread.MAX_PRIORITY); // Set the thread priority to maximum
        return thread;
    });

    private static void startKafkaConsumer(JComponent component, String topicName) {
        // Submit Kafka consumer task to the executor
        executor.submit(() -> {
            // Kafka consumer properties
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "door-monitoring-group");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
            props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000"); // 10 seconds (default: 45 seconds)
            props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "3000"); // 3 seconds (default: 3 seconds)
            props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "30000"); // 30 seconds (default: 5 minutes)
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Collections.singletonList(topicName));

            try {
                while (true) {
                    // Poll for new Kafka messages
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    records.forEach(record -> {
                        // Update the component (JTextField or JTextArea) on the Event Dispatch Thread
                        if (component instanceof JTextField) {
                            ((JTextField) component).setText(record.value());
                        } else if (component instanceof JTextArea) {
                            JTextArea textArea = (JTextArea) component;
                            textArea.append(record.value() + "\n");
                            textArea.setCaretPosition(textArea.getDocument().getLength());
                        }
                        component.repaint(); // Explicitly request repaint
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                consumer.close();
            }
        });
    }

}
