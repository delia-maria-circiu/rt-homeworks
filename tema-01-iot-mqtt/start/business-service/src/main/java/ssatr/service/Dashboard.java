package ssatr.service;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
/**
 * Ultima stare cunoscută a fiecărui device: ultima temperatură, momentul
 * ultimului mesaj, numărul de alarme etc.
 *
 * Este actualizată de thread-urile MessageProcessor și citită de CommandConsole,
 * deci accesul trebuie sincronizat.
 */
public class Dashboard {
    private final Map<String, DeviceState> devices = new LinkedHashMap<>();

    private static class DeviceState {
        double temperature;
        boolean hasTemperature;
        long lastMessageTimestamp;
        int alarmCount;
    }

    public synchronized void updateTelemetry(
            String deviceId, double temperature, long timestamp) {
        DeviceState state = devices.computeIfAbsent(
                deviceId, key -> new DeviceState());
        state.temperature = temperature;
        state.hasTemperature = true;
        state.lastMessageTimestamp = timestamp;
    }

    public synchronized void updateAlarm(
            String deviceId, double temperature, long timestamp) {
        DeviceState state = devices.computeIfAbsent(
                deviceId, key -> new DeviceState());
        state.temperature = temperature;
        state.hasTemperature = true;
        state.lastMessageTimestamp = timestamp;
        state.alarmCount++;
    }

    public synchronized void updateStatus(
            String deviceId, long timestamp) {
        DeviceState state = devices.computeIfAbsent(
                deviceId, key -> new DeviceState());
        state.lastMessageTimestamp = timestamp;
    }
    // TODO 5: alegeți structura de date pentru starea device-urilor
    //   (de exemplu un Map<String, ...> cu cheia deviceId)
    //   și adăugați metodele de actualizare apelate din MessageProcessor.

    /** Afișează un tabel cu starea tuturor device-urilor. */
    public void print() {
        // TODO 5: afișați câte un rând pentru fiecare device, de exemplu:
        //   DEVICE     TEMP    ULTIMUL MESAJ   ALARME
        //   device-1   23.41   acum 1 s        2
        System.out.printf("%-20s %-12s %-20s %s%n",
                "DEVICE", "TEMP", "ULTIMUL MESAJ", "ALARME");

        long now = System.currentTimeMillis();

        for (Map.Entry<String, DeviceState> entry : devices.entrySet()) {
            DeviceState state = entry.getValue();

            String temperature = state.hasTemperature
                    ? String.format(Locale.US, "%.2f", state.temperature)
                    : "-";

            long ageSeconds = Math.max(
                    0, (now - state.lastMessageTimestamp) / 1000);

            System.out.printf("%-20s %-12s acum %d s             %d%n",
                    entry.getKey(), temperature,
                    ageSeconds, state.alarmCount);
        }

        if (devices.isEmpty()) {
            System.out.println("Nu au fost primite mesaje de la device-uri.");
        }
    }
    }

