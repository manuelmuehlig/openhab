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
public class OpenhabRuleBlock extends DesignerRuleCreator {
	private static final Logger logger = LoggerFactory.getLogger(OpenhabRuleBlock.class);

	String processBlock(int level, DesignerBlockBean block) {
		String blockString = new String();
		String ruleString = new String();

		// Loop through the fields to find the rule name
		DesignerFieldBean nameField = findField(block.fields, "NAME");
		if(nameField == null) {
			logger.error("ROOT block must have a name.");
			return null;
		}

		// Process the children
		for(DesignerChildBean child : block.children) {
			String response = callBlock(level, child.block);
			if(response == null)
				return null;
			ruleString += response;
		}
		
		//
		// Write the rule
		
		// Add a comment if there is one
		if (block.comment != null) {
			String[] comments = block.comment.text.split("\\r?\\n");
			for (String comment : comments)
				blockString += "// " + comment + "\r\n";
		}

		blockString += "rule \"" + nameField.value + "\"\r\n";
		blockString += "when\r\n";
		int triggerTotal = getTriggerList().size();
		int triggerCnt = 0;
		for (Trigger trigger : getTriggerList()) {
			triggerCnt++;
			blockString += "    Item " + trigger.item + " " + trigger.type.toString();
			switch(trigger.type) {
			case CHANGED:
				if(trigger.value1 != null)
					blockString += " from " + trigger.value1;
				if(trigger.value2 != null)
					blockString += " to " + trigger.value2;
				break;
			case UPDATED:
				if(trigger.value1 != null)
					blockString += " " + trigger.value1;
				break;
			case COMMAND:
				if(trigger.value1 != null)
					blockString += " " + trigger.value1;
				break;
			}
			if(triggerCnt < triggerTotal)
				blockString += " or";
			blockString += EOL;
		}
		blockString += "then\r\n";
		blockString += ruleString;
		
		// for (String line : rule.action) {
		// ruleString += "  " + resolveVariable(line, variables) + "\r\n";
		// }

		
		// Create a new copy of the variables so we can add the ItemName without
		// messing with the rules variables
		// List<RuleVariableBean> variables = new
		// ArrayList<RuleVariableBean>(ruleVariables);
		// RuleVariableBean thisItem = new RuleVariableBean();
		// thisItem.name = "ItemName";
		// thisItem.value = itemName;
		// variables.add(thisItem);
		
		blockString += "end" + EOL;

		return blockString;
	}
}