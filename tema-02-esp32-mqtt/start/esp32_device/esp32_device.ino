
#include "config.h"
#include "connection.h"

#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/queue.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

// Variabile globale
volatile uint32_t currentPeriod = PERIOD_MS_INITIAL;
volatile float currentThreshold = THRESHOLD_INITIAL;

// Structura unui eveniment de alarmă
struct AlarmEvent {
    float temperature;
    float threshold;
};

// Structura unei comenzi
struct CommandMessage {
    char text[COMMAND_MAX_LEN];
};

// Cozile FreeRTOS
QueueHandle_t alarmQueue = NULL;
QueueHandle_t commandQueue = NULL;

// Citirea temperaturii de la senzorul intern
float read_temperature(void) {
    return temperatureRead();
}

// Verifică dacă după un număr există doar spații
bool validEnd(char* end) {
    while (*end == ' ' || *end == '\t') {
        end++;
    }
    return *end == '\0';
}

// Publică un mesaj pe topic-ul status
void publish_status(const char* message) {
    char payload[160];

    snprintf(
        payload,
        sizeof(payload),
        "%s;%lld;%s",
        DEVICE_ID,
        (long long)epoch_ms(),
        message
    );

    mqtt_publish("status", payload);
}

// TODO 1: TASK PERIODIC
void periodic_task(void* parameter) {
    TickType_t lastWakeTime = xTaskGetTickCount();

    for (;;) {
        uint32_t period = currentPeriod;
        float threshold = currentThreshold;

        float temperature = read_temperature();

        char payload[128];

        snprintf(
            payload,
            sizeof(payload),
            "%s;%lld;%.2f",
            DEVICE_ID,
            (long long)epoch_ms(),
            temperature
        );

        mqtt_publish("telemetry", payload);

        Serial.printf(
            "[periodic] temperatura: %.2f C, prag: %.2f C\n",
            temperature,
            threshold
        );

        // Dacă temperatura depășește pragul, trimitem
        // evenimentul în coada de alarme fără blocare.
        if (temperature > threshold) {
            AlarmEvent event;

            event.temperature = temperature;
            event.threshold = threshold;

            if (xQueueSend(alarmQueue, &event, 0) != pdPASS) {
                Serial.println("[periodic] coada de alarme este plina");
            }
        }

        // Așteptare absolută, pentru evitarea derivei temporale.
        xTaskDelayUntil(
            &lastWakeTime,
            pdMS_TO_TICKS(period)
        );
    }
}

// TODO 2: TASK SPORADIC
void sporadic_task(void* parameter) {
    AlarmEvent event;

    uint32_t lastAlarmTime = 0;
    bool alarmPublished = false;

    for (;;) {
        // Așteaptă un eveniment în coadă.
        if (xQueueReceive(alarmQueue, &event, portMAX_DELAY) == pdPASS) {

            uint32_t currentTime = millis();

            // Verifică intervalul minim dintre două alarme.
            if (alarmPublished &&
                currentTime - lastAlarmTime < MIN_INTERARRIVAL_MS) {

                Serial.println("[sporadic] alarma respinsa: interval prea mic");
                continue;
            }

            char payload[160];

            snprintf(
                payload,
                sizeof(payload),
                "%s;%lld;%.2f;%.2f",
                DEVICE_ID,
                (long long)epoch_ms(),
                event.temperature,
                event.threshold
            );

            mqtt_publish("alarm", payload);

            lastAlarmTime = currentTime;
            alarmPublished = true;

            Serial.printf(
                "[sporadic] alarma publicata: %.2f C\n",
                event.temperature
            );
        }
    }
}

