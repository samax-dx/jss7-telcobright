package org.restcomm.protocols.ss7.tools.simulator.bootstrap;

import java.util.Arrays;
import java.util.List;

public class ArgsTB {
    private static List<String> args;

    public static void init(String[] args) {
        ArgsTB.args = Arrays.asList(args);
    }

    public static String get(int i) {
        return args.size() > i ? args.get(i) : null;
    }
}
