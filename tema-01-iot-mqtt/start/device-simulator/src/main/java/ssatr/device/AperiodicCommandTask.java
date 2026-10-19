package ssatr.device;

import java.util.concurrent.BlockingQueue;

/**
 * TASK APERIODIC: execută comenzile primite de la business service.
 *
 * Comenzile sosesc oricând, pe MQTT, fără un interval minim între ele, și sunt
 * puse în coada commands. Fiecare comandă eliberează un job al taskului.
 * Comenzi care trebuie suportate:
 *
 *   SET_INTERVAL <ms>       schimbă perioada T a taskului periodic
 *   SET_THRESHOLD <valoare> schimbă pragul de alarmă
 *   RESET                   readuce senzorul la valoarea inițială
 *   STATUS                  raportează perioada și pragul curente
 *
 * După executarea fiecărei comenzi, device-ul publică pe Config.statusTopic(deviceId)
 * o confirmare: "deviceId;timestamp;text", de exemplu
 * "device-1;1727600000000;OK SET_INTERVAL 1000".
 */
public class AperiodicCommandTask implements Runnable {

    private final String deviceId;
    private final TemperatureSensor sensor;
    private final MqttConnection mqtt;
    private final BlockingQueue<String> commands;
    private final PeriodicTelemetryTask periodic;

    public AperiodicCommandTask(String deviceId, TemperatureSensor sensor, MqttConnection mqtt,
                                BlockingQueue<String> commands, PeriodicTelemetryTask periodic) {
        this.deviceId = deviceId;
        this.sensor = sensor;
        this.mqtt = mqtt;
        this.commands = commands;
        this.periodic = periodic;
    }

    @Override
    public void run() {
        // TODO 3: implementați taskul aperiodic:
        //   - preluați comenzile din coadă: commands.take()
        //   - separați numele comenzii de parametru: command.split(" ")
        //   - executați comanda (periodic.setPeriodMs(...), sensor.reset() etc.)
        //   - publicați confirmarea pe Config.statusTopic(deviceId)
        //   - o comandă necunoscută sau un parametru invalid nu trebuie să
        //     oprească taskul: publicați "ERROR <motiv>"
        //
        // Vezi exemplul exemple/01-taskuri/.../AperiodicTaskExample.java
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Preluăm următoarea comandă din coadă.
                String command = commands.take().trim();
                String[] parts = command.split("\\s+", 2);
                String action = parts[0].toUpperCase();
                String response;

                try {
                    switch (action) {
                        case "SET_INTERVAL":
                            if (parts.length != 2) {
                                throw new IllegalArgumentException(
                                        "Lipseste intervalul");
                            }

                            long interval = Long.parseLong(parts[1].trim());

                            if (interval <= 0) {
                                throw new IllegalArgumentException(
                                        "Intervalul trebuie sa fie pozitiv");
                            }

                            periodic.setPeriodMs(interval);
                            response = "OK SET_INTERVAL " + interval;
                            break;

                        case "SET_THRESHOLD":
                            if (parts.length != 2) {
                                throw new IllegalArgumentException(
                                        "Lipseste pragul");
                            }

                            double threshold =
                                    Double.parseDouble(parts[1].trim());

                            if (!Double.isFinite(threshold)) {
                                throw new IllegalArgumentException(
                                        "Pragul trebuie sa fie un numar finit");
                            }

                            periodic.setThreshold(threshold);
                            response = "OK SET_THRESHOLD " + threshold;
                            break;

                        case "RESET":
                            if (parts.length != 1) {
                                throw new IllegalArgumentException(
                                        "RESET nu accepta parametri");
                            }

                            sensor.reset();
                            response = "OK RESET";
                            break;

                        case "STATUS":
                            if (parts.length != 1) {
                                throw new IllegalArgumentException(
                                        "STATUS nu accepta parametri");
                            }

                            response = "OK STATUS interval="
                                    + periodic.getPeriodMs()
                                    + " threshold="
                                    + periodic.getThreshold();
                            break;

                        default:
                            throw new IllegalArgumentException(
                                    "Comanda necunoscuta: " + action);
                    }
                } catch (IllegalArgumentException e) {
                    response = "ERROR " + e.getMessage();
                }

                // Publicăm confirmarea sau eroarea pe topicul de status.
                String statusMessage = deviceId + ";"
                        + System.currentTimeMillis() + ";"
                        + response;

                mqtt.publish(
                        Config.statusTopic(deviceId),
                        statusMessage);

                System.out.println(statusMessage);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
