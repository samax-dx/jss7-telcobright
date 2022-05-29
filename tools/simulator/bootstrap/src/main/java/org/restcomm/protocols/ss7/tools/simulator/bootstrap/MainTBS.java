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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


public class MainTBS {
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
                        runApp(finalTaskIndex);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                t.start();
                Thread.sleep(1000);
            }
        }
    }

    private static void runApp(int instanceNumber) throws Exception {
        SctpManTB sctpServer = new SctpManTB(false, instanceNumber);
        sctpServer.start(false);

        SccpManTB sccpServer = new SccpManTB(sctpServer.getMtp3UserPart(), false);
        sccpServer.start();

        TcMapManTB mapServer = new TcMapManTB(sccpServer);
        mapServer.start();

        SmsServerTB smsServer = new SmsServerTB(mapServer);
        smsServer.start();
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
