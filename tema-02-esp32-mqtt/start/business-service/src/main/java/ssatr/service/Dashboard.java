
package ssatr.service;

import java.util.LinkedHashMap;
import java.util.Map;

public class Dashboard {

    private static class DeviceState {
        double temperature;
        long lastTimestamp;
        int alarms;
        String lastStatus = "-";
    }

    private final Map<String, DeviceState> devices = new LinkedHashMap<>();

    private DeviceState getDevice(String deviceId) {
        return devices.computeIfAbsent(deviceId, id -> new DeviceState());
    }

    public synchronized void updateTelemetry(
            String deviceId, double temperature, long timestamp) {

        DeviceState state = getDevice(deviceId);
        state.temperature = temperature;
        state.lastTimestamp = timestamp;
    }

    public synchronized void updateAlarm(
            String deviceId, double temperature, long timestamp) {

        DeviceState state = getDevice(deviceId);
        state.temperature = temperature;
        state.lastTimestamp = timestamp;
        state.alarms++;
    }

    public synchronized void updateStatus(
            String deviceId, String status, long timestamp) {

        DeviceState state = getDevice(deviceId);
        state.lastTimestamp = timestamp;
        state.lastStatus = status;
    }

    public synchronized void print() {

        System.out.println();
        System.out.println("============== DASHBOARD ==============");
        System.out.printf("%-18s %-10s %-18s %-10s%n",
                "DEVICE", "TEMP (C)", "ULTIMUL MESAJ", "ALARME");

        long now = System.currentTimeMillis();

        if (devices.isEmpty()) {
            System.out.println("Nu exista inca dispozitive conectate.");
        }

        for (Map.Entry<String, DeviceState> entry : devices.entrySet()) {

            String deviceId = entry.getKey();
            DeviceState state = entry.getValue();

            long secondsAgo = Math.max(0, (now - state.lastTimestamp) / 1000);

            System.out.printf(
                    "%-18s %-10.2f acum %-13s %-10d%n",
                    deviceId,
                    state.temperature,
                    secondsAgo + " s",
                    state.alarms
            );
        }

        System.out.println("=======================================");
        System.out.println();
    }
}
