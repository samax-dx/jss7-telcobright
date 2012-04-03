/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.sccp.impl;

import java.io.IOException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.mtp.Mtp3;
import org.mobicents.protocols.ss7.mtp.Mtp3TransferPrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3UserPart;
import org.mobicents.protocols.ss7.sccp.SccpListener;
import org.mobicents.protocols.ss7.sccp.impl.message.EncodingResultData;
import org.mobicents.protocols.ss7.sccp.impl.message.MessageFactoryImpl;
import org.mobicents.protocols.ss7.sccp.impl.message.SccpAddressedMessageImpl;
import org.mobicents.protocols.ss7.sccp.impl.message.SccpDataMessageImpl;
import org.mobicents.protocols.ss7.sccp.impl.message.SccpMessageImpl;
import org.mobicents.protocols.ss7.sccp.impl.message.SccpNoticeMessageImpl;
import org.mobicents.protocols.ss7.sccp.impl.parameter.ParameterFactoryImpl;
import org.mobicents.protocols.ss7.sccp.impl.router.LoadSharingAlgorithm;
import org.mobicents.protocols.ss7.sccp.impl.router.LongMessageRule;
import org.mobicents.protocols.ss7.sccp.impl.router.LongMessageRuleType;
import org.mobicents.protocols.ss7.sccp.impl.router.Mtp3ServiceAccessPoint;
import org.mobicents.protocols.ss7.sccp.impl.router.Rule;
import org.mobicents.protocols.ss7.sccp.impl.router.RuleType;
import org.mobicents.protocols.ss7.sccp.message.SccpDataMessage;
import org.mobicents.protocols.ss7.sccp.message.SccpNoticeMessage;
import org.mobicents.protocols.ss7.sccp.parameter.GlobalTitle;
import org.mobicents.protocols.ss7.sccp.parameter.ReturnCause;
import org.mobicents.protocols.ss7.sccp.parameter.ReturnCauseValue;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;

/**
 * 
 * @author amit bhayani
 * @author sergey vetyutnev
 * 
 */
public class SccpRoutingControl {
	private static final Logger logger = Logger.getLogger(SccpRoutingControl.class);

	private SccpStackImpl sccpStackImpl = null;
	private SccpProviderImpl sccpProviderImpl = null;

	private SccpManagement sccpManagement = null;

	private MessageFactoryImpl messageFactory;

	public SccpRoutingControl(SccpProviderImpl sccpProviderImpl, SccpStackImpl sccpStackImpl) {
		this.messageFactory = sccpStackImpl.messageFactory;
		this.sccpProviderImpl = sccpProviderImpl;
		this.sccpStackImpl = sccpStackImpl;
	}

	public SccpManagement getSccpManagement() {
		return sccpManagement;
	}

	public void setSccpManagement(SccpManagement sccpManagement) {
		this.sccpManagement = sccpManagement;
	}

	public void start() {
		// NOP for now

	}

	public void stop() {
		// NOP for now

	}

	protected void routeMssgFromMtp(SccpAddressedMessageImpl msg) throws IOException {
		// TODO if the local SCCP or node is in an overload condition, SCRC
		// shall inform SCMG
		
		SccpAddress calledPartyAddress = msg.getCalledPartyAddress();
		RoutingIndicator ri = calledPartyAddress.getAddressIndicator().getRoutingIndicator();
		switch (ri) {
		case ROUTING_BASED_ON_DPC_AND_SSN:
			int ssn = msg.getCalledPartyAddress().getSubsystemNumber();
			if (ssn == 1) {
				// This is for management
				if (msg instanceof SccpDataMessage) {
					this.sccpManagement.onManagementMessage((SccpDataMessage) msg);
				}
				return;
			}

			SccpListener listener = this.sccpProviderImpl.getSccpListener(ssn);
			if (listener == null) {
				// SCCP user with received SSN is not available - Notify Management
				this.sccpManagement.recdMsgForProhibitedSsn(msg, ssn);

				if (logger.isEnabledFor(Level.WARN)) {
					logger.warn(String.format("Received SccpMessage=%s from MTP but the SSN is not available for local routing", msg));
				}
				this.sendSccpError(msg, ReturnCauseValue.SUBSYSTEM_FAILURE);
				return;
			}

			// Notify Listener
			try {
				if (msg instanceof SccpDataMessage) {
					if (logger.isDebugEnabled()) {
						logger.debug(String.format("Local deliver : SCCP Data Message=%s", msg.toString()));
					}
					listener.onMessage((SccpDataMessage) msg);
				} else if (msg instanceof SccpNoticeMessage) {
					if (logger.isDebugEnabled()) {
						logger.debug(String.format("Local deliver : SCCP Notice Message=%s", msg.toString()));
					}
					listener.onNotice((SccpNoticeMessage) msg);
				} else {
					// TODO: process connection-oriented messages
				}
				
			} catch (Exception e) {
				if (logger.isEnabledFor(Level.WARN)) {
					logger.warn(String.format("Exception from the listener side when delivering SccpData to ssn=%d: Message=%s", msg.getOriginLocalSsn(), msg), e);
				}
			}
			break;
		case ROUTING_BASED_ON_GLOBAL_TITLE:
			this.translationFunction(msg);
			break;
		default:
			// This can never happen
			logger.error(String.format("Invalid Routing Indictaor received for message=%s from MTP3", msg));
			break;
		}
	}

