/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal.protocol.event;

import org.openhab.binding.zwave.internal.protocol.AssociationGroup;

/**
 * ZWave association group received event.
 * Send from the association members to the binding
 * Note that multiple events can be required to build up the full list.
 * 
 * @author Chris Jackson
 * @since 1.4.0
 */
public class ZWaveAssociationEvent extends ZWaveEvent {

		private int group;
		private AssociationGroup members;
		
		/**
		 * Constructor. Creates a new instance of the ZWaveAssociationEvent
		 * class.
		 * @param nodeId the nodeId of the event. Must be set to the controller node.
		 */
		public ZWaveAssociationEvent(int nodeId, int group) {
			super(nodeId);
			
			this.group = group;
			this.members = new AssociationGroup(nodeId);
		}

		public int getGroup() {
			return group;
		}

		public AssociationGroup getMembers() {
			return members;
		}

//		public int getMemberCnt() {
//			return members.size();
//		}

		public void addMembers(AssociationGroup group) {
			members = group;
		}
	}
