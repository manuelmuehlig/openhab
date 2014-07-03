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
 * @since 1.6.0
 */
public class RequestNetworkUpdateMessageClass  extends ZWaveCommandProcessor {
	private static final Logger logger = LoggerFactory.getLogger(RequestNetworkUpdateMessageClass.class);

	final int ZW_SUC_UPDATE_DONE      = 0x00;
	final int ZW_SUC_UPDATE_ABORT     = 0x01;
	final int ZW_SUC_UPDATE_WAIT      = 0x02;
	final int ZW_SUC_UPDATE_DISABLED  = 0x03;
	final int ZW_SUC_UPDATE_OVERFLOW  = 0x04;

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
			logger.debug("Request node info successfully placed on stack.");
		else
			logger.error("Request node info not placed on stack due to error.");
		
		checkTransactionComplete(lastSentMessage, incomingMessage);

		return true;
	}

	@Override
	public boolean handleRequest(ZWaveController zController, SerialMessage lastSentMessage, SerialMessage incomingMessage) {
		logger.trace("Handle RequestNetworkUpdate Request");
		switch(incomingMessage.getMessagePayloadByte(0)) {
		case ZW_SUC_UPDATE_DONE:
			logger.debug("RequestNetworkUpdate DONE.");
			break;
		case ZW_SUC_UPDATE_ABORT:
			logger.debug("RequestNetworkUpdate ABORT.");
			break;
		case ZW_SUC_UPDATE_WAIT:
			logger.debug("RequestNetworkUpdate WAIT.");
			break;
		case ZW_SUC_UPDATE_DISABLED:
			logger.debug("RequestNetworkUpdate DISABLED.");
			break;
		case ZW_SUC_UPDATE_OVERFLOW:
			logger.debug("RequestNetworkUpdate OVERFLOW.");
			break;
		}
		
		checkTransactionComplete(lastSentMessage, incomingMessage);

		return true;
	}
}

