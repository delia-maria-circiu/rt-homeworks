package ssatr.service;

import java.util.Scanner;

/**
 * Consola operatorului: citește comenzi de la tastatură.
 *
 *   show                               afișează dashboard-ul
 *   <deviceId> <COMANDA> [parametru]   trimite o comandă unui device, de exemplu
 *                                      device-1 SET_INTERVAL 1000
 *   help                               afișează comenzile
 *   exit                               oprește serviciul
 */
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
        // TODO 6: separați deviceId de restul liniei
        //   ("device-1 SET_INTERVAL 1000" -> "device-1" și "SET_INTERVAL 1000")
        //   și publicați comanda pe Config.commandTopic(deviceId).
        //   Afișați un mesaj de eroare dacă linia nu are formatul corect.
        String[] parts = line.trim().split("\\s+", 2);

        if (parts.length != 2 || parts[0].isBlank()
                || parts[1].isBlank()) {
            System.out.println(
                    "Format invalid. Exemplu: device-1 SET_INTERVAL 1000");
            return;
        }

        String deviceId = parts[0].trim();
        String command = parts[1].trim();
        String[] commandParts = command.split("\\s+");

        try {
            switch (commandParts[0].toUpperCase()) {
                case "SET_INTERVAL":
                    if (commandParts.length != 2
                            || Long.parseLong(commandParts[1]) <= 0) {
                        throw new IllegalArgumentException(
                                "SET_INTERVAL necesita un numar pozitiv de ms");
                    }
                    break;

                case "SET_THRESHOLD":
                    if (commandParts.length != 2
                            || !Double.isFinite(
                            Double.parseDouble(commandParts[1]))) {
                        throw new IllegalArgumentException(
                                "SET_THRESHOLD necesita o valoare numerica finita");
                    }
                    break;

                case "RESET":
                case "STATUS":
                    if (commandParts.length != 1) {
                        throw new IllegalArgumentException(
                                "Comanda nu accepta parametri");
                    }
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Comanda necunoscuta: " + commandParts[0]);
            }

            mqtt.publish(Config.commandTopic(deviceId), command);
            System.out.println(
                    "Comanda trimisa catre " + deviceId + ": " + command);

        } catch (IllegalArgumentException e) {
            System.out.println("Eroare: " + e.getMessage());
        }
    }

    private void printHelp() {
        System.out.println("Comenzi: show | <deviceId> SET_INTERVAL <ms> | <deviceId> SET_THRESHOLD <valoare>");
        System.out.println("         <deviceId> RESET | <deviceId> STATUS | help | exit");
    }
}
