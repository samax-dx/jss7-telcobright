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

package org.mobicents.protocols.ss7.cap.service.circuitSwitchedCall;

import java.io.IOException;

import org.mobicents.protocols.asn.AsnException;
import org.mobicents.protocols.asn.AsnInputStream;
import org.mobicents.protocols.asn.AsnOutputStream;
import org.mobicents.protocols.asn.Tag;
import org.mobicents.protocols.ss7.cap.api.CAPException;
import org.mobicents.protocols.ss7.cap.api.CAPParsingComponentException;
import org.mobicents.protocols.ss7.cap.api.CAPParsingComponentExceptionReason;
import org.mobicents.protocols.ss7.cap.api.primitives.AChChargingAddress;
import org.mobicents.protocols.ss7.cap.api.primitives.CAPExtensions;
import org.mobicents.protocols.ss7.cap.api.primitives.SendingSideID;
import org.mobicents.protocols.ss7.cap.api.service.circuitSwitchedCall.ApplyChargingRequestIndication;
import org.mobicents.protocols.ss7.cap.api.service.circuitSwitchedCall.primitive.CAMELAChBillingChargingCharacteristics;
import org.mobicents.protocols.ss7.cap.primitives.CAMELAChBillingChargingCharacteristicsImpl;
import org.mobicents.protocols.ss7.cap.primitives.CAPExtensionsImpl;
import org.mobicents.protocols.ss7.cap.primitives.SendingSideIDImpl;

/**
 * 
 * @author sergey vetyutnev
 * 
 */
public class ApplyChargingRequestIndicationImpl extends CircuitSwitchedCallMessageImpl implements ApplyChargingRequestIndication {

	public static final int _ID_aChBillingChargingCharacteristics = 0;
	public static final int _ID_partyToCharge = 2;
	public static final int _ID_extensions = 3;
	public static final int _ID_aChChargingAddress = 50;

	public static final String _PrimitiveName = "ApplyChargingRequestIndication";

	private CAMELAChBillingChargingCharacteristics aChBillingChargingCharacteristics;
	private SendingSideID partyToCharge;
	private CAPExtensions extensions;
	private AChChargingAddress aChChargingAddress;

	
	public ApplyChargingRequestIndicationImpl() {
	}
	
	public ApplyChargingRequestIndicationImpl(CAMELAChBillingChargingCharacteristics aChBillingChargingCharacteristics, SendingSideID partyToCharge,
			CAPExtensions extensions, AChChargingAddress aChChargingAddress) {
		this.aChBillingChargingCharacteristics = aChBillingChargingCharacteristics;
		this.partyToCharge = partyToCharge;
		this.extensions = extensions;
		this.aChChargingAddress = aChChargingAddress;
	}
	
	@Override
	public CAMELAChBillingChargingCharacteristics getAChBillingChargingCharacteristics() {
		return aChBillingChargingCharacteristics;
	}

	@Override
	public SendingSideID getPartyToCharge() {
		return partyToCharge;
	}

	@Override
	public CAPExtensions getExtensions() {
		return extensions;
	}

	@Override
	public AChChargingAddress getAChChargingAddress() {
		return aChChargingAddress;
	}

	
	@Override
	public int getTag() throws CAPException {
		return Tag.SEQUENCE;
	}

	@Override
	public int getTagClass() {
		return Tag.CLASS_UNIVERSAL;
	}

	@Override
	public boolean getIsPrimitive() {
		return false;
	}

	@Override
	public void decodeAll(AsnInputStream ansIS) throws CAPParsingComponentException {

		try {
			int length = ansIS.readLength();
			this._decode(ansIS, length);
		} catch (IOException e) {
			throw new CAPParsingComponentException("IOException when decoding " + _PrimitiveName + ": " + e.getMessage(), e,
					CAPParsingComponentExceptionReason.MistypedParameter);
		} catch (AsnException e) {
			throw new CAPParsingComponentException("AsnException when decoding " + _PrimitiveName + ": " + e.getMessage(), e,
					CAPParsingComponentExceptionReason.MistypedParameter);
		}
	}

	@Override
	public void decodeData(AsnInputStream ansIS, int length) throws CAPParsingComponentException {

		try {
			this._decode(ansIS, length);
		} catch (IOException e) {
			throw new CAPParsingComponentException("IOException when decoding " + _PrimitiveName + ": " + e.getMessage(), e,
					CAPParsingComponentExceptionReason.MistypedParameter);
		} catch (AsnException e) {
			throw new CAPParsingComponentException("AsnException when decoding " + _PrimitiveName + ": " + e.getMessage(), e,
					CAPParsingComponentExceptionReason.MistypedParameter);
		}
	}

