package org.restcomm.protocols.ss7.tools.simulator.bootstrap;


import org.restcomm.protocols.ss7.map.api.MAPApplicationContext;
import org.restcomm.protocols.ss7.map.api.MAPApplicationContextName;
import org.restcomm.protocols.ss7.map.api.MAPApplicationContextVersion;
import org.restcomm.protocols.ss7.map.api.MAPDialog;
import org.restcomm.protocols.ss7.map.api.MAPDialogListener;
import org.restcomm.protocols.ss7.map.api.MAPException;
import org.restcomm.protocols.ss7.map.api.MAPMessage;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.map.api.dialog.MAPAbortProviderReason;
import org.restcomm.protocols.ss7.map.api.dialog.MAPAbortSource;
import org.restcomm.protocols.ss7.map.api.dialog.MAPNoticeProblemDiagnostic;
import org.restcomm.protocols.ss7.map.api.dialog.MAPRefuseReason;
import org.restcomm.protocols.ss7.map.api.dialog.MAPUserAbortChoice;
import org.restcomm.protocols.ss7.map.api.errors.MAPErrorMessage;
import org.restcomm.protocols.ss7.map.api.primitives.AddressNature;
import org.restcomm.protocols.ss7.map.api.primitives.AddressString;
import org.restcomm.protocols.ss7.map.api.primitives.ISDNAddressString;
import org.restcomm.protocols.ss7.map.api.primitives.MAPExtensionContainer;
import org.restcomm.protocols.ss7.map.api.primitives.NumberingPlan;
import org.restcomm.protocols.ss7.map.api.service.sms.AlertServiceCentreRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.AlertServiceCentreResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.ForwardShortMessageRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.ForwardShortMessageResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.InformServiceCentreRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPDialogSms;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPServiceSmsListener;
import org.restcomm.protocols.ss7.map.api.service.sms.MoForwardShortMessageRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.MoForwardShortMessageResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.MtForwardShortMessageRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.MtForwardShortMessageResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.NoteSubscriberPresentRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.ReadyForSMRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.ReadyForSMResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.ReportSMDeliveryStatusRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.ReportSMDeliveryStatusResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.SM_RP_DA;
import org.restcomm.protocols.ss7.map.api.service.sms.SM_RP_OA;
import org.restcomm.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.SmsSignalInfo;
import org.restcomm.protocols.ss7.map.api.smstpdu.AddressField;
import org.restcomm.protocols.ss7.map.api.smstpdu.DataCodingScheme;
import org.restcomm.protocols.ss7.map.api.smstpdu.NumberingPlanIdentification;
import org.restcomm.protocols.ss7.map.api.smstpdu.ProtocolIdentifier;
import org.restcomm.protocols.ss7.map.api.smstpdu.SmsSubmitTpdu;
import org.restcomm.protocols.ss7.map.api.smstpdu.SmsTpdu;
import org.restcomm.protocols.ss7.map.api.smstpdu.TypeOfNumber;
import org.restcomm.protocols.ss7.map.api.smstpdu.UserData;
import org.restcomm.protocols.ss7.map.api.smstpdu.UserDataHeader;
import org.restcomm.protocols.ss7.map.api.smstpdu.ValidityPeriod;
import org.restcomm.protocols.ss7.map.smstpdu.AddressFieldImpl;
import org.restcomm.protocols.ss7.map.smstpdu.DataCodingSchemeImpl;
import org.restcomm.protocols.ss7.map.smstpdu.ProtocolIdentifierImpl;
import org.restcomm.protocols.ss7.map.smstpdu.SmsSubmitTpduImpl;
import org.restcomm.protocols.ss7.map.smstpdu.UserDataImpl;
import org.restcomm.protocols.ss7.map.smstpdu.ValidityPeriodImpl;
import org.restcomm.protocols.ss7.tcap.asn.ApplicationContextName;
import org.restcomm.protocols.ss7.tcap.asn.comp.Problem;
import org.restcomm.protocols.ss7.tools.simulator.tests.sms.SmsCodingType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


public class SmsServerTB {
    private final TcMapManTB mapMan;
    private int mesRef;

    private boolean needSendClose = false;

    public SmsServerTB(TcMapManTB mapMan) {
        this.mapMan = mapMan;
    }

    private class HostMessageData {
        public MtMessageData mtMessageData;
        public ResendMessageData resendMessageData;
    }

    private class MtMessageData {
        public String msg;
        public String origIsdnNumber;
        public String vlrNum;
        public String destImsi;
    }

