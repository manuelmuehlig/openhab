/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.habmin.services.designer.blocks;

import org.openhab.io.habmin.services.designer.DesignerBlockBean;
import org.openhab.io.habmin.services.designer.DesignerChildBean;
import org.openhab.io.habmin.services.designer.DesignerFieldBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Chris Jackson
 * @since 1.5.0
 * 
 */
public class OpenhabPersistenceGetBlock extends DesignerRuleCreator {
	private static final Logger logger = LoggerFactory.getLogger(OpenhabPersistenceGetBlock.class);

	String processBlock(int level, DesignerBlockBean block) {

		//addImport("import org.openhab.core.persistence.*");
	
		String blockString = new String();
		DesignerChildBean child;

		DesignerFieldBean varField = findField(block.fields, "VAR");
		if (varField == null) {
			logger.error("PERSISTENCE GET contains no VAR");
			return null;
		}
		
		child = findChild(block.children, "DAYS");
		if (child == null) {
			logger.error("PERSISTENCE GET contains no DAYS");
			return null;
		}
		String days = callBlock(level, child.block);

		child = findChild(block.children, "HOURS");
		if (child == null) {
			logger.error("PERSISTENCE GET contains no HOURS");
			return null;
		}
		String hours = callBlock(level, child.block);

		child = findChild(block.children, "MINUTES");
		if (child == null) {
			logger.error("PERSISTENCE GET contains no MINUTES");
			return null;
		}
		String minutes = callBlock(level, child.block);

		child = findChild(block.children, "SECONDS");
		if (child == null) {
			logger.error("PERSISTENCE GET contains no SECONDS");
			return null;
		}
		String seconds = callBlock(level, child.block);

		DesignerFieldBean typeField = findField(block.fields, "TYPE");
		if(typeField == null) {
			logger.error("PERSISTENCE GET contains no field TYPE");
			return null;
		}
		Operators type = Operators.valueOf(typeField.value.toUpperCase());
		if(type == null) {
			logger.error("PERSISTENCE GET contains invalid field OP ({})", typeField.name.toUpperCase());
			return null;
		}
		
		int timeSeconds = 0;
		String timer = null;
		// Firstly see if just a single parameter is set
		if(Integer.parseInt(days) != 0 
				&& Integer.parseInt(hours) == 0 && Integer.parseInt(minutes) == 0 && Integer.parseInt(seconds) == 0) {
			timer = "minusDays(" + Integer.parseInt(days) + ")";
			timeSeconds = Integer.parseInt(days) * 86400;
		}
		else if(Integer.parseInt(hours) != 0 
				&& Integer.parseInt(days) == 0 && Integer.parseInt(minutes) == 0 && Integer.parseInt(seconds) == 0) {
			timer = "minusHours(" + Integer.parseInt(hours) + ")";
			timeSeconds = Integer.parseInt(hours) * 3600;
		}
		else if(Integer.parseInt(minutes) != 0 
				&& Integer.parseInt(days) == 0 && Integer.parseInt(hours) == 0 && Integer.parseInt(seconds) == 0) {
			timer = "minusMinutes(" + Integer.parseInt(minutes) + ")";
			timeSeconds = Integer.parseInt(minutes) * 60;
		}
		else if(Integer.parseInt(seconds) != 0 
				&& Integer.parseInt(days) == 0 && Integer.parseInt(hours) == 0 && Integer.parseInt(minutes) == 0) {
			timer = "minusSeconds(" + Integer.parseInt(seconds) + ")";
			timeSeconds = Integer.parseInt(seconds);
		}
		else {
			timeSeconds = (Integer.parseInt(days) * 86400 + 
					Integer.parseInt(hours) * 3600 + Integer.parseInt(minutes) * 60 + Integer.parseInt(seconds));
			timer = "minus(" + timeSeconds * 1000 + ")";
		}

		// Add triggers
		// We simply add a timer - 500th of the persistence period seems like a good place to start (?)
		setCron(Math.max(5,timeSeconds / 500));

		// TODO - lots!
		// TODO: resolve type
		String dataType = "DecimalType";

		// Generate the rule string
		blockString = varField.value + "." + type.toString() + "(now." + timer + "" + ").state as " + dataType;
		return blockString;
	}
	
	enum Operators {
		STATE("historicState"),
		CHANGED("changedSince"),
		UPDATED("updatedSince"),
		AVERAGE("averageSince"),
		MINIMUM("minimumSince"),
		MAXIMUM("maximumSince");
		
		private String value;

		private Operators(String value) {
			this.value = value;
		}

		public static Operators fromString(String text) {
			if (text != null) {
				for (Operators c : Operators.values()) {
					if (text.equalsIgnoreCase(c.name())) {
						return c;
					}
				}
			}
			return null;
		}

		public String toString() {
			return this.value;
		}
	}
}
