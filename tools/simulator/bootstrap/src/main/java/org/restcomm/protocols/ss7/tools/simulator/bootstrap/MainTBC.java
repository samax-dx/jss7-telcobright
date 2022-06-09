package org.restcomm.protocols.ss7.tools.simulator.bootstrap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.BasicConfigurator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;


public class MainTBC {
    public static void writeSs7TaskReport(SS7Task task, long startTime, long endTime) {
        String startedOn = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(startTime);
        String endedOn = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(endTime);

        String report = String.join("\n", new String[]{
                "### " + task.operatorName,
                "Start: " + startedOn,
                "End: " + endedOn,
                "Number of SMS: " + task.repeatCount//,
//                "Elapsed Time: " + ((endTime - startTime) / 100),
//                "TPS: " + (((double) task.repeatCount) / (endTime - startTime) * 100)
        });

        Path reportPath = Paths.get("/home/samax/ss7cnf/" + task.operatorName + ".report.json");
        try {
            Files.write(reportPath, report.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<SS7Task> getConfigs(String dir) {
        return Arrays.stream(Objects.requireNonNull(new File(dir).listFiles())).map(file -> {
            try {
                Path configPath = Paths.get(file.getAbsolutePath());
                String config = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
                return new ObjectMapper().readValue(config, SS7Task.class);
            } catch (IOException ignore) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();

        int taskIndex = 0;
        for (SS7Task task : getConfigs("/home/samax/ss7cnf/")) {
            for (int i = 0; i < task.threadCount; ++i) {
                int finalTaskIndex = taskIndex++;
                Thread t = new Thread(() -> {
                    try {
                        runApp(finalTaskIndex, task.message, task.repeatCount / task.threadCount, (startTime, endTime) -> writeSs7TaskReport(task, startTime, endTime));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                t.start();
                Thread.sleep(1000);
            }
        }
    }

    private static void runApp(int instanceNumber, String message, int repeatCount, BiConsumer<Long, Long> onDone) throws Exception {
        SctpManTB sctpClient = new SctpManTB(instanceNumber);
        sctpClient.start();

        SccpManTB sccpClient = new SccpManTB(sctpClient.getMtp3UserPart());
        sccpClient.start();

        TcMapManTB mapClient = new TcMapManTB(sccpClient);
        mapClient.start();

        SmsClientTB smsClient = new SmsClientTB(mapClient);
        smsClient.start();

        Thread.sleep(15000); // waiting for freeing ip ports

        long startTime = System.currentTimeMillis();
        smsClient.sendSms(message, "8888", "1111", repeatCount);
        long endTime = System.currentTimeMillis();

        onDone.accept(startTime, endTime);
    }

    public static class SS7Task {
        @JsonProperty("operatorName")
        private String operatorName;
        @JsonProperty("message")
        private String message;
        @JsonProperty("repeatCount")
        private int repeatCount;
        @JsonProperty("threadCount")
        private int threadCount;
    }
}
