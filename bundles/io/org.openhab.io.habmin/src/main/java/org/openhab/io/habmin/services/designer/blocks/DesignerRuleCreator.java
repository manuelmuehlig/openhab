/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.habmin.services.designer.blocks;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.openhab.io.habmin.services.designer.DesignerBlockBean;
import org.openhab.io.habmin.services.designer.DesignerChildBean;
import org.openhab.io.habmin.services.designer.DesignerFieldBean;
import org.openhab.io.habmin.services.designer.DesignerMutationBean;
import org.openhab.io.habmin.services.designer.blocks.LogicOperationBlock.Operators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Chris Jackson
 * @since 1.5.0
 * 
 */
public abstract class DesignerRuleCreator {
	private static final Logger logger = LoggerFactory.getLogger(DesignerRuleCreator.class);

	List<Trigger> triggerList = new ArrayList<Trigger>();
//	String blockString = new String();
	int cronTime = 0;
	final String EOL = "\r\n";

	abstract String processBlock(int level, DesignerBlockBean block);

	String callBlock(int level, DesignerBlockBean block) {
		if(block == null) {
			logger.error("Block is null!");
			return null;
		}

		DesignerRuleCreator processor = getBlockProcessor(block.type);
		if(processor == null) {
			logger.error("Error finding processor for block type '{}'.", block.type);
			return null;
		}
		
		// Process the block
		String blockString = processor.processBlock(level + 1, block);
		if(blockString == null)
			return null;
		
		// Get any triggers identified in this block
		triggerList.addAll(processor.getTriggerList());
		
		// And we're done
		return blockString;
	}

	String startLine(int level) {
		String line = new String();
		for(int c = 0; c < level; c++) {
			line += "  ";
		}
		
		return line;
	};
	
//	String getString() {
//		return blockString;
//	}
	
	List<Trigger> getTriggerList() {
		return triggerList;
	}
	
	void addTrigger(String item, TriggerType type) {
		Trigger trigger = new Trigger();
		trigger.item = item;
		trigger.type = type;

		triggerList.add(trigger);
	}
	
	int getCron() {
		return cronTime;
	}

	DesignerChildBean findChild(List<DesignerChildBean>children, String name) {
		if(children == null)
			return null;
		for(DesignerChildBean child : children) {
			if(child.name == null) {
				continue;
			}
			if(child.name.equalsIgnoreCase(name))
				return child;
		}
		return null;
	}
	
	DesignerMutationBean findMutation(List<DesignerMutationBean>mutations, String name) {
		if(mutations == null)
			return null;
		for(DesignerMutationBean mutation : mutations) {
			if(mutation.name == null) {
				continue;
			}
			if(mutation.name.equalsIgnoreCase(name))
				return mutation;
		}
		return null;
	}
	
	DesignerFieldBean findField(List<DesignerFieldBean>fields, String name) {
		if(fields == null)
			return null;
		for(DesignerFieldBean field : fields) {
			if(field.name == null) {
				continue;
			}
			if(field.name.equalsIgnoreCase(name))
				return field;
		}
		return null;
	}

	public static String makeRule(DesignerBlockBean rootBlock) {
		// Check we're sane!
		if (rootBlock.fields == null) {
			logger.error("Root block doesn't contain any fields.");
			return null;
		}
		if (!rootBlock.type.equalsIgnoreCase("openhab_rule")) {
			logger.error("Root block type is not an openhab rule.");
			return null;
		}
		if(rootBlock.children == null) {
			logger.error("Root block has no children.");
			return null;
		}
		if(rootBlock.children.size() == 0) {
			logger.error("Root block has no children.");
			return null;
		}

		DesignerRuleCreator processor = getBlockProcessor(rootBlock.type);
		if(processor == null) {
			logger.error("Error finding processor for ROOT block type '{}'.", rootBlock.type);
			return null;
		}
		
		// Process the block
		String ruleString = processor.callBlock(0, rootBlock);
		if(ruleString == null)
			return null;

		logger.debug(ruleString);
		return ruleString;
	}

	private static HashMap<String, Class<? extends DesignerRuleCreator>> blockMap = null;

	public static DesignerRuleCreator getBlockProcessor(String type) {
		if (blockMap == null) {
			blockMap = new HashMap<String, Class<? extends DesignerRuleCreator>>();
			blockMap.put("openhab_rule", OpenhabRuleBlock.class);
			blockMap.put("controls_if", ControlIfBlock.class);
			blockMap.put("logic_operation", LogicOperationBlock.class);
			blockMap.put("logic_compare", LogicCompareBlock.class);
			blockMap.put("math_number", MathNumberBlock.class);
			blockMap.put("variables_get", VariableGetBlock.class);
			blockMap.put("variables_set", VariableSetBlock.class);
		}

		if(blockMap.get(type) == null) {
			return null;
		}

		Constructor<? extends DesignerRuleCreator> constructor;
		try {
			constructor = blockMap.get(type).getConstructor();
			return constructor.newInstance();
		} catch (NoSuchMethodException e) {
			logger.error("Command processor error: {}", e);
		} catch (InvocationTargetException e) {
			logger.error("Command processor error: {}", e);
		} catch (InstantiationException e) {
			logger.error("Command processor error: {}", e);
		} catch (IllegalAccessException e) {
			logger.error("Command processor error: {}", e);
		} catch (SecurityException e) {
			logger.error("Command processor error: {}", e);
		} catch (IllegalArgumentException e) {
			logger.error("Command processor error: {}", e);
		}

		return null;
	}
	
	class Trigger {
		String item;
		TriggerType type;
		String value1;
		String value2;
	}
	
	enum TriggerType {
		CHANGED("changed"), UPDATED("received update"), COMMAND("received command");

			private String value;

			private TriggerType(String value) {
				this.value = value;
			}

			public static TriggerType fromString(String text) {
				if (text != null) {
					for (TriggerType c : TriggerType.values()) {
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