	protected void routeMssgFromSccpUser(SccpAddressedMessageImpl msg) throws IOException {
		this.route(msg);
	}

	protected ReturnCauseValue send(SccpMessageImpl message) throws IOException {

		int dpc = message.getOutgoingDpc();
		int sls = message.getSls();

		Mtp3ServiceAccessPoint sap = this.sccpStackImpl.router.findMtp3ServiceAccessPoint(dpc, sls);
		if (sap == null) {
			if (logger.isEnabledFor(Level.WARN)) {
				logger.warn(String.format("SccpMessage for sending=%s but no matching dpc=%d & sls=%d SAP found", message, dpc, sls));
			}
			return ReturnCauseValue.SCCP_FAILURE;
		}

		Mtp3UserPart mup = this.sccpStackImpl.getMtp3UserPart(sap.getMtp3Id());
		if (mup == null) {
			if (logger.isEnabledFor(Level.WARN)) {
				logger.warn(String.format("SccpMessage for sending=%s but no matching Mtp3UserPart found for Id=%d", message, sap.getMtp3Id()));
			}
			return ReturnCauseValue.SCCP_FAILURE;
		}

		LongMessageRule lmr = this.sccpStackImpl.router.findLongMessageRule(dpc);
		LongMessageRuleType lmrt = LongMessageRuleType.LongMessagesForbidden;
		if (lmr != null)
			lmrt = lmr.getLongMessageRuleType();
		EncodingResultData erd = message.encode(lmrt, mup.getMaxUserDataLength(dpc), logger);
		switch (erd.getEncodingResult()) {
		case Success:
			if (erd.getSolidData() != null) {
				// nonsegmented data
				Mtp3TransferPrimitive msg = new Mtp3TransferPrimitive(Mtp3._SI_SERVICE_SCCP, sap.getNi(), 0, sap.getOpc(), dpc, sls, erd.getSolidData());
				mup.sendMessage(msg);
			} else {
				// segmented data
				for (byte[] bf : erd.getSegementedData()) {
					Mtp3TransferPrimitive msg = new Mtp3TransferPrimitive(Mtp3._SI_SERVICE_SCCP, sap.getNi(), 0, sap.getOpc(), dpc, sls, bf);
					mup.sendMessage(msg);
				}
			}
			return null;
		
		case ReturnFailure:
			return erd.getReturnCause();

		default:
			String em = String.format("Error %s when encoding a SccpMessage\n%s", erd.getEncodingResult().toString(), message.toString());
			if (logger.isEnabledFor(Level.WARN)) {
				logger.warn(em);
			}
			throw new IOException(em);
		}
	}

	private enum TranslationAddressCheckingResult {
		destinationAvailable, 
		destinationUnavailable_SubsystemFailure, 
		destinationUnavailable_MtpFailure, 
		translationFailure;
	}