// TODO 3: TASK APERIODIC
void aperiodic_task(void* parameter) {
    CommandMessage command;

    for (;;) {
        if (xQueueReceive(commandQueue, &command, portMAX_DELAY) == pdPASS) {

            char response[COMMAND_MAX_LEN + 80];

            Serial.printf("[aperiodic] execut comanda: %s\n", command.text);

            // SET_INTERVAL
            if (strncmp(command.text, "SET_INTERVAL ", 13) == 0) {

                char* end;
                long value = strtol(command.text + 13, &end, 10);

                if (end != command.text + 13 &&
                    validEnd(end) &&
                    value >= 1 &&
                    value <= 3600000) {

                    currentPeriod = (uint32_t)value;

                    snprintf(
                        response,
                        sizeof(response),
                        "OK SET_INTERVAL %ld",
                        value
                    );

                } else {
                    snprintf(response, sizeof(response),
                             "ERROR parametru invalid pentru SET_INTERVAL");
                }
            }

            // SET_THRESHOLD
            else if (strncmp(command.text, "SET_THRESHOLD ", 14) == 0) {

                char* end;
                float value = strtof(command.text + 14, &end);

                if (end != command.text + 14 &&
                    validEnd(end) &&
                    isfinite(value)) {

                    currentThreshold = value;

                    snprintf(
                        response,
                        sizeof(response),
                        "OK SET_THRESHOLD %.2f",
                        value
                    );

                } else {
                    snprintf(response, sizeof(response),
                             "ERROR parametru invalid pentru SET_THRESHOLD");
                }
            }

            // RESET
            else if (strcmp(command.text, "RESET") == 0) {

                currentPeriod = PERIOD_MS_INITIAL;
                currentThreshold = THRESHOLD_INITIAL;

                snprintf(
                    response,
                    sizeof(response),
                    "OK RESET"
                );
            }

            // STATUS
            else if (strcmp(command.text, "STATUS") == 0) {

                snprintf(
                    response,
                    sizeof(response),
                    "OK STATUS interval=%lu threshold=%.2f",
                    (unsigned long)currentPeriod,
                    currentThreshold
                );
            }

            // Comandă necunoscută
            else {
                snprintf(
                    response,
                    sizeof(response),
                    "ERROR comanda necunoscuta"
                );
            }

            Serial.printf("[aperiodic] raspuns: %s\n", response);

            publish_status(response);
        }
    }
}

// Callback apelat la primirea unei comenzi MQTT
void command_received(const char* text) {

    Serial.printf("[mqtt] comanda primita: %s\n", text);

    CommandMessage command;

    strncpy(command.text, text, COMMAND_MAX_LEN - 1);
    command.text[COMMAND_MAX_LEN - 1] = '\0';

    // Adaugă comanda în coadă fără blocare.
    if (xQueueSend(commandQueue, &command, 0) != pdPASS) {
        Serial.println("[mqtt] coada de comenzi este plina");
    }
}

// TODO 0: INITIALIZARE
void setup() {

    Serial.begin(115200);
    Serial.setTxTimeoutMs(0);

    uint32_t t = millis();

    while (!Serial && millis() - t < 3000) {
        delay(10);
    }

    delay(200);

    Serial.println();

    Serial.printf(
        "=== tema 02: STUDENT %s, DEVICE_ID %s ===\n",
        STUDENT,
        DEVICE_ID
    );

    wifi_connect();
    time_sync();

    // Crearea cozilor înainte de pornirea MQTT.
    alarmQueue = xQueueCreate(10, sizeof(AlarmEvent));
    commandQueue = xQueueCreate(10, sizeof(CommandMessage));

    if (alarmQueue == NULL || commandQueue == NULL) {
        Serial.println("EROARE: cozile nu au putut fi create!");
        return;
    }

    // Pornirea conexiunii MQTT.
    mqtt_start();

    // Pornirea celor trei taskuri FreeRTOS.
    xTaskCreate(
        periodic_task,
        "PeriodicTask",
        4096,
        NULL,
        3,
        NULL
    );

    xTaskCreate(
        sporadic_task,
        "SporadicTask",
        4096,
        NULL,
        4,
        NULL
    );

    xTaskCreate(
        aperiodic_task,
        "AperiodicTask",
        4096,
        NULL,
        5,
        NULL
    );

    Serial.println("[setup] cele trei taskuri au fost pornite");
}

void loop() {
    delay(1000);
}