    private class ResendMessageData {
        public SM_RP_DA da;
        public SM_RP_OA oa;
        public SmsSignalInfo si;
        public String msg;
        public String destImsi;
        public String vlrNumber;
        public String origIsdnNumber;
        public String serviceCentreAddr;
    }

    private static int smsCodingToDcs(SmsCodingType smsCodingType) {
        switch (smsCodingType.intValue()) {
            case SmsCodingType.VAL_GSM7:
                return 0;
            case SmsCodingType.VAL_GSM8:
                return 4;
            case SmsCodingType.VAL_UCS2:
                return 8;
            default:
                return -1;
        }
    }

    public void start() {
        MAPProvider mapProvider = this.mapMan.getMAPStack().getMAPProvider();
        mapProvider.getMAPServiceSms().acivate();
        mapProvider.getMAPServiceSms().addMAPServiceListener(new MAPServiceSmsListener() {

            @Override
            public void onForwardShortMessageRequest(ForwardShortMessageRequest forwSmInd) {
//                System.out.println("\n* onForwardShortMessageRequest *\n");
            }

            @Override
            public void onForwardShortMessageResponse(ForwardShortMessageResponse forwSmRespInd) {
//                System.out.println("\n* onForwardShortMessageResponse *\n");
            }

            @Override
            public void onMoForwardShortMessageRequest(MoForwardShortMessageRequest moForwSmInd) {
                MAPDialogSms curDialog = moForwSmInd.getMAPDialog();
                long invokeId = moForwSmInd.getInvokeId();
                SM_RP_DA da = moForwSmInd.getSM_RP_DA();
                SM_RP_OA oa = moForwSmInd.getSM_RP_OA();
                SmsSignalInfo si = moForwSmInd.getSM_RP_UI();

                onMoRequest(da, oa, si, curDialog);

                try {
                    curDialog.addMoForwardShortMessageResponse(invokeId, null, null);
                    needSendClose = true;
                } catch (MAPException e) {
                    System.out.println("Exception when invoking addMoForwardShortMessageResponse : " + e.getMessage());
                }
            }

            @Override
            public void onMoForwardShortMessageResponse(MoForwardShortMessageResponse moForwSmRespInd) {
//                System.out.println("\n* onMoForwardShortMessageResponse *\n");
            }

            @Override
            public void onMtForwardShortMessageRequest(MtForwardShortMessageRequest mtForwSmInd) {
//                System.out.println("\n* onMtForwardShortMessageRequest *\n");
            }

            @Override
            public void onMtForwardShortMessageResponse(MtForwardShortMessageResponse mtForwSmRespInd) {
//                System.out.println("\n* onMtForwardShortMessageResponse *\n");
            }

            @Override
            public void onSendRoutingInfoForSMRequest(SendRoutingInfoForSMRequest sendRoutingInfoForSMInd) {
//                System.out.println("\n* onSendRoutingInfoForSMRequest *\n");
            }

            @Override
            public void onSendRoutingInfoForSMResponse(SendRoutingInfoForSMResponse sendRoutingInfoForSMRespInd) {
//                System.out.println("\n* onSendRoutingInfoForSMResponse *\n");
            }

            @Override
            public void onReportSMDeliveryStatusRequest(ReportSMDeliveryStatusRequest reportSMDeliveryStatusInd) {
//                System.out.println("\n* onReportSMDeliveryStatusRequest *\n");
            }

            @Override
            public void onReportSMDeliveryStatusResponse(ReportSMDeliveryStatusResponse reportSMDeliveryStatusRespInd) {
//                System.out.println("\n* onReportSMDeliveryStatusResponse *\n");
            }

            @Override
            public void onInformServiceCentreRequest(InformServiceCentreRequest informServiceCentreInd) {
//                System.out.println("\n* onInformServiceCentreRequest *\n");
            }

            @Override
            public void onAlertServiceCentreRequest(AlertServiceCentreRequest alertServiceCentreInd) {
//                System.out.println("\n* onAlertServiceCentreRequest *\n");
            }

            @Override
            public void onAlertServiceCentreResponse(AlertServiceCentreResponse alertServiceCentreInd) {
//                System.out.println("\n* onAlertServiceCentreResponse *\n");
            }

            @Override
            public void onReadyForSMRequest(ReadyForSMRequest request) {
//                System.out.println("\n* onReadyForSMRequest *\n");
            }

            @Override
            public void onReadyForSMResponse(ReadyForSMResponse response) {
//                System.out.println("\n* onReadyForSMResponse *\n");
            }

            @Override
            public void onNoteSubscriberPresentRequest(NoteSubscriberPresentRequest request) {
//                System.out.println("\n* onNoteSubscriberPresentRequest *\n");
            }

            @Override
            public void onErrorComponent(MAPDialog mapDialog, Long invokeId, MAPErrorMessage mapErrorMessage) {
//                System.out.println("\n* onErrorComponent *\n");
            }

            @Override
            public void onRejectComponent(MAPDialog mapDialog, Long invokeId, Problem problem, boolean isLocalOriginated) {
//                System.out.println("\n* onRejectComponent *\n");
            }

            @Override
            public void onInvokeTimeout(MAPDialog mapDialog, Long invokeId) {
//                System.out.println("\n* onInvokeTimeout *\n");
            }

            @Override
            public void onMAPMessage(MAPMessage mapMessage) {
                // handling optional
            }
        });
        mapProvider.addMAPDialogListener(new MAPDialogListener() {
            @Override
            public void onDialogDelimiter(MAPDialog mapDialog) {
                boolean needSend = mapDialog.getApplicationContext().getApplicationContextName() == MAPApplicationContextName.shortMsgMTRelayContext
                        || mapDialog.getApplicationContext().getApplicationContextName() == MAPApplicationContextName.shortMsgMORelayContext;

                if (needSend) {
                    if (mapDialog.getUserObject() != null) {
                        HostMessageData hmd = (HostMessageData) mapDialog.getUserObject();
                        ResendMessageData md = hmd.resendMessageData;
                        if (md != null) {
                            try {
                                MAPDialogSms dlg = (MAPDialogSms) mapDialog;

                                if (dlg.getApplicationContext().getApplicationContextVersion().getVersion() <= 2) {
                                    dlg.addForwardShortMessageRequest(md.da, md.oa, md.si, false);
                                } else {
                                    dlg.addMoForwardShortMessageRequest(md.da, md.oa, md.si, null, null);
                                }

                                mapDialog.send();
                            } catch (Exception e) {
                                System.out.println("Exception when invoking close() : " + e.getMessage());
                                return;
                            }
                            hmd.resendMessageData = null;
                            return;
                        }
                    }
                    return;
                }

                if (needSendClose) {
                    try {
                        needSendClose = false;
                        mapDialog.close(false);
                    } catch (Exception e) {
                        System.out.println("Exception when invoking close() : " + e.getMessage());
                    }
                    return;
                }

                if (needSend) {
                    try {
                        mapDialog.send();
                    } catch (Exception e) {
                        System.out.println("Exception when invoking send() : " + e.getMessage());
                    }
                    return;
                }
            }

            @Override
            public void onDialogRequest(MAPDialog mapDialog, AddressString destReference, AddressString origReference, MAPExtensionContainer extensionContainer) {
                // handling optional
            }

            @Override
            public void onDialogRequestEricsson(MAPDialog mapDialog, AddressString destReference, AddressString origReference, AddressString eriMsisdn, AddressString eriVlrNo) {
//                System.out.println("\n* onDialogRequestEricsson *\n");
            }

            @Override
            public void onDialogAccept(MAPDialog mapDialog, MAPExtensionContainer extensionContainer) {
//                System.out.println("\n* onDialogAccept *\n");
            }

            @Override
            public void onDialogReject(MAPDialog mapDialog, MAPRefuseReason refuseReason, ApplicationContextName alternativeApplicationContext, MAPExtensionContainer extensionContainer) {
//                System.out.println("\n* onDialogReject *\n");
            }

            @Override
            public void onDialogUserAbort(MAPDialog mapDialog, MAPUserAbortChoice userReason, MAPExtensionContainer extensionContainer) {
//                System.out.println("\n* onDialogUserAbort *\n");
            }

            @Override
            public void onDialogProviderAbort(MAPDialog mapDialog, MAPAbortProviderReason abortProviderReason, MAPAbortSource abortSource, MAPExtensionContainer extensionContainer) {
//                System.out.println("\n* onDialogProviderAbort *\n");
            }

            @Override
            public void onDialogClose(MAPDialog mapDialog) {
//                System.out.println("\n* onDialogClose *\n");
            }

            @Override
            public void onDialogNotice(MAPDialog mapDialog, MAPNoticeProblemDiagnostic noticeProblemDiagnostic) {
//                System.out.println("\n* onDialogNotice *\n");
            }

            @Override
            public void onDialogRelease(MAPDialog mapDialog) {
//                System.out.println("\n* onDialogRelease *\n");
            }

            @Override
            public void onDialogTimeout(MAPDialog mapDialog) {
//                System.out.println("\n* onDialogTimeout *\n");
            }
        });
    }

