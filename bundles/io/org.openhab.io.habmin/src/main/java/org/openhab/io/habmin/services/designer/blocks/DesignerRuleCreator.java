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
import java.util.ArrayList;
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

	List<Trigger> triggerList = new ArrayList<Trigger>();
	List<String> importList = new ArrayList<String>();

	int cronTime = 0;
	final static String EOL = "\r\n";

	public static final String PATH_RULES = "configurations/rules";
	abstract String processBlock(int level, DesignerBlockBean block);

	String callBlock(int level, DesignerBlockBean block) {
		if(block == null) {
			logger.error("Block is null!");
			return null;
		}

		DesignerRuleCreator processor = getBlockProcessor(block.type);
		if(processor == null) {
			logger.error("Error finding processor for block type '{}'.", block.type);
			return EOL + "*** Unknown Block \"" + block.type + "\"" + EOL;
		}
		
		// Process the block
		String blockString = processor.processBlock(level + 1, block);
		if(blockString == null)
			return null;
		
		// Get any triggers identified in this block
		for(Trigger trigger : processor.getTriggerList()) {
			addTrigger(trigger.item, trigger.type);
		}
		
		// Get any imports identified in this block
		for(String s : processor.getImportList()) {
			addImport(s);
		}
		
		// Get the lowest cron time
		setCron(processor.getCron());
		
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

	List<Trigger> getTriggerList() {
		return triggerList;
	}

	void addTrigger(String item, TriggerType type) {
		// Check if this trigger already exists
		for(Trigger trigger : triggerList) {
			if(!trigger.item.equalsIgnoreCase(item))
				continue;
			if(trigger.type == TriggerType.COMMAND && type == TriggerType.COMMAND)
				return;
			// This trigger already exists - if either are UPDATED, then set to UPDATED
			if(trigger.type == TriggerType.UPDATED || type == TriggerType.UPDATED)
				trigger.type = TriggerType.UPDATED;
			return;
		}
		Trigger trigger = new Trigger();
		trigger.item = item;
		trigger.type = type;

		triggerList.add(trigger);
	}
	
	List<String> getImportList() {
		return importList;
	}
	
	void addImport(String newImport) {
		importList.add(newImport);
	}

	void setCron(int time) {
		if(time == 0)
			return;
		if(cronTime == 0)
			cronTime = time;
		if(time < cronTime)
			cronTime = time;
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

		DesignerRuleCreator processor = getBlockProcessor(rootBlock.type);
		if (processor == null) {
			logger.error("Error finding processor for ROOT block type '{}'.", rootBlock.type);
			return null;
		}

		// Process the block
		String ruleString = processor.callBlock(0, rootBlock);
		if (ruleString == null)
			return null;

		// First delete the existing file
		// Since we'll use the rule name as part of the filename
		// and this can change (id is constant) we need to perform
		// a wildcard delete!
		String fWildcard = "\\[" + id + "\\]_.*\\.txt";
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
		String fileName = PATH_RULES + "/[" + id + "]_" + fileRule + ".txt";
		logger.debug(ruleString);
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"));

			// Write a warning!
			out.write("// This rule file is autogenerated by HABmin." + EOL);
			out.write("// Any changes made manually to this file will be overwritten next time HABmin rules are saved." + EOL);
			out.write(EOL);
			out.write(ruleString);

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
			blockMap.put("logic_negate", LogicNegateBlock.class);
			blockMap.put("math_arithmetic", MathArithmeticBlock.class);
			blockMap.put("math_number", MathNumberBlock.class);
			blockMap.put("math_round", MathRoundBlock.class);
			blockMap.put("math_constrain", MathConstrainBlock.class);
			blockMap.put("variables_get", VariableGetBlock.class);
			blockMap.put("variables_set", VariableSetBlock.class);
			blockMap.put("openhab_rule", OpenhabRuleBlock.class);
			blockMap.put("openhab_persistence_get", OpenhabPersistenceGetBlock.class);
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

	enum CronType {
		STARTED(0, "System started"),
		SHUTDOWN(0, "System shutdown"),
		MIDNIGHT(0, "Time is midnight"), 
		MIDDAY(0, "Time is midday"),
		CRON5SECONDS(5, "Time cron \"*/5 * * * * ?\""),
		CRON6SECONDS(6, "Time cron \"*/6 * * * * ?\""),
		CRON7SECONDS(7, "Time cron \"*/7 * * * * ?\""),
		CRON8SECONDS(8, "Time cron \"*/8 * * * * ?\""),
		CRON9SECONDS(9, "Time cron \"*/9 * * * * ?\""),
		CRON10SECONDS(10, "Time cron \"*/10 * * * * ?\""),
		CRON11SECONDS(11, "Time cron \"*/11 * * * * ?\""),
		CRON12SECONDS(12, "Time cron \"*/12 * * * * ?\""),
		CRON13SECONDS(13, "Time cron \"*/13 * * * * ?\""),
		CRON14SECONDS(14, "Time cron \"*/14 * * * * ?\""),
		CRON15SECONDS(15, "Time cron \"*/15 * * * * ?\""),
		CRON20SECONDS(20, "Time cron \"*/20 * * * * ?\""),
		CRON30SECONDS(30, "Time cron \"*/30 * * * * ?\""),
		CRON1MINUTE(60, "Time cron \"0 * * * * ?\""),
		CRON2MINUTE(120, "Time cron \"0 */2 * * * ?\""),
		CRON3MINUTE(180, "Time cron \"0 */3 * * * ?\""),
		CRON4MINUTE(240, "Time cron \"0 */4 * * * ?\""),
		CRON5MINUTE(300, "Time cron \"0 */5 * * * ?\""),
		CRON6MINUTE(360, "Time cron \"0 */6 * * * ?\""),
		CRON7MINUTE(420, "Time cron \"0 */7 * * * ?\""),
		CRON8MINUTE(480, "Time cron \"0 */8 * * * ?\""),
		CRON9MINUTE(540, "Time cron \"0 */9 * * * ?\""),
		CRON10MINUTE(600, "Time cron \"0 */10 * * * ?\""),
		CRON15MINUTE(900, "Time cron \"0 */15 * * * ?\""),
		CRON20MINUTE(1200, "Time cron \"0 */20 * * * ?\""),
		CRON30MINUTE(1800, "Time cron \"0 */30 * * * ?\""),
		CRON1HOUR(3600, "Time cron \"0 0 * * * ?\""),
		CRON2HOUR(7200, "Time cron \"0 0 */2 * * ?\""),
		CRON3HOUR(10800, "Time cron \"0 0 */3 * * ?\""),
		CRON4HOUR(14400, "Time cron \"0 0 */4 * * ?\""),
		CRON6HOUR(21600, "Time cron \"0 0 */6 * * ?\""),
		CRON8HOUR(28800, "Time cron \"0 0 */8 * * ?\""),
		CRON12HOUR(43200, "Time cron \"0 0 */12 * * ?\""),
		CRON1DAY(86400, "Time cron \"0 0 0 * * ?\""),
		CRONFOREVER(Integer.MAX_VALUE, "Time cron \"0 0 0 * * ?\"");

		private int period;
		private String value;

		private CronType(int period, String value) {
			this.period = period;
			this.value = value;
		}

		// Find the value closest to, but below, the requested period
		public static CronType fromPeriod(int period) {
			// Don't allow anything faster than 5 seconds!
			CronType lowest = null;
			for (CronType c : CronType.values()) {
				// Ignore non-cron statements
				if (c.period == 0)
					continue;
				if (lowest == null)
					lowest = c;
				if (period > lowest.period && c.period <= period) {
					lowest = c;
				}
			}
			return lowest;
		}

		public static CronType fromString(String text) {
			if (text != null) {
				for (CronType c : CronType.values()) {
					if (text.equalsIgnoreCase(c.name())) {
						return c;
					}
				}
			}
			return null;
		}

		// TODO: Maybe we should randomise the seconds, so not all rules trigger at exactly the same time!?!
		public String toString() {
			return this.value;
		}
	}
}
