import javax.swing.*;
import java.awt.*;

public class DoorMonitoringApp {
    public static void main(String[] args) {
        // Create the main frame
        JFrame frame = new JFrame("Door Monitoring System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800); // Adjusted frame size for better proportions
        frame.setLayout(new BorderLayout(10, 10)); // Added spacing

        // Create the main panel with GridLayout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayout(1, 4, 20, 0)); // 4 columns, spacing between panels
        frame.add(mainPanel, BorderLayout.CENTER);

        // Add panels for each door
        for (int i = 1; i <= 4; i++) {
            mainPanel.add(createDoorPanel(i));
        }

        // Create the buttons panel at the bottom
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10)); // Centered with spacing
        for (int i = 1; i <= 4; i++) {
            JButton button = new JButton("Button " + i);
            button.setPreferredSize(new Dimension(100, 40)); // Uniform button size
            buttonsPanel.add(button);
        }
        frame.add(buttonsPanel, BorderLayout.SOUTH);

        // Show the frame
        frame.setVisible(true);
    }

    private static JPanel createDoorPanel(int doorNumber) {
        // Panel for a single door
        JPanel doorPanel = new JPanel();
        doorPanel.setBorder(BorderFactory.createTitledBorder("Door " + doorNumber));
        doorPanel.setLayout(new BoxLayout(doorPanel, BoxLayout.Y_AXIS));
        doorPanel.setPreferredSize(new Dimension(200, 600)); // Adjusted panel size

        // Label and text fields
        JLabel carCountLabel = new JLabel("Door " + doorNumber + " Car Count:");
        JTextField carCountField = new JTextField("3");
        carCountField.setEditable(false);
        carCountField.setMaximumSize(new Dimension(200, 30)); // Uniform size for fields

        JLabel masterInstanceLabel = new JLabel("Door " + doorNumber + " Master Instance:");
        JTextField masterInstanceField = new JTextField("1");
        masterInstanceField.setEditable(false);
        masterInstanceField.setMaximumSize(new Dimension(200, 30)); // Uniform size for fields

        // Text area for terminal output
        JLabel terminalLabel = new JLabel("Cars/Event Arrive -> Door" + doorNumber);
        JTextArea terminalOutput = new JTextArea(
                "some text fetched from terminal from\n" +
                        "command \"./kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic test-topic\"");
        terminalOutput.setEditable(false);
        terminalOutput.setLineWrap(true);
        terminalOutput.setWrapStyleWord(true);
        JScrollPane terminalScrollPane = new JScrollPane(terminalOutput);
        terminalScrollPane.setPreferredSize(new Dimension(200, 150)); // Adjusted text area size

        // Add components to the door panel
        doorPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Spacer
        doorPanel.add(carCountLabel);
        doorPanel.add(carCountField);
        doorPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Spacer
        doorPanel.add(masterInstanceLabel);
        doorPanel.add(masterInstanceField);
        doorPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Spacer
        doorPanel.add(terminalLabel);
        doorPanel.add(terminalScrollPane);

        return doorPanel;
    }
}
