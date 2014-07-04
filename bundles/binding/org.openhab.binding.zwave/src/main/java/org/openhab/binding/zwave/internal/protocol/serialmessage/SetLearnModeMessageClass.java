/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal.protocol.serialmessage;

import org.openhab.binding.zwave.internal.protocol.SerialMessage;
import org.openhab.binding.zwave.internal.protocol.ZWaveController;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveInclusionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class processes a serial message from the zwave controller
 * @author Chris Jackson
 * @since 1.6.0
 */
public class SetLearnModeMessageClass extends ZWaveCommandProcessor {
	private static final Logger logger = LoggerFactory.getLogger(SetLearnModeMessageClass.class);

	private final int ASSIGN_COMPLETE          = 0x00;
	private final int ASSIGN_NODEID_DONE       = 0x01;
	private final int ASSIGN_RANGE_INFO_UPDATE = 0x02;
	

	public SerialMessage doRequest(boolean start) {
		logger.debug("Setting controller into LEARN mode.");

		// Queue the request
		SerialMessage newMessage = new SerialMessage(SerialMessage.SerialMessageClass.SetLearnMode, SerialMessage.SerialMessageType.Request,
				null, SerialMessage.SerialMessagePriority.High);
		byte[] newPayload = { (byte) (start ? 1 : 0) };

    	newMessage.setMessagePayload(newPayload);
    	return newMessage;
    }

	@Override
	public boolean handleRequest(ZWaveController zController, SerialMessage lastSentMessage, SerialMessage incomingMessage) {
		switch(incomingMessage.getMessagePayloadByte(1)) {
		case ASSIGN_COMPLETE:
			logger.debug("Learn ASSIGN_COMPLETE.");
//			zController.notifyEventListeners(new ZWaveInclusionEvent(ZWaveInclusionEvent.Type.IncludeStart));
			break;
		case ASSIGN_NODEID_DONE:
			logger.debug("Learn ASSIGN_NODEID_DONE.");
//			zController.notifyEventListeners(new ZWaveInclusionEvent(ZWaveInclusionEvent.Type.IncludeStart));
			break;
		case ASSIGN_RANGE_INFO_UPDATE:
			logger.debug("Learn ASSIGN_RANGE_INFO_UPDATE.");
//			zController.notifyEventListeners(new ZWaveInclusionEvent(ZWaveInclusionEvent.Type.IncludeStart));
			break;
		default:
			logger.debug("Unknown request ({}).", incomingMessage.getMessagePayloadByte(1));
			break;
		}
		checkTransactionComplete(lastSentMessage, incomingMessage);

		return transactionComplete;
	}
}
