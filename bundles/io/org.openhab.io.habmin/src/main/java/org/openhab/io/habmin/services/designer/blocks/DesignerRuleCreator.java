/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.habmin.services.designer.blocks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;

import org.openhab.io.habmin.services.designer.DesignerBlockBean;
import org.openhab.io.habmin.services.designer.DesignerChildBean;
import org.openhab.io.habmin.services.designer.DesignerFieldBean;
import org.openhab.io.habmin.services.designer.DesignerMutationBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class acts as a block factory and provides most of the core functionality.
 * @author Chris Jackson
 * @since 1.5.0
 * 
 */
public abstract class DesignerRuleCreator {
	private static final Logger logger = LoggerFactory.getLogger(DesignerRuleCreator.class);

	final static String EOL = "\r\n";

	public static final String PATH_RULES = "configurations/rules";
	abstract String processBlock(RuleContext ruleContext, DesignerBlockBean block);

	String callBlock(RuleContext context, DesignerBlockBean block) {
		if(block == null) {
			logger.error("Block is null!");
			return null;
		}

		// Get the block processor
		DesignerRuleCreator processor = getBlockProcessor(block.type);
		if(processor == null) {
			logger.error("Error finding processor for block type '{}'.", block.type);
			return EOL + "*** Unknown Block \"" + block.type + "\"" + EOL;
		}

		// Process the block
		String blockString = processor.processBlock(context, block);
		if(blockString == null)
			return null;
		
		if(block.next != null) {
			blockString += EOL;
			blockString += callBlock(context, block.next);
		}

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

	public static String makeRule(int id, String name, DesignerBlockBean rootBlock) {
		// Check we're sane!
		if (rootBlock.fields == null) {
			logger.error("Root block doesn't contain any fields.");
			return null;
		}
		if (!rootBlock.type.equalsIgnoreCase("openhab_rule")) {
			logger.error("Root block type is not an openhab rule.");
			return null;
		}
		if (rootBlock.children == null) {
			logger.error("Root block has no children.");
			return null;
		}
		if (rootBlock.children.size() == 0) {
			logger.error("Root block has no children.");
			return null;
		}
		if(name == null) {
			logger.error("Rule has no name.");
			return null;
		}

		// Trim any whitespace
		name = name.trim();

		RuleContext context = new RuleContext();

		DesignerRuleCreator processor = getBlockProcessor(rootBlock.type);
		if (processor == null) {
			logger.error("Error finding processor for ROOT block type '{}'.", rootBlock.type);
			return null;
		}

		// Process the block
		String ruleString = processor.callBlock(context, rootBlock);
		if (ruleString == null)
			return null;

		// First delete the existing file
		// Since we'll use the rule name as part of the filename
		// and this can change (id is constant) we need to perform
		// a wildcard delete!
		String fWildcard = "\\(" + id + "\\)_.*\\.txt";
		final File folder = new File(PATH_RULES);
		final File[] allFiles = folder.listFiles();
		if(allFiles != null) {
			for (File file : allFiles) {
				if (!file.getName().matches(fWildcard))
					continue;
				if (!file.delete()) {
					logger.error("Can't remove " + file.getAbsolutePath());
				}
			}
		}

		String fileRule = name.toLowerCase().replaceAll("[^a-z0-9.-]", "_");
		String fileName = PATH_RULES + "/(" + id + ")_" + fileRule + ".txt";
		logger.debug(ruleString);
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"));

			// Write a warning!
			out.write("// This rule file is autogenerated by HABmin." + EOL);
			out.write("// Any changes made manually to this file will be overwritten next time HABmin rules are saved." + EOL);
			out.write(EOL);

			if(context.getImportList().size() != 0) {
				out.write("// Imports" + EOL);
				for(String i : context.getImportList()) {
					out.write("import " + i + EOL);
				}
				out.write(EOL);
			}

			if(context.getImportList().size() != 0) {
				out.write("// Globals" + EOL);
				for(String i : context.getGlobalList()) {
					out.write(i + EOL);
				}
				out.write(EOL);
			}

			out.write(ruleString);
			out.write(EOL);
			out.close();
		} catch (IOException e) {
			logger.error("Error writing habmin rule file :", e);
		}
		return ruleString;
	}

	private static HashMap<String, Class<? extends DesignerRuleCreator>> blockMap = null;

	public static DesignerRuleCreator getBlockProcessor(String type) {
		if (blockMap == null) {
			blockMap = new HashMap<String, Class<? extends DesignerRuleCreator>>();
			blockMap.put("controls_if", ControlIfBlock.class);
			blockMap.put("logic_operation", LogicOperationBlock.class);
			blockMap.put("logic_compare", LogicCompareBlock.class);
			blockMap.put("logic_boolean", LogicBooleanBlock.class);
			blockMap.put("logic_negate", LogicNegateBlock.class);
			blockMap.put("math_arithmetic", MathArithmeticBlock.class);
			blockMap.put("math_number", MathNumberBlock.class);
			blockMap.put("math_round", MathRoundBlock.class);
			blockMap.put("math_constrain", MathConstrainBlock.class);
			blockMap.put("variables_get", VariableGetBlock.class);
			blockMap.put("variables_set", VariableSetBlock.class);
			blockMap.put("openhab_constantget", OpenhabConstantGetBlock.class);
			blockMap.put("openhab_constantset", OpenhabConstantSetBlock.class);
			blockMap.put("openhab_itemget", OpenhabItemGetBlock.class);
			blockMap.put("openhab_itemset", OpenhabItemSetBlock.class);
			blockMap.put("openhab_itemcmd", OpenhabItemCmdBlock.class);
			blockMap.put("openhab_rule", OpenhabRuleBlock.class);
			blockMap.put("openhab_iftimer", OpenhabIfTimerBlock.class);
			blockMap.put("openhab_persistence_get", OpenhabPersistenceGetBlock.class);
			blockMap.put("openhab_state_onoff", OpenhabStateOnOffBlock.class);
			blockMap.put("openhab_state_openclosed", OpenhabStateOpenClosedBlock.class);
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
}