	private TranslationAddressCheckingResult checkTranslationAddress(SccpAddressedMessageImpl msg, Rule rule, SccpAddress translationAddress, String destName) {

		if (translationAddress == null) {
			if (logger.isEnabledFor(Level.WARN)) {
				logger.warn(String.format("Received SccpMessage=% for Translation but no matching %s Address defined for Rule=%s for routing", msg, destName,
						rule));
			}
			return TranslationAddressCheckingResult.translationFailure;
		}

		if (!translationAddress.getAddressIndicator().pcPresent()) {

			// destination PC is absent - bad rule
			if (logger.isEnabledFor(Level.WARN)) {
				logger.warn(String.format("Received SccpMessage=%s for Translation but no PC is present for %s Address ", msg, destName));
			}
			return TranslationAddressCheckingResult.translationFailure;
		}

		if (this.sccpStackImpl.router.spcIsLocal(translationAddress.getSignalingPointCode())) {
			// destination PC is local
			int ssn = translationAddress.getSubsystemNumber();
			if (ssn == 1 || this.sccpProviderImpl.getSccpListener(ssn) != null) {
				return TranslationAddressCheckingResult.destinationAvailable;
			} else {
				return TranslationAddressCheckingResult.destinationUnavailable_SubsystemFailure;
			}
		}

		// Check if the DPC is prohibited
		RemoteSignalingPointCode remoteSpc = this.sccpStackImpl.getSccpResource().getRemoteSpcByPC(translationAddress.getSignalingPointCode());
		if (remoteSpc == null) {
			if (logger.isEnabledFor(Level.WARN)) {
				logger.warn(String.format("Received SccpMessage=%s for Translation but no %s Remote Signaling Pointcode = %d resource defined ", msg, destName,
						translationAddress.getSignalingPointCode()));
			}
			return TranslationAddressCheckingResult.translationFailure;
		}
		
		if (remoteSpc.isRemoteSpcProhibited()) {
			return TranslationAddressCheckingResult.destinationUnavailable_MtpFailure;
		}			

		if (translationAddress.getAddressIndicator().getRoutingIndicator() == RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN) {
			int ssn = translationAddress.getSubsystemNumber();
			if (ssn != 1) {
				RemoteSubSystem remoteSubSystem = this.sccpStackImpl.getSccpResource().getRemoteSsn(translationAddress.getSignalingPointCode(), ssn);
				if (remoteSubSystem == null) {
					if (logger.isEnabledFor(Level.WARN)) {
						logger.warn(String.format("Received SccpMessage=%s for Translation but no %s Remote SubSystem = %d (dpc=%d) resource defined ", msg,
								destName, translationAddress.getSubsystemNumber(), translationAddress.getSignalingPointCode()));
					}
					return TranslationAddressCheckingResult.translationFailure;
				}
				if (remoteSubSystem.isRemoteSsnProhibited()) {
					return TranslationAddressCheckingResult.destinationUnavailable_SubsystemFailure;
				}
			}
		}

		return TranslationAddressCheckingResult.destinationAvailable;
	}

