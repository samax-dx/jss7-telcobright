package org.restcomm.protocols.ss7.tools.simulator.bootstrap;
import org.restcomm.protocols.ss7.tools.simulator.bootstrap.MainTBC;
import org.restcomm.protocols.ss7.tools.simulator.bootstrap.MainTBS;
public class MyMain {
    public static void main(String[] args) throws Exception {
        MainTBS.main(new String[]{"/home/mustafa/dsi-ss7/tools/simulator/bootstrap"});
        MainTBC.main(new String[]{"/home/mustafa/dsi-ss7/tools/simulator/bootstrap"});
    }
}
