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
public class OpenhabIfTimerBlock extends DesignerRuleCreator {
	private static final Logger logger = LoggerFactory.getLogger(OpenhabIfTimerBlock.class);

	String processBlock(int level, DesignerBlockBean block) {
		String blockString = new String();
		String response;
		DesignerChildBean child;
		DesignerFieldBean field;

		String timerID = "timer" + getGlobalId();
		addGlobal("var Timer " + timerID + " = null");

		// Add a comment if there is one
		if (block.comment != null) {
			String[] comments = block.comment.text.split("\\r?\\n");
			for (String comment : comments)
				blockString += "// " + comment + "\r\n";
		}

		// Process the IF...
		child = findChild(block.children, "IF0");
		if (child == null) {
			logger.error("OPENHAB IF TIMER contains no IF0.");
			return null;
		}
		response = callBlock(level, child.block);

		blockString += startLine(level) + "if" + response + " {" + EOL;

		field = findField(block.fields, "PERIOD");
		if (field == null) {
			logger.error("OPENHAB IF TIMER contains no PERIOD.");
			return null;
		}

		Period period = Period.fromString(field.value);
		if (period == null) {
			logger.error("OPENHAB IF TIMER contains invalid PERIOD.");
			return null;
		}

		field = findField(block.fields, "NUM");
		if (field == null) {
			logger.error("OPENHAB IF TIMER contains no NUM.");
			return null;
		}

		// Now add the timer
		blockString += startLine(level + 1) + timerID + " = createTimer(now.plus" + period.toString() + "("
				+ field.value + ")) [|" + EOL;

		// And then the DO...
		child = findChild(block.children, "DO0");
		if (child == null) {
			logger.error("OPENHAB IF TIMER contains no DO0");
			return null;
		}
		blockString += callBlock(level + 1, child.block);

		// Terminate the timer block
		blockString += startLine(level + 1) + "]" + EOL;
		blockString += startLine(level) + "}" + EOL;

		// And the cancel timer part...
		blockString += startLine(level) + "else if(" + timerID + " != null) {" + EOL;
		blockString += startLine(level + 1) + timerID + ".cancel()" + EOL;
		blockString += startLine(level) + "}" + EOL;

		return blockString;
	}

	enum Period {
		SECONDS("Seconds"), MINUTES("Minutes"), HOURS("Hours");

		private String value;

		private Period(String value) {
			this.value = value;
		}

		public static Period fromString(String text) {
			if (text != null) {
				for (Period c : Period.values()) {
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