	private void translationFunction(SccpAddressedMessageImpl msg) throws IOException {

		// checking for hop counter
		if (!msg.reduceHopCounter()) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Received SccpMessage for Translation but hop counter violation detected\nSccpMessage=%s", msg));
			}
			this.sendSccpError(msg, ReturnCauseValue.HOP_COUNTER_VIOLATION);
			return;
		}

		SccpAddress calledPartyAddress = msg.getCalledPartyAddress();

		Rule rule = this.sccpStackImpl.router.findRule(calledPartyAddress);
		if (rule == null) {
			if (logger.isEnabledFor(Level.WARN)) {
				logger.warn(String.format("Received SccpMessage for Translation but no matching Rule found for local routing\nSccpMessage=%s", msg));
			}
			// Translation failed return error
			this.sendSccpError(msg, ReturnCauseValue.NO_TRANSLATION_FOR_ADDRESS);
			return;
		}

		// Check whether to use primary or backup address
		SccpAddress translationAddressPri = this.sccpStackImpl.router.getPrimaryAddress(rule.getPrimaryAddressId());
		TranslationAddressCheckingResult resPri = this.checkTranslationAddress(msg, rule, translationAddressPri, "primary");
		if (resPri == TranslationAddressCheckingResult.translationFailure) {
			this.sendSccpError(msg, ReturnCauseValue.NO_TRANSLATION_FOR_ADDRESS);
			return;
		}

		SccpAddress translationAddressSec = null;
		TranslationAddressCheckingResult resSec = TranslationAddressCheckingResult.destinationUnavailable_SubsystemFailure; 
		if (rule.getRuleType() != RuleType.Solitary) {
			translationAddressSec = this.sccpStackImpl.router.getBackupAddress(rule.getSecondaryAddressId());
			resSec = this.checkTranslationAddress(msg, rule, translationAddressSec, "secondary");
			if (resSec == TranslationAddressCheckingResult.translationFailure) {
				this.sendSccpError(msg, ReturnCauseValue.NO_TRANSLATION_FOR_ADDRESS);
				return;
			}
		}		

		if (resPri != TranslationAddressCheckingResult.destinationAvailable && resSec != TranslationAddressCheckingResult.destinationAvailable) {
			switch (resPri) {
			case destinationUnavailable_SubsystemFailure:
				this.sendSccpError(msg, ReturnCauseValue.SUBSYSTEM_FAILURE);
				return;
			case destinationUnavailable_MtpFailure:
				this.sendSccpError(msg, ReturnCauseValue.MTP_FAILURE);
				return;
			default:
				this.sendSccpError(msg, ReturnCauseValue.SCCP_FAILURE);
				return;
			}
		}

		SccpAddress translationAddress;
		if (resPri == TranslationAddressCheckingResult.destinationAvailable && resSec != TranslationAddressCheckingResult.destinationAvailable) {
			translationAddress = translationAddressPri;
		} else if (resPri != TranslationAddressCheckingResult.destinationAvailable && resSec == TranslationAddressCheckingResult.destinationAvailable) {
			translationAddress = translationAddressSec;
		} else {
			if (rule.getRuleType() != RuleType.Loadshared) {
				translationAddress = translationAddressPri;
			} else {
				// loadsharing case and both destinations are available
				if (msg.getSccpCreatesSls()) {
					if (this.sccpStackImpl.newSelector())
						translationAddress = translationAddressPri;
					else
						translationAddress = translationAddressSec;
				} else {
					if (this.selectLoadSharingRoute(rule.getLoadSharingAlgorithm(), msg))
						translationAddress = translationAddressPri;
					else
						translationAddress = translationAddressSec;
				}
			}
		}

		// translate address
		SccpAddress address = rule.translate(calledPartyAddress, translationAddress);
		msg.setCalledPartyAddress(address);

		if (logger.isDebugEnabled()) {
			logger.debug(String.format("CalledPartyAddress after translation = %s", address));
		}

		// routing procedures then continue's
		this.route(msg);
	}

	private boolean selectLoadSharingRoute(LoadSharingAlgorithm loadSharingAlgo, SccpAddressedMessageImpl msg) {

		if (loadSharingAlgo == LoadSharingAlgorithm.Bit4) {
			if ((msg.getSls() & 0x10) == 0)
				return true;
			else
				return false;
		} else if (loadSharingAlgo == LoadSharingAlgorithm.Bit3) {
			if ((msg.getSls() & 0x08) == 0)
				return true;
			else
				return false;
		} else {
			// TODO: implement complicated algorithms for selecting a destination
			// (CallingPartyAddress & SLS depended)
			// Look at Q.815 8.1.3 - active loadsharing 
			return true;
		}
	}

	private void route(SccpAddressedMessageImpl msg) throws IOException {

		SccpAddress calledPartyAddress = msg.getCalledPartyAddress();

		int dpc = calledPartyAddress.getSignalingPointCode();
		int ssn = calledPartyAddress.getSubsystemNumber();
		GlobalTitle gt = calledPartyAddress.getGlobalTitle();

		if (calledPartyAddress.getAddressIndicator().pcPresent()) {
			// DPC present

			if (this.sccpStackImpl.router.spcIsLocal(dpc)) {
				// This message is for local routing

				if (ssn > 0) {
					// if a non-zero SSN is present but not the GT (case 2 a) of
					// 2.2.2), then the message is passed based on the message
					// type to either connection-oriented control or
					// connectionless control and based on the availability of
					// the subsystem;
					if (ssn == 1) {
						// This is for management
						if (msg instanceof SccpDataMessage) {
							this.sccpManagement.onManagementMessage((SccpDataMessage) msg);
						}
						return;
					}

					SccpListener listener = this.sccpProviderImpl.getSccpListener(ssn);
					if (listener == null) {
						if (logger.isEnabledFor(Level.WARN)) {
							logger.warn(String.format("Received SccpMessage=%s for routing but the SSN is not available for local routing", msg));
						}
						this.sendSccpError(msg, ReturnCauseValue.SUBSYSTEM_FAILURE);
						return;
					}
					// Notify Listener
					try {
						// JIC: user may behave bad and throw something here.
						if (msg instanceof SccpDataMessage) {
							if (logger.isDebugEnabled()) {
								logger.debug(String.format("Local deliver : SCCP Data Message=%s", msg.toString()));
							}
							listener.onMessage((SccpDataMessage) msg);
						} else if (msg instanceof SccpNoticeMessage) {
							if (logger.isDebugEnabled()) {
								logger.debug(String.format("Local deliver : SCCP Notice Message=%s", msg.toString()));
							}
							listener.onNotice((SccpNoticeMessage) msg);
						} else {
							// TODO: process connection-oriented messages
						}
					} catch (Exception e) {
						if (logger.isEnabledFor(Level.WARN)) {
							logger.warn(String.format("Exception from the listener side when delivering SccpData to ssn=%d: Message=%s", msg.getOriginLocalSsn(), msg), e);
						}
					}
				} else if (gt != null) {
					// if the GT is present but no SSN or a zero SSN is present
					// (case 2 b) of 2.2.2), then the message is passed to the
					// translation function;

					if (calledPartyAddress.isTranslated()) {
						// Called address already translated once. This is loop
						// condition and error
						logger.error(String.format("Droping message. Received SCCPMessage=%s for routing but CalledPartyAddress is already translated once",
								msg));
						this.sendSccpError(msg, ReturnCauseValue.SCCP_FAILURE);
						return;
					}

					this.translationFunction(msg);

				} else {
					// if an SSN equal to zero is present but not a GT (case 2
					// d) of 2.2.2), then the address information is incomplete
					// and the message shall be discarded. This abnormality is
					// similar to the one described in 3.8.3.3, item 1) b6.

					logger.error(String.format("Received SCCPMessage=%s for routing, but neither SSN nor GT present", msg));
					this.sendSccpError(msg, ReturnCauseValue.NO_TRANSLATION_FOR_NATURE);
				}

			} else {
				// DPC present but its not local pointcode. This message should be Tx to MTP

				// Check if the DPC is not prohibited
				RemoteSignalingPointCode remoteSpc = this.sccpStackImpl.getSccpResource().getRemoteSpcByPC(dpc);
				if (remoteSpc == null) {
					if (logger.isEnabledFor(Level.WARN)) {
						logger.warn(String.format("Received SccpMessage=%s for routing but no Remote Signaling Pointcode = %d resource defined ", msg, dpc));
					}
					this.sendSccpError(msg, ReturnCauseValue.SCCP_FAILURE);
					return;
				}
				if (remoteSpc.isRemoteSpcProhibited()) {
					if (logger.isEnabledFor(Level.WARN)) {
						logger.warn(String.format("Received SccpMessage=%s for routing but Remote Signaling Pointcode = %d is prohibited", msg, dpc));
					}
					this.sendSccpError(msg, ReturnCauseValue.MTP_FAILURE);
					return;
				}

				if (ssn > 1) {  // was: ssn > 1 ???
					if (calledPartyAddress.getAddressIndicator().getRoutingIndicator() == RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN) {
						// if a non-zero SSN is present but not the GT (case 2a) of 2.2.2), 
						// then the called party address provided shall
						// contain this SSN and the routing indicator shall be set
						// to "Route on SSN"; See 2.2.2.1 point 2 of ITU-T Q.714
						// If routing based on SSN, check remote SSN is available
						RemoteSubSystem remoteSsn = this.sccpStackImpl.getSccpResource().getRemoteSsn(dpc, calledPartyAddress.getSubsystemNumber());
						if (remoteSsn == null) {
							if (logger.isEnabledFor(Level.WARN)) {
								logger.warn(String.format("Received SCCPMessage=%s for routing, but no Remote SubSystem = %d resource defined ", msg,
										calledPartyAddress.getSubsystemNumber()));
							}
							// Routing failed return error
							this.sendSccpError(msg, ReturnCauseValue.SCCP_FAILURE);
							return;
						}

						if (remoteSsn.isRemoteSsnProhibited()) {
							if (logger.isEnabledFor(Level.WARN)) {
								logger.warn(String.format("Routing of Sccp Message=%s failed as Remote SubSystem = %d is prohibited ", msg,
										calledPartyAddress.getSubsystemNumber()));
							}
							this.sendSccpError(msg, ReturnCauseValue.SUBSYSTEM_FAILURE);
							return;
						}
					}

					// send to MTP
					if (logger.isDebugEnabled()) {
						logger.debug(String.format("Tx : SCCP Message=%s", msg.toString()));
					}
					this.sendMessageToMtp(msg);
				} else if (gt != null) {

					// if the GT is present but no SSN or a zero SSN is present
					// (case 2 b) of 2.2.2), then the DPC identifies where the
					// global title translation occurs. The called party address
					// provided shall contain this GT and the routing indicator
					// shall be set to "Route on GT"; See 2.2.2.1 point 3 of
					// ITU-T Q.714

					// send to MTP
					if (logger.isDebugEnabled()) {
						logger.debug(String.format("Tx : SCCP Message=%s", msg.toString()));
					}
					this.sendMessageToMtp(msg);
				} else {

					logger.error(String.format("Received SCCPMessage=%s for routing, but neither SSN nor GT present", msg));
					this.sendSccpError(msg, ReturnCauseValue.NO_TRANSLATION_FOR_NATURE);
				}
			}
		} else {
			// DPC not present

			// If the DPC is not present, (case 3 of 2.2.2), then a global title
			// translation is required before the message can be sent out.
			// Translation results in a DPC and possibly a new SSN or new GT or
			// both.

			if (gt == null) {
				// No DPC, and no GT. This is insufficient information
				if (logger.isEnabledFor(Level.WARN)) {
					logger.warn(String.format("Received SccpMessage=%s for routing from local SCCP user part but no pointcode and no GT or SSN included", msg,
							dpc));
				}
				this.sendSccpError(msg, ReturnCauseValue.NO_TRANSLATION_FOR_NATURE);
				return;
			}

			if (calledPartyAddress.isTranslated()) {
				// Called address already translated once. This is loop
				// condition and error
				logger.error(String.format("Droping message. Received SCCPMessage=%s for Routing , but CalledPartyAddress is already translated once", msg));
				this.sendSccpError(msg, ReturnCauseValue.SCCP_FAILURE);
				return;
			}

			this.translationFunction(msg);
		}
	}

	protected void sendMessageToMtp(SccpAddressedMessageImpl msg) throws IOException {

		msg.setOutgoingDpc(msg.getCalledPartyAddress().getSignalingPointCode());

		if (msg.getSccpCreatesSls()) {
			msg.setSls(this.sccpStackImpl.newSls());
		}

		ReturnCauseValue er = this.send(msg);
		if (er != null) {
			this.sendSccpError(msg, er);
		}
	}

	protected void sendSccpError(SccpAddressedMessageImpl msg, ReturnCauseValue returnCauseInt) throws IOException {

		// sending only if "ReturnMessageOnError" flag of the origin message
		if (!msg.getReturnMessageOnError())
			return;
		
		// in case we did not consume and this message has arrived from
		// other end.... we have to reply in some way Q.714 4.2 for now
		SccpNoticeMessageImpl ans = null;
		// not sure if its proper
		ReturnCause returnCause = ((ParameterFactoryImpl)this.sccpProviderImpl.getParameterFactory()).createReturnCause(returnCauseInt);
		if (msg instanceof SccpDataMessageImpl) {
			SccpDataMessageImpl msgData = (SccpDataMessageImpl) msg;
			ans = (SccpNoticeMessageImpl) messageFactory.createNoticeMessage(msg.getType(), returnCause, msg.getCallingPartyAddress(),
					msg.getCalledPartyAddress(), msgData.getData(), msgData.getHopCounter(), msgData.getImportance());
		} else {
			// TODO: Implement return errors for connection-oriented messages
		}

		if (ans != null) {
			if (msg.getIsMtpOriginated()) {
				
				// send to MTP3
				if (logger.isDebugEnabled()) {
					logger.debug(String.format("sendSccpError to a remote user: SCCP Message=%s", msg.toString()));
				}
				this.route(ans);
			} else {
				
				// deliver locally
				if (logger.isDebugEnabled()) {
					logger.debug(String.format("sendSccpError to a local user: SCCP Message=%s", msg.toString()));
				}
				SccpListener listener = this.sccpProviderImpl.getSccpListener(msg.getOriginLocalSsn());
				if (listener != null) {
					try {
						listener.onNotice(ans);
					} catch (Exception e) {
						if (logger.isEnabledFor(Level.WARN)) {
							logger.warn(String.format("Exception from the listener side when delivering SccpNotice to ssn=%d: Message=%s", msg.getOriginLocalSsn(), msg), e);
						}
					}
				}
			}
		}
	}
}

