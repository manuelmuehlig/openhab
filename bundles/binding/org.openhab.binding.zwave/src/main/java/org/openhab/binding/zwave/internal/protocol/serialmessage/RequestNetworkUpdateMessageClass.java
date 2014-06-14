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
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageClass;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessagePriority;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class processes a serial message from the zwave controller
 * @author Chris Jackson
 * @since 1.5.0
 */
public class RequestNetworkUpdateMessageClass  extends ZWaveCommandProcessor {
	private static final Logger logger = LoggerFactory.getLogger(RequestNetworkUpdateMessageClass.class);

	public SerialMessage doRequest(int nodeId) {
		SerialMessage newMessage = new SerialMessage(nodeId, SerialMessageClass.RequestNetworkUpdate, SerialMessageType.Request, SerialMessageClass.RequestNetworkUpdate, SerialMessagePriority.High);
    	byte[] newPayload = { (byte) nodeId };
    	newMessage.setMessagePayload(newPayload);
    	return newMessage;
	}

	@Override
	public boolean handleResponse(ZWaveController zController, SerialMessage lastSentMessage, SerialMessage incomingMessage) {
		logger.trace("Handle RequestNetworkUpdate Response");
		if(incomingMessage.getMessagePayloadByte(0) != 0x00)
			logger.debug("Request network update successfully placed on stack.");
		else
			logger.error("Request network update not placed on stack due to error.");
		
		checkTransactionComplete(lastSentMessage, incomingMessage);

		return true;
	}

	@Override
	public boolean handleRequest(ZWaveController zController, SerialMessage lastSentMessage, SerialMessage incomingMessage) {
		logger.trace("Handle RequestNetworkUpdate Request");
		switch(incomingMessage.getMessagePayloadByte(1)) {
		case 0:		//ZW_SUC_UPDATE_DONE
			logger.debug("Request network update DONE.");
			break;
		case 1:		// ZW_SUC_UPDATE_ABORT
			logger.error("Request network update ABORT.");
			break;
		case 2:		// ZW_SUC_UPDATE_WAIT
			logger.debug("Request network update WAIT.");
			break;
		case 3:		// ZW_SUC_UPDATE_DISABLED
			logger.debug("Request network update DISABLED.");
			break;
		case 4:		// ZW_SUC_UPDATE_OVERFLOW
			logger.error("Request network update OVERFLOW.");
			break;
		}
		
		return true;
	}
}
