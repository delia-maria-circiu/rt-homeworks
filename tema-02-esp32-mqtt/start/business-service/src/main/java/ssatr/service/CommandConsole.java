
package ssatr.service;

import java.util.Scanner;

public class CommandConsole implements Runnable {

    private final MqttConnection mqtt;
    private final Dashboard dashboard;

    public CommandConsole(MqttConnection mqtt, Dashboard dashboard) {
        this.mqtt = mqtt;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {

        Scanner scanner = new Scanner(System.in);
        printHelp();

        while (scanner.hasNextLine()) {

            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;

            } else if (line.equals("exit")) {
                break;

            } else if (line.equals("help")) {
                printHelp();

            } else if (line.equals("show")) {
                dashboard.print();

            } else {
                sendCommand(line);
            }
        }
    }

    private void sendCommand(String line) {

        String[] parts = line.split("\\s+", 2);

        if (parts.length != 2) {
            System.out.println(
                    "[ERROR] Format invalid. Exemplu: esp32-a1b2c3 STATUS"
            );
            return;
        }

        String deviceId = parts[0];
        String command = parts[1].trim();

        if (!isValidCommand(command)) {
            System.out.println("[ERROR] Comanda nu este valida: " + command);
            return;
        }

        String topic = Config.commandTopic(deviceId);

        mqtt.publish(topic, command);

        System.out.println(
                "[MQTT] Comanda trimisa catre " + deviceId + ": " + command
        );
    }

    private boolean isValidCommand(String command) {

        String[] parts = command.split("\\s+");

        try {
            switch (parts[0]) {

                case "SET_INTERVAL":
                    if (parts.length != 2) {
                        return false;
                    }

                    long interval = Long.parseLong(parts[1]);
                    return interval >= 1 && interval <= 3600000;

                case "SET_THRESHOLD":
                    if (parts.length != 2) {
                        return false;
                    }

                    double threshold = Double.parseDouble(parts[1]);
                    return Double.isFinite(threshold);

                case "RESET":
                case "STATUS":
                    return parts.length == 1;

                default:
                    return false;
            }

        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void printHelp() {

        System.out.println();
        System.out.println("========== COMENZI DISPONIBILE ==========");
        System.out.println("show");
        System.out.println("<deviceId> SET_INTERVAL <ms>");
        System.out.println("<deviceId> SET_THRESHOLD <valoare>");
        System.out.println("<deviceId> RESET");
        System.out.println("<deviceId> STATUS");
        System.out.println("help");
        System.out.println("exit");
        System.out.println("=========================================");
        System.out.println();
    }
}
