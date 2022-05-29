package org.restcomm.protocols.ss7.tools.simulator.bootstrap;

import javolution.util.FastList;
import org.restcomm.protocols.ss7.indicator.RoutingIndicator;
import org.restcomm.protocols.ss7.map.MAPStackImpl;
import org.restcomm.protocols.ss7.map.api.MAPStack;
import org.restcomm.protocols.ss7.sccp.impl.parameter.ParameterFactoryImpl;
import org.restcomm.protocols.ss7.sccp.parameter.ParameterFactory;
import org.restcomm.protocols.ss7.sccp.parameter.SccpAddress;


public class TcMapManTB {
    private final String persistDir = "/home/samax";//System.getProperty("user.home");

    private final SccpManTB sccpClient;
    protected ParameterFactory parameterFactory = new ParameterFactoryImpl();
    private MAPStackImpl mapStack;


    public TcMapManTB(SccpManTB sccpClient) {
        this.sccpClient = sccpClient;
    }

    public void initMap() throws Exception {
        int extraSsn = 0;

        this.mapStack = new MAPStackImpl("Simulator", sccpClient.getSccpStack().getSccpProvider(), sccpClient.getLocalSsn());
        this.mapStack.setPersistDir(persistDir);

        if (extraSsn > 0) {
            FastList<Integer> extraSsnsNew = new FastList<>();
            extraSsnsNew.add(extraSsn);
            this.mapStack.getTCAPStack().setExtraSsns(extraSsnsNew);
        }

        this.mapStack.start();
    }

    public void start() throws Exception {
        initMap();
    }

    public MAPStack getMAPStack() {
        return this.mapStack;
    }

    public SccpAddress createOrigAddress() {
        return parameterFactory.createSccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, null, sccpClient.getLocalSpc(), sccpClient.getLocalSsn());
    }

    public SccpAddress createDestAddress() {
        return parameterFactory.createSccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, null, sccpClient.getRemoteSpc(), sccpClient.getRemoteSsn());
    }
}
