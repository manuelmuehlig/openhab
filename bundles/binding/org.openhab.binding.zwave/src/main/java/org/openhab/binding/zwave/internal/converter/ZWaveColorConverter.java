/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal.converter;

import java.util.Map;

import org.openhab.binding.zwave.internal.converter.command.IntegerCommandConverter;
import org.openhab.binding.zwave.internal.converter.command.MultiLevelOnOffCommandConverter;
import org.openhab.binding.zwave.internal.converter.command.MultiLevelPercentCommandConverter;
import org.openhab.binding.zwave.internal.converter.command.ZWaveCommandConverter;
import org.openhab.binding.zwave.internal.converter.state.IntegerDecimalTypeConverter;
import org.openhab.binding.zwave.internal.converter.state.IntegerOnOffTypeConverter;
import org.openhab.binding.zwave.internal.converter.state.IntegerPercentTypeConverter;
import org.openhab.binding.zwave.internal.converter.state.ZWaveStateConverter;
import org.openhab.binding.zwave.internal.protocol.SerialMessage;
import org.openhab.binding.zwave.internal.protocol.ZWaveController;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveColorCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveColorCommandClass.ZWaveColorType;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveColorCommandClass.ZWaveColorValueEvent;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveCommandClassValueEvent;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.Item;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * ZWaveAlarmConverter class. Converter for communication with the 
 * {@link ZWaveAlarmCommandClass}. Implements polling of the alarm 
 * status and receiving of alarm  events.
 * @author Chris Jackson
 * @since 1.6.0
 */
public class ZWaveColorConverter extends ZWaveCommandClassConverter<ZWaveColorCommandClass> {

	private static final Logger logger = LoggerFactory.getLogger(ZWaveColorConverter.class);
	private static final int REFRESH_INTERVAL = 0; // refresh interval in seconds for the binary switch;

	/**
	 * Constructor. Creates a new instance of the {@link ZWaveColorConverter} class.
	 * @param controller the {@link ZWaveController} to use for sending messages.
	 * @param eventPublisher the {@link EventPublisher} to use to publish events.
	 */
	public ZWaveColorConverter(ZWaveController controller, EventPublisher eventPublisher) {
		super(controller, eventPublisher);
		
		// State and commmand converters used by this converter.
		this.addCommandConverter(new MultiLevelOnOffCommandConverter());
		this.addCommandConverter(new MultiLevelPercentCommandConverter());
		this.addCommandConverter(new IntegerCommandConverter());

		this.addStateConverter(new IntegerDecimalTypeConverter());
		this.addStateConverter(new IntegerPercentTypeConverter());
		this.addStateConverter(new IntegerOnOffTypeConverter());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SerialMessage executeRefresh(ZWaveNode node, 
			ZWaveColorCommandClass commandClass, int endpointId, Map<String,String> arguments) {
		logger.debug("NODE {}: Generating poll message for {}, endpoint {}", node.getNodeId(), commandClass.getCommandClass().getLabel(), endpointId);
		String colorType = arguments.get("color_type");

		if (colorType != null) {
			return node.encapsulate(commandClass.getValueMessage(Integer.parseInt(colorType)), commandClass, endpointId);
		} else {
			return node.encapsulate(commandClass.getValueMessage(0), commandClass, endpointId);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handleEvent(ZWaveCommandClassValueEvent event, Item item, Map<String,String> arguments) {
		ZWaveStateConverter<?,?> converter = this.getStateConverter(item, event.getValue());
		String colorType = arguments.get("color_type");
		ZWaveColorValueEvent colorEvent = (ZWaveColorValueEvent)event;
		
		if (converter == null) {
			logger.warn("NODE {}: No converter found for item = {}, endpoint = {}, ignoring event.", event.getNodeId(), item.getName(), event.getEndpoint());
			return;
		}

		// Don't trigger event if this item is bound to another alarm type
		if (colorType != null && ZWaveColorType.getColorType(Integer.parseInt(colorType)) != colorEvent.getColorType()) {
			return;
		}
		
		State state = converter.convertFromValueToState(event.getValue());
		this.getEventPublisher().postUpdate(item.getName(), state);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void receiveCommand(Item item, Command command, ZWaveNode node,
			ZWaveColorCommandClass commandClass, int endpointId, Map<String,String> arguments) {
		ZWaveCommandConverter<?,?> converter = this.getCommandConverter(command.getClass());
		
		if (converter == null) {
			logger.warn("NODE {}: No converter found for item = {}, endpoint = {}, ignoring command.", node.getNodeId(), item.getName(), endpointId);
			return;
		}
		
		String colorType = arguments.get("color_type");

		SerialMessage serialMessage = commandClass.setValueMessage(Integer.parseInt(colorType), (Integer)converter.convertFromCommandToValue(item, command));
		if (serialMessage == null) {
			logger.warn("NODE {}: Generating message failed for command class = {}, endpoint = {}", node.getNodeId(), commandClass.getCommandClass().getLabel(), endpointId);
			return;
		}
		
		this.getController().sendData(serialMessage);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int getRefreshInterval() {
		return REFRESH_INTERVAL;
	}
}
