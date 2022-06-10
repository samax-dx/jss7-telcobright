package org.restcomm.protocols.ss7.tools.simulator.bootstrap;

import org.restcomm.protocols.ss7.mtp.Mtp3UserPart;
import org.restcomm.protocols.ss7.sccp.LongMessageRuleType;
import org.restcomm.protocols.ss7.sccp.SccpProtocolVersion;
import org.restcomm.protocols.ss7.sccp.SccpResource;
import org.restcomm.protocols.ss7.sccp.SccpStack;
import org.restcomm.protocols.ss7.sccp.impl.SccpStackImpl;


public class SccpManTB {
    private final String name;
    private final String persistDir = ArgsTB.get(0);//System.getProperty("user.home");
    private final Mtp3UserPart mtp3UserPart;
    private final int opc;
    private final int dpc;
    private final int ni = 2;
    private final int localSsn = 8;
    private final int remoteSsn = 8;
    private SccpStackImpl sccpStack;
    private SccpResource resource;


    public SccpManTB(Mtp3UserPart mtp3UserPart) {
        this(mtp3UserPart, true);
    }

    public SccpManTB(Mtp3UserPart mtp3UserPart, boolean isClient) {
        this.mtp3UserPart = mtp3UserPart;

        if (isClient) {
            name = "TelcoClient";
            opc = 1;
            dpc = 2;
        } else {
            name = "TelcoServer";
            opc = 2;
            dpc = 1;
        }
    }

    public void initSccp() throws Exception {
        int dpc2 = 0; // remoteSpc2

        this.sccpStack = new SccpStackImpl("TestingSccp", null);
        this.sccpStack.setPersistDir(persistDir);

        this.sccpStack.setMtp3UserPart(1, mtp3UserPart);
        this.sccpStack.start();
        this.sccpStack.removeAllResourses();

        this.sccpStack.setSccpProtocolVersion(SccpProtocolVersion.ITU);
        this.sccpStack.getRouter().addMtp3ServiceAccessPoint(1, 1, opc, ni, 0, null);
        this.sccpStack.getRouter().addMtp3Destination(1, 1, dpc, dpc, 0, 255, 255);
        this.sccpStack.getRouter().addLongMessageRule(1, 1, 16384, LongMessageRuleType.XUDT_ENABLED);
        if (dpc2 > 0) {
            this.sccpStack.getRouter().addMtp3Destination(1, 2, dpc2, dpc2, 0, 255, 255);
        }

        this.resource = this.sccpStack.getSccpResource();

        this.resource.addRemoteSpc(1, dpc, 0, 0);
        this.resource.addRemoteSsn(1, dpc, remoteSsn, 0, false);
        if (dpc2 > 0) {
            this.resource.addRemoteSpc(2, dpc2, 0, 0);
            this.resource.addRemoteSsn(2, dpc2, remoteSsn, 0, false);
        }

        sccpConfigExt(opc, dpc, dpc2, localSsn);
    }

    protected void sccpConfigExt(int opc, int dpc, int dpc2, int localSsn) throws Exception {
    }

    public void start() throws Exception {
        initSccp();
    }

    public SccpStack getSccpStack() {
        return sccpStack;
    }

    public int getLocalSpc() {
        return opc;
    }

    public int getRemoteSpc() {
        return dpc;
    }

    public int getLocalSsn() {
        return localSsn;
    }

    public int getRemoteSsn() {
        return localSsn;
    }
}
