package org.restcomm.protocols.ss7.tools.simulator.bootstrap;

import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.Management;
import org.mobicents.protocols.sctp.netty.NettySctpManagementImpl;
import org.restcomm.protocols.ss7.m3ua.ExchangeType;
import org.restcomm.protocols.ss7.m3ua.Functionality;
import org.restcomm.protocols.ss7.m3ua.IPSPType;
import org.restcomm.protocols.ss7.m3ua.impl.parameter.ParameterFactoryImpl;
import org.restcomm.protocols.ss7.m3ua.parameter.NetworkAppearance;
import org.restcomm.protocols.ss7.m3ua.parameter.ParameterFactory;
import org.restcomm.protocols.ss7.m3ua.parameter.RoutingContext;
import org.restcomm.protocols.ss7.m3ua.parameter.TrafficModeType;
import org.restcomm.protocols.ss7.mtp.Mtp3UserPart;
import org.restcomm.protocols.ss7.mtp.RoutingLabelFormat;
import org.restcomm.protocols.ss7.tools.simulator.level1.M3UAManagementProxyImpl;


public class SctpManServer {
    private final String name;
    private int noOfWorkerThread;
    private boolean setSingleThread;
    protected String localHost = "127.0.0.1";
    protected int localPort;
    protected String remoteHost = "127.0.0.1";
    protected  int remotePorts;

    private final String persistDir = ArgsTB.get(0);//System.getProperty("user.home");
    private final ParameterFactory factory = new ParameterFactoryImpl();
    private Management sctpManagement;
    private M3UAManagementProxyImpl m3uaMgmt;

    public SctpManServer(String name, int noOfWorkerThread, boolean setSingleThread,
                         int localPort, int remotePorts) {
        this.setSingleThread = setSingleThread;
        this.noOfWorkerThread = noOfWorkerThread;
        this.name = name;
        this.localPort = 5500;
        this.remotePorts = 5000;
    }

    public void initSCTP() throws Exception {
        initSCTP(false);
    }

    public void initSCTP(boolean isSctpServer) throws Exception {
        // init SCTP stack
        this.sctpManagement = new NettySctpManagementImpl("SimSCTPServer_" + name);
        // set 8 threads for delivering messages
        this.sctpManagement.setPersistDir(persistDir);
        this.sctpManagement.setWorkerThreads(this.noOfWorkerThread);
        this.sctpManagement.setSingleThread(this.setSingleThread);

        this.sctpManagement.start();
        this.sctpManagement.setConnectDelay(10000);
        this.sctpManagement.removeAllResourses();
        Thread.sleep(500); // waiting for freeing ip ports

        String SERVER_NAME = "Server_" + name;

        // 1. Create SCTP Server
        sctpManagement.addServer(SERVER_NAME, localHost, localPort, IpChannelType.SCTP, null);

        // 2. Create SCTP Server Association

        sctpManagement.addServerAssociation(remoteHost, remotePorts, SERVER_NAME, "ServerAss_" + name, IpChannelType.SCTP);

        // 3. Start Server
        sctpManagement.startServer(SERVER_NAME);

    }

    public void initM3UA() throws Exception {
        initM3UA(false);
    }

    public void initM3UA(boolean isSctpServer) throws Exception {
        // init M3UA stack
        this.m3uaMgmt = new M3UAManagementProxyImpl("SimM3uaServer_" + name, null, null);

        this.m3uaMgmt.setPersistDir(persistDir);
        this.m3uaMgmt.setTransportManagement(this.sctpManagement);
        this.m3uaMgmt.setRoutingLabelFormat(RoutingLabelFormat.ITU);

        this.m3uaMgmt.start();
        this.m3uaMgmt.removeAllResourses();

        // configure M3UA stack
        RoutingContext rc = factory.createRoutingContext(new long[]{101/*this.testerHost.getConfigurationData().getM3uaConfigurationData().getRoutingContext()*/});
        TrafficModeType trafficModeType = factory.createTrafficModeType(TrafficModeType.Loadshare);
        NetworkAppearance na = factory.createNetworkAppearance(102L);

        // 1. Create AS
        m3uaMgmt.createAs("testas", Functionality.IPSP,//this.testerHost.getConfigurationData().getM3uaConfigurationData().getM3uaFunctionality(),
                ExchangeType.SE,//this.testerHost.getConfigurationData().getM3uaConfigurationData().getM3uaExchangeType(),
                isSctpServer ? IPSPType.SERVER : IPSPType.CLIENT,//this.testerHost.getConfigurationData().getM3uaConfigurationData().getM3uaIPSPType(),
                rc, trafficModeType, 1, na);

        // 2. Create ASP
        m3uaMgmt.createAspFactory("testasp", "ServerAss_" + name);

        // 3. Assign ASP to AS
        m3uaMgmt.assignAspToAs("testas", "testasp");

        // 4. Define Route
        m3uaMgmt.addRoute(isSctpServer ? 1 : 2,//this.testerHost.getConfigurationData().getM3uaConfigurationData().getDpc(),
                -1,//this.testerHost.getConfigurationData().getM3uaConfigurationData().getOpc(),
                -1,//this.testerHost.getConfigurationData().getM3uaConfigurationData().getSi(),
                "testas");
    }

    public void start() throws Exception {
        start(true);
    }

    public void start(boolean isClient) throws Exception {
        initSCTP(!isClient);
        initM3UA(!isClient);
        m3uaMgmt.startAsp("testasp");
    }

    public Mtp3UserPart getMtp3UserPart() {
        return this.m3uaMgmt;
    }
}