    public void sendSms(String msg, String destIsdnNumber, String origIsdnNumber) throws MAPException {
        int msgRef = 0;
        int segmCnt = 0;
        int segmNum = 0;

        AddressNature addressNature = AddressNature.international_number;
        NumberingPlan numberingPlan = NumberingPlan.ISDN;
        TypeOfNumber typeOfNumber = TypeOfNumber.InternationalNumber;
        NumberingPlanIdentification numberingPlanIdentification = NumberingPlanIdentification.ISDNTelephoneNumberingPlan;
        SmsCodingType smsCodingType = new SmsCodingType(SmsCodingType.VAL_GSM7);
        boolean statusReportRequest = false;
        Charset isoCharset = StandardCharsets.ISO_8859_1;

        MAPProvider mapProvider = this.mapMan.getMAPStack().getMAPProvider();

        MAPApplicationContext mapAppContext = MAPApplicationContext.getInstance(MAPApplicationContextName.shortMsgMORelayContext, MAPApplicationContextVersion.version3);
        String serviceCentreAddr = "";

        AddressString serviceCentreAddressDA = mapProvider.getMAPParameterFactory().createAddressString(addressNature, numberingPlan, serviceCentreAddr);
        SM_RP_DA da = mapProvider.getMAPParameterFactory().createSM_RP_DA(serviceCentreAddressDA);
        ISDNAddressString msisdn = mapProvider.getMAPParameterFactory().createISDNAddressString(addressNature, numberingPlan, origIsdnNumber);
        SM_RP_OA oa = mapProvider.getMAPParameterFactory().createSM_RP_OA_Msisdn(msisdn);

        AddressField destAddress = new AddressFieldImpl(typeOfNumber, numberingPlanIdentification, destIsdnNumber);
        DataCodingScheme dcs = new DataCodingSchemeImpl(smsCodingToDcs(smsCodingType));
        UserDataHeader udh = null;

        UserData userData = new UserDataImpl(msg, dcs, udh, isoCharset);
        ProtocolIdentifier pi = new ProtocolIdentifierImpl(0);
        ValidityPeriod validityPeriod = new ValidityPeriodImpl(169); // 3

        SmsSubmitTpdu tpdu = new SmsSubmitTpduImpl(false, false, statusReportRequest, ++mesRef, destAddress, pi, validityPeriod, userData);
        SmsSignalInfo si = mapProvider.getMAPParameterFactory().createSmsSignalInfo(tpdu, null);

        MAPDialogSms curDialog = mapProvider.getMAPServiceSms().createNewDialog(mapAppContext, this.mapMan.createOrigAddress(), null, this.mapMan.createDestAddress(), null);
        curDialog.addMoForwardShortMessageRequest(da, oa, si, null, null);
        curDialog.send();
    }

