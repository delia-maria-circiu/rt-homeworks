package ssatr.device;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;

/**
 * TASK SPORADIC: tratează alarmele de temperatură.
 *
 * Evenimentele sosesc în coada alarmEvents (puse de taskul periodic).
 * Intervalul minim între sosiri este MIN_INTERARRIVAL_MS: între două eliberări
 * ale taskului (două alarme publicate) trebuie să treacă cel puțin atât.
 * Intervalul este impus de cod: evenimentele care sosesc mai des sunt
 * respinse, ca să nu fie inundat brokerul.
 */
public class SporadicAlarmTask implements Runnable {

    static final long MIN_INTERARRIVAL_MS = 5000;

    private final String deviceId;
    private final MqttConnection mqtt;
    private final BlockingQueue<Double> alarmEvents;
    private final PeriodicTelemetryTask periodicTask;

    public SporadicAlarmTask(
            String deviceId,
            MqttConnection mqtt,
            BlockingQueue<Double> alarmEvents,
            PeriodicTelemetryTask periodicTask) {
        this.deviceId = deviceId;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
        this.periodicTask = periodicTask;
    }

    @Override
    public void run() {
        // TODO 2: implementați taskul sporadic:
        //   - așteptați un eveniment: alarmEvents.take()
        //   - dacă de la ultima eliberare au trecut mai puțin de
        //     MIN_INTERARRIVAL_MS, respingeți evenimentul (afișați un mesaj)
        //   - altfel publicați pe Config.alarmTopic(deviceId) mesajul
        //     "deviceId;timestamp;temperatura;prag"
        //     (pragul curent poate fi transmis împreună cu temperatura sau
        //      citit din PeriodicTelemetryTask; alegeți și justificați în raport)
        //
        // Vezi exemplul exemple/01-taskuri/.../SporadicTaskExample.java

        long lastRelease = 0;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Așteptăm un eveniment de alarmă.
                double temperature = alarmEvents.take();
                long now = System.nanoTime();

                // Respingem evenimentele care sosesc prea repede.
                long minInterval =
                        TimeUnit.MILLISECONDS.toNanos(MIN_INTERARRIVAL_MS);

                if (lastRelease != 0 && now - lastRelease < minInterval) {
                    System.out.println(
                            "Alarma respinsa pentru " + deviceId
                                    + ": interval minim de 5 secunde");
                    continue;
                }

                // Construim mesajul cu temperatura și pragul curent.
                long timestamp = System.currentTimeMillis();
                double threshold = periodicTask.getThreshold();

                String message = deviceId + ";"
                        + timestamp + ";"
                        + String.format(Locale.US, "%.2f", temperature) + ";"
                        + String.format(Locale.US, "%.2f", threshold);

                mqtt.publish(Config.alarmTopic(deviceId), message);

                System.out.println("Alarma publicata: " + message);

                lastRelease = System.nanoTime();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
