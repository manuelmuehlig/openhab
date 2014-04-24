/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.habmin.services.designer.blocks;

import org.openhab.core.items.ItemNotFoundException;
import org.openhab.io.habmin.HABminApplication;
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
public class VariableSetBlock extends DesignerRuleCreator {
	private static final Logger logger = LoggerFactory.getLogger(VariableSetBlock.class);

	String processBlock(int level, DesignerBlockBean block) {
		DesignerFieldBean varField = findField(block.fields, "VAR");
		if (varField == null) {
			logger.error("VARIABLE SET contains no VAR");
			return null;
		}

		DesignerChildBean child = findChild(block.children, "VALUE");
		if (child == null) {
			logger.error("VARIABLE SET contains no VALUE");
			return null;
		}
		String value = callBlock(level, child.block);

		// TODO: Work out what type of item this is.
		// If it's a command, then use 'sendCommand'
		// If it's not, then use 'postUpdate'
		// TODO: Can this be reliable???
		try {
			HABminApplication.getItemUIRegistry().getItem(varField.value);
		} catch (ItemNotFoundException e) {
			// TODO Auto-generated catch block
			return "*** Unknown item: " + varField.value + EOL;
		}

		return startLine(level) + "postUpdate(" + varField.value + ", " + value + ")" + EOL;
	}
}