    private void onMoRequest(SM_RP_DA da, SM_RP_OA oa, SmsSignalInfo si, MAPDialogSms curDialog) {
        si.setGsm8Charset(Charset.forName("ISO-8859-1"));

        String origIsdnNumber = null;
        if (oa != null) {
            ISDNAddressString isdn = oa.getMsisdn();
            if (isdn != null)
                origIsdnNumber = isdn.getAddress();
        }

        try {
            String msg = null;
            String destIsdnNumber = null;
            if (si != null) {
                SmsTpdu tpdu = si.decodeTpdu(true);
                if (tpdu instanceof SmsSubmitTpdu) {
                    SmsSubmitTpdu dTpdu = (SmsSubmitTpdu) tpdu;
                    AddressField af = dTpdu.getDestinationAddress();
                    if (af != null)
                        destIsdnNumber = af.getAddressValue();
                    UserData ud = dTpdu.getUserData();
                    if (ud != null) {
                        ud.decode();
                        msg = ud.getDecodedMessage();

                        UserDataHeader udh = ud.getDecodedUserDataHeader();
                        if (udh != null) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("[");
                            int i2 = 0;
                            for (byte b : udh.getEncodedData()) {
                                int i1 = (b & 0xFF);
                                if (i2 == 0)
                                    i2 = 1;
                                else
                                    sb.append(", ");
                                sb.append(i1);
                            }
                            sb.append("] ");
                            msg = sb.toString() + msg;
                        }
                    }
                }
            }
        } catch (MAPException e) {
            System.out.println("Exception when decoding MoForwardShortMessageRequest tpdu : " + e.getMessage());
        }
    }
}
