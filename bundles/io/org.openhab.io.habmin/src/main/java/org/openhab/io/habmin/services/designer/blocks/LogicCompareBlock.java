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
public class LogicCompareBlock extends DesignerRuleCreator {
	private static final Logger logger = LoggerFactory.getLogger(LogicCompareBlock.class);

	String processBlock(int level, DesignerBlockBean block) {
		String blockString = new String();
		DesignerChildBean child;

		child = findChild(block.children, "A");
		if (child == null) {
			logger.error("LOGIC COMPARE contains no A");
			return null;
		}
		String blockA = callBlock(level, child.block);

		child = findChild(block.children, "B");
		if (child == null) {
			logger.error("LOGIC COMPARE contains no B");
			return null;
		}
		String blockB = callBlock(level, child.block);

		DesignerFieldBean operatorField = findField(block.fields, "OP");
		if(operatorField == null) {
			logger.error("LOGIC COMPARE contains no field OP");
			return null;
		}
		Operators op = Operators.valueOf(operatorField.value.toUpperCase());
		if(op == null) {
			logger.error("LOGIC COMPARE contains invalid field OP ({})", operatorField.name.toUpperCase());
			return null;
		}
		
		switch(op) {
		case GT:
			break;
		default:
			logger.error("LOGIC COMPARE contains unprocessed field OP ({})", operatorField.name.toUpperCase());
			break;
		}

		blockString = "(" + blockA + " " + op.toString() + " " + blockB + ")";
		return blockString;
	}

	enum Operators {
		GT(">"), LT("<"), EQ("==");

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