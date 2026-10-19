package ssatr.service;

import java.util.concurrent.BlockingQueue;

/**
 * Preia mesajele de un anumit tip ("telemetry", "alarm" sau "status") din coada
 * inbox, le salvează și actualizează dashboard-ul.
 *
 * Formatul mesajelor (câmpuri separate prin ";"):
 *   telemetry:  deviceId;timestamp;temperatura
 *   alarm:      deviceId;timestamp;temperatura;prag
 *   status:     deviceId;timestamp;text
 */
public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(String kind, BlockingQueue<String> inbox, CsvStorage storage, Dashboard dashboard) {
        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        // TODO 4: într-o buclă:
        //   - preluați un mesaj: inbox.take()
        //   - salvați mesajul în fișierul CSV: storage.append(kind, mesaj)
        //     (CsvStorage este gata implementată)
        //   - actualizați dashboard-ul, în funcție de kind
        //   - alarmele și confirmările se afișează imediat în consolă
        //   - un mesaj greșit (format invalid) nu trebuie să oprească thread-ul

        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = inbox.take();
                String[] fields = message.split(";", -1);

                try {
                    String deviceId;
                    long timestamp;

                    // Verificăm formatul și câmpurile fiecărui tip de mesaj.
                    switch (kind) {
                        case "telemetry": {
                            if (fields.length != 3) {
                                throw new IllegalArgumentException(
                                        "Telemetria trebuie sa aiba 3 campuri");
                            }

                            deviceId = fields[0].trim();
                            timestamp = Long.parseLong(fields[1].trim());
                            double temperature =
                                    Double.parseDouble(fields[2].trim());

                            if (deviceId.isEmpty()
                                    || timestamp <= 0
                                    || !Double.isFinite(temperature)) {
                                throw new IllegalArgumentException(
                                        "Valori invalide in telemetrie");
                            }

                            // Salvăm doar mesajele valide.
                            storage.append(kind, message);
                            dashboard.updateTelemetry(
                                    deviceId, temperature, timestamp);
                            break;
                        }

                        case "alarm": {
                            if (fields.length != 4) {
                                throw new IllegalArgumentException(
                                        "Alarma trebuie sa aiba 4 campuri");
                            }

                            deviceId = fields[0].trim();
                            timestamp = Long.parseLong(fields[1].trim());
                            double temperature =
                                    Double.parseDouble(fields[2].trim());
                            double threshold =
                                    Double.parseDouble(fields[3].trim());

                            if (deviceId.isEmpty()
                                    || timestamp <= 0
                                    || !Double.isFinite(temperature)
                                    || !Double.isFinite(threshold)) {
                                throw new IllegalArgumentException(
                                        "Valori invalide in alarma");
                            }

                            storage.append(kind, message);
                            dashboard.updateAlarm(
                                    deviceId, temperature, timestamp);

                            System.out.println("[ALARMA] " + message);
                            break;
                        }

                        case "status": {
                            if (fields.length != 3) {
                                throw new IllegalArgumentException(
                                        "Statusul trebuie sa aiba 3 campuri");
                            }

                            deviceId = fields[0].trim();
                            timestamp = Long.parseLong(fields[1].trim());
                            String statusText = fields[2].trim();

                            if (deviceId.isEmpty()
                                    || timestamp <= 0
                                    || statusText.isEmpty()) {
                                throw new IllegalArgumentException(
                                        "Valori invalide in status");
                            }

                            storage.append(kind, message);
                            dashboard.updateStatus(deviceId, timestamp);

                            System.out.println("[STATUS] " + message);
                            break;
                        }

                        default:
                            throw new IllegalArgumentException(
                                    "Tip de mesaj necunoscut: " + kind);
                    }

                } catch (IllegalArgumentException e) {
                    System.out.println("[MESAJ INVALID - " + kind + "] "
                            + message + " (" + e.getMessage() + ")");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
