
package ssatr.service;

import java.util.concurrent.BlockingQueue;

public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(
            String kind,
            BlockingQueue<String> inbox,
            CsvStorage storage,
            Dashboard dashboard) {

        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {

        while (!Thread.currentThread().isInterrupted()) {

            try {
                String message = inbox.take();

                String[] fields = message.split(";", -1);

                if (!isValid(fields)) {
                    System.out.println(
                            "[ERROR] Mesaj invalid (" + kind + "): " + message
                    );
                    continue;
                }

                String deviceId = fields[0];
                long timestamp = Long.parseLong(fields[1]);

                // Salvăm mesajul original în CSV.
                storage.append(kind, message);

                switch (kind) {

                    case "telemetry": {
                        double temperature = Double.parseDouble(fields[2]);

                        dashboard.updateTelemetry(
                                deviceId,
                                temperature,
                                timestamp
                        );
                        break;
                    }

                    case "alarm": {
                        double temperature = Double.parseDouble(fields[2]);

                        dashboard.updateAlarm(
                                deviceId,
                                temperature,
                                timestamp
                        );

                        System.out.printf(
                                "[ALARMA] Device: %s | Temperatura: %.2f C | Prag: %s C%n",
                                deviceId,
                                temperature,
                                fields[3]
                        );
                        break;
                    }

                    case "status": {
                        String status = fields[2];

                        dashboard.updateStatus(
                                deviceId,
                                status,
                                timestamp
                        );

                        System.out.println(
                                "[STATUS] Device: " + deviceId +
                                        " | Raspuns: " + status
                        );
                        break;
                    }

                    default:
                        System.out.println("[ERROR] Tip de mesaj necunoscut: " + kind);
                        break;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;

            } catch (NumberFormatException e) {
                System.out.println(
                        "[ERROR] Valoare numerica invalida pentru mesajul " + kind
                );

            } catch (Exception e) {
                System.out.println(
                        "[ERROR] Eroare la procesarea mesajului: " + e.getMessage()
                );
            }
        }
    }

    private boolean isValid(String[] fields) {

        try {
            if (fields.length == 0 || fields[0].isBlank()) {
                return false;
            }

            if (kind.equals("telemetry") && fields.length == 3) {
                Long.parseLong(fields[1]);
                double temperature = Double.parseDouble(fields[2]);
                return Double.isFinite(temperature);
            }

            if (kind.equals("alarm") && fields.length == 4) {
                Long.parseLong(fields[1]);

                double temperature = Double.parseDouble(fields[2]);
                double threshold = Double.parseDouble(fields[3]);

                return Double.isFinite(temperature)
                        && Double.isFinite(threshold);
            }

            if (kind.equals("status") && fields.length == 3) {
                Long.parseLong(fields[1]);
                return !fields[2].isBlank();
            }

        } catch (NumberFormatException e) {
            return false;
        }

        return false;
    }
}