	private void _decode(AsnInputStream ansIS, int length) throws CAPParsingComponentException, IOException, AsnException {

		this.aChBillingChargingCharacteristics = null;
		this.partyToCharge = null;
//		this.partyToCharge = new SendingSideIDImpl(LegType.leg1);
		this.extensions = null;
		this.aChChargingAddress = null; // TODO: DEFAULT legID:sendingSideID:leg1

		AsnInputStream ais = ansIS.readSequenceStreamData(length);
		while (true) {
			if (ais.available() == 0)
				break;

			int tag = ais.readTag();
			
			if (ais.getTagClass() == Tag.CLASS_CONTEXT_SPECIFIC) {
				switch (tag) {
				case _ID_aChBillingChargingCharacteristics:
					this.aChBillingChargingCharacteristics = new CAMELAChBillingChargingCharacteristicsImpl();
					((CAMELAChBillingChargingCharacteristicsImpl) this.aChBillingChargingCharacteristics).decodeAll(ais);
					break;
				case _ID_partyToCharge:
					AsnInputStream ais2 = ais.readSequenceStream();
					ais2.readTag();
					this.partyToCharge = new SendingSideIDImpl();
					((SendingSideIDImpl) this.partyToCharge).decodeAll(ais2);
					break;
				case _ID_extensions:
					this.extensions = new CAPExtensionsImpl();
					((CAPExtensionsImpl) this.extensions).decodeAll(ais);
					break;
				case _ID_aChChargingAddress:
					ais.advanceElement(); // TODO: implement it
					break;

				default:
					ais.advanceElement();
					break;
				}
			} else {
				ais.advanceElement();
			}
		}

		if (this.aChBillingChargingCharacteristics==null)
			throw new CAPParsingComponentException("Error while decoding " + _PrimitiveName + ": aChBillingChargingCharacteristics is mandatory but not found ",
					CAPParsingComponentExceptionReason.MistypedParameter);
	}

	@Override
	public void encodeAll(AsnOutputStream asnOs) throws CAPException {
		this.encodeAll(asnOs, this.getTagClass(), this.getTag());
	}

	@Override
	public void encodeAll(AsnOutputStream asnOs, int tagClass, int tag) throws CAPException {

		try {
			asnOs.writeTag(tagClass, this.getIsPrimitive(), tag);
			int pos = asnOs.StartContentDefiniteLength();
			this.encodeData(asnOs);
			asnOs.FinalizeContent(pos);
		} catch (AsnException e) {
			throw new CAPException("AsnException when encoding " + _PrimitiveName + ": " + e.getMessage(), e);
		}
	}

	@Override
	public void encodeData(AsnOutputStream aos) throws CAPException {

		if (this.aChBillingChargingCharacteristics == null)
			throw new CAPException("Error while encoding " + _PrimitiveName + ": aChBillingChargingCharacteristics must not be null");

		try {
			if (this.aChBillingChargingCharacteristics != null)
				((CAMELAChBillingChargingCharacteristicsImpl) this.aChBillingChargingCharacteristics).encodeAll(aos, Tag.CLASS_CONTEXT_SPECIFIC,
						_ID_aChBillingChargingCharacteristics);
			if (this.partyToCharge != null) {
				aos.writeTag(Tag.CLASS_CONTEXT_SPECIFIC, false, _ID_partyToCharge);
				int pos = aos.StartContentDefiniteLength();
				((SendingSideIDImpl) this.partyToCharge).encodeAll(aos);
				aos.FinalizeContent(pos);
			}			
			if (this.extensions != null)
				((CAPExtensionsImpl) this.extensions).encodeAll(aos, Tag.CLASS_CONTEXT_SPECIFIC, _ID_extensions);
			if (this.aChChargingAddress != null) {
				// TODO: implement it - _ID_cause
			}

		} catch (AsnException e) {
			throw new CAPException("AsnException when encoding " + _PrimitiveName + ": " + e.getMessage(), e);
		}
	}

	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder();
		sb.append(_PrimitiveName);
		sb.append(" [");

		if (this.aChBillingChargingCharacteristics != null) {
			sb.append("aChBillingChargingCharacteristics=");
			sb.append(aChBillingChargingCharacteristics.toString());
		}
		if (this.partyToCharge != null) {
			sb.append(", partyToCharge=");
			sb.append(partyToCharge.toString());
		}
		if (this.extensions != null) {
			sb.append(", extensions=");
			sb.append(extensions.toString());
		}
		if (this.aChChargingAddress != null) {
			sb.append(", aChChargingAddress=");
			sb.append(aChChargingAddress.toString());
		}

		sb.append("]");

		return sb.toString();
	}
}

