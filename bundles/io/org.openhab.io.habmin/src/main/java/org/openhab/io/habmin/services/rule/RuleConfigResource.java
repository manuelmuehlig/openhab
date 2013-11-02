/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */package org.openhab.io.habmin.services.rule;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.openhab.io.habmin.HABminApplication;
import org.openhab.io.habmin.internal.resources.MediaTypeHelper;
import org.openhab.io.habmin.repository.HabminConfigDatabase;
import org.openhab.io.habmin.repository.HabminItemBean;
import org.openhab.io.habmin.services.item.ItemConfigBean;
import org.openhab.io.habmin.services.item.ItemModelHelper;
import org.openhab.model.core.ModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.json.JSONWithPadding;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

/**
 * <p>
 * This class acts as a REST resource for history data and provides different
 * methods to interact with the rule system
 * 
 * <p>
 * The typical content types are plain text for status values and XML or JSON(P)
 * for more complex data structures
 * </p>
 * 
 * <p>
 * This resource is registered with the Jersey servlet.
 * </p>
 * 
 * @author Chris Jackson
 * @since 1.4.0
 */
@Path(RuleConfigResource.PATH_RULES)
public class RuleConfigResource {

	private static final Logger logger = LoggerFactory.getLogger(RuleConfigResource.class);

	private final String HABMIN_RULES = "habmin-autorules";
	private final String HABMIN_RULES_LIBRARY = "rules_library.xml";

	/** The URI path to this resource */
	public static final String PATH_RULES = "config/rules";

	@Context
	UriInfo uriInfo;

	@GET
	@Path("/library/list")
	@Produces({ MediaType.WILDCARD })
	public Response httpGetTemplateList(@Context HttpHeaders headers, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getRuleTemplateList(null), callback) : getRuleTemplateList(null);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@GET
	@Path("/list")
	@Produces({ MediaType.WILDCARD })
	public Response httpGetList(@Context HttpHeaders headers, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getRuleList(), callback) : getRuleList();
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@GET
	@Path("/item/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response httpGetItem(@Context HttpHeaders headers, @QueryParam("type") String type,
			@PathParam("itemname") String itemName,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getRuleTemplateItemList(itemName), callback) : getRuleTemplateItemList(itemName);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@PUT
	@Path("/item/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response httpPutItem(@Context HttpHeaders headers, @QueryParam("type") String type,
			@PathParam("itemname") String itemName,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback, RuleListBean ruleData) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					putItemRules(itemName, ruleData), callback) : putItemRules(itemName, ruleData);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@POST
	@Path("/item/{itemname: [a-zA-Z_0-9]*}/{rulename: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response httpPostItemRule(@Context HttpHeaders headers, @QueryParam("type") String type,
			@PathParam("itemname") String itemName, @PathParam("rulename") String ruleName,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback, RuleBean ruleData) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					postRule(itemName, ruleName, ruleData), callback) : postRule(itemName, ruleName, ruleData);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@DELETE
	@Path("/item/{itemname: [a-zA-Z_0-9]*}/{rulename: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response httpDeleteItemRule(@Context HttpHeaders headers, @QueryParam("type") String type,
			@PathParam("itemname") String itemName, @PathParam("rulename") String ruleName,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					deleteRule(itemName, ruleName), callback) : deleteRule(itemName, ruleName);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	private RuleListBean getRuleTemplateList(String type) {
		// List<RuleTemplateBean> beanList = new ArrayList<RuleTemplateBean>();

		FileInputStream fin;
		try {
			fin = new FileInputStream("webapps/habmin/openhab/" + HABMIN_RULES_LIBRARY);

			// for (Map.Entry<String, PersistenceService> service :
			// RESTApplication.getPersistenceServices().entrySet()) {
			// RuleTemplateBean ruleBean = new RuleTemplateBean();

			XStream xstream = new XStream(new StaxDriver());
			xstream.alias("rules", RuleListBean.class);
			xstream.processAnnotations(RuleListBean.class);

			RuleListBean newRules = (RuleListBean) xstream.fromXML(fin);

			fin.close();

			return newRules;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Produces a lit of rules that are applicable to the item. Rules are
	 * filtered out based on attributes in the rule template file
	 * 
	 * @param itemName
	 *            the item name to return the list of rules
	 * @return returns a list of rules applicable and configured for this item
	 */
	private RuleListBean getRuleTemplateItemList(String itemName) {
		// List<RuleTemplateBean> beanList = new ArrayList<RuleTemplateBean>();

		// Get the item from the itemName
		// This is used so we can get the type, and filter only relevant rules

		FileInputStream fin;
		try {
			fin = new FileInputStream("webapps/habmin/openhab/" + HABMIN_RULES_LIBRARY);

			XStream xstream = new XStream(new StaxDriver());
			xstream.alias("rules", RuleListBean.class);
			xstream.processAnnotations(RuleListBean.class);

			RuleListBean newRules = (RuleListBean) xstream.fromXML(fin);
			fin.close();
			
			newRules.item = itemName;

			// Loop through all rules and filter out any that aren't applicable
			// for this item
			for (RuleBean rule : newRules.rule) {
				boolean delete = false;
				if (rule.itemType != null) {
					for (String type : rule.itemType) {
						// if(type)
					}
				}
				if (delete == true) {
					// Remove this from the list
					newRules.rule.remove(rule);
				}
			}

			// Loop through the rules and add any relevant config from the item
			// config database
			for (RuleBean rulelist : newRules.rule) {
				RuleBean rule = HabminConfigDatabase.getItemRule(itemName, rulelist.name);
				if (rule != null) {
					for (RuleVariableBean vars : rule.variable) {
						// Correlate the values
						for (RuleVariableBean x : rulelist.variable) {
							if (x.name.equals(vars.name)) {
								x.value = vars.value;
								break;
							}
						}

						if (vars.name.equals("DerivedItem")) {
							rulelist.linkeditem = vars.value;
						}
					}
				}
			}

			return newRules;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	private RuleBean getRuleTemplate(String ruleName) {
		// Get the item from the itemName
		// This is used so we can get the type, and filter only relevant rules

		FileInputStream fin;
		try {
			fin = new FileInputStream("webapps/habmin/openhab/" + HABMIN_RULES_LIBRARY);

			XStream xstream = new XStream(new StaxDriver());
			xstream.alias("rules", RuleListBean.class);
			xstream.processAnnotations(RuleListBean.class);

			RuleListBean newRules = (RuleListBean) xstream.fromXML(fin);
			fin.close();

			// Loop through the rules and find the one we're looking for
			for (RuleBean rule : newRules.rule) {
				if (rule.name.equals(ruleName))
					return rule;
			}

			return null;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	private String resolveVariable(String line, List<RuleVariableBean> variables) {
		for (RuleVariableBean variable : variables) {
			line = line.replace("%%" + variable.name + "%%", variable.value);
		}

		return line;
	}

	private String writeRule(String itemName, List<RuleVariableBean> ruleVariables, RuleBean rule) {
		String ruleString = new String();

		// Create a new copy of the variables so we can add the ItemName without
		// messing with the rules variables
		List<RuleVariableBean> variables = new ArrayList<RuleVariableBean>(ruleVariables);
		RuleVariableBean thisItem = new RuleVariableBean();
		thisItem.name = "ItemName";
		thisItem.value = itemName;
		variables.add(thisItem);

		ruleString += "// " + rule.description + "\r\n";

		ruleString += "rule \"" + itemName + ": " + rule.label + "\"\r\n";
		ruleString += "when\r\n";
		for (String line : rule.trigger) {
			ruleString += "  " + resolveVariable(line, variables) + "\r\n";
		}
		ruleString += "then\r\n";
		for (String line : rule.action) {
			ruleString += "  " + resolveVariable(line, variables) + "\r\n";
		}
		ruleString += "end\r\n";

		return ruleString;
	}

	/**
	 * Writes the rules configured with all items to the habmin derived rules
	 * file
	 * 
	 * @return true if successful
	 */
	boolean writeRules() {
		ModelRepository repo = HABminApplication.getModelRepository();
		if (repo == null)
			return false;

		String orgName = "configurations/rules/" + HABMIN_RULES + ".rules";
		String newName = "configurations/rules/" + HABMIN_RULES + ".rules.new";
		String bakName = "configurations/rules/" + HABMIN_RULES + ".rules.bak";

		List<String> importList = new ArrayList<String>();
		List<String> ruleList = new ArrayList<String>();

		// Load the HABmin database so we have all the rules for all items
		try {
			FileWriter fw = null;
			fw = new FileWriter(newName, false);
			BufferedWriter out = new BufferedWriter(fw);

			List<HabminItemBean> items = HabminConfigDatabase.getItems();

			// Loop through the items
			for (HabminItemBean item : items) {
				if (item.rules != null) {
					// And now loop through all the rules for this item
					for (RuleBean rule : item.rules) {
						RuleBean template = getRuleTemplate(rule.name);
						if (template.imports != null) {
							for (String i : template.imports) {
								if (importList.indexOf(i) == -1)
									importList.add(i);
							}
						}

						ruleList.add(writeRule(item.name, rule.variable, template));
					}
				}
			}

			// Write a warning!
			out.write("// This rule file is autogenerated by HABmin.\r\n");
			out.write("// Any changes made manually to this file will be overwritten next time HABmin rules are saved.");
			out.write("\r\n");

			// Loop though all the rules and write each rule
			for (String s : importList)
				out.write("import " + s + "\r\n");
			out.write("\r\n");

			for (String s : ruleList)
				out.write(s + "\r\n");
			out.write("\r\n");

			out.close();
			fw.close();

			// Rename the files.
			File bakFile = new File(bakName);
			File orgFile = new File(orgName);
			File newFile = new File(newName);

			// Delete any existing .bak file
			if (bakFile.exists())
				bakFile.delete();

			// Rename the existing item file to backup
			orgFile.renameTo(bakFile);

			// Rename the new file to the item file
			newFile.renameTo(orgFile);

			// Update the model repository
			InputStream inFile;
			try {
				inFile = new FileInputStream(orgName);
				repo.addOrRefreshModel(HABMIN_RULES, inFile);
			} catch (FileNotFoundException e) {
				logger.error("Error refreshing habmin rule file :", e);
			}
		} catch (IOException e) {
			logger.error("Error writing habmin rule file :", e);
		}

		return true;
	}

	/**
	 * Save a new rule for an item
	 * 
	 * @param itemName
	 *            the item name
	 * @param ruleName
	 *            the name of the rule
	 * @param ruleData
	 *            rule data
	 * @return returns a list of rules applicable and configured for this item
	 */
	private RuleListBean postRule(String itemName, String ruleName, RuleBean ruleData) {
		// Add the rule into the database
		HabminConfigDatabase.updateItemRule(itemName, ruleData);

		// Check if there is an item to create
		for (RuleVariableBean variable : ruleData.variable) {
			if (variable.name.equals("DerivedItem")) {
				// Create a new item
				ItemModelHelper itemHelper = new ItemModelHelper();

				ItemConfigBean item = new ItemConfigBean();
				item.name = variable.value;
				RuleBean template = getRuleTemplate(ruleData.name);
				
				// Set the label here in case we don't find the parent bean later.
				if (template != null)
					item.label = itemName + " " + template.name;

				// Put the new item in the "habmin.items" model file
				item.model = "habmin";

				// Get the parent item so that we know type etc.
				ItemModelHelper modelHelper = new ItemModelHelper();
				ItemConfigBean itemBean = modelHelper.getItemConfigBean(itemName);
				if (itemBean != null) {
					item.label = itemBean.label + " " + template.name;
					item.icon = itemBean.icon;
					item.format = itemBean.format;
					item.units = itemBean.units;
					item.translateRule = itemBean.translateRule;
					item.translateService = itemBean.translateService;
					if (variable.type != null)
						item.type = variable.type;
					else if (itemBean.type.equals("Group"))
						item.type = "Number";
					else
						item.type = itemBean.type;

					// Save
					itemHelper.updateItem(item.name, item, false);
				}
			}
		}

		// Update the rules for openHAB
		writeRules();

		// Return the list of rules for this item
		return getRuleTemplateItemList(itemName);
	}

	/**
	 * Delete a rule from an item (not the rule library)
	 * 
	 * @param itemName
	 *            item to remove the rule from
	 * @param ruleName
	 *            name of the rule to remove
	 * @return list of rules configured for the item
	 */
	private RuleListBean deleteRule(String itemName, String ruleName) {
		HabminConfigDatabase.removeItemRule(itemName, ruleName);

		// Update the rules for openHAB
		writeRules();

		// Return the list of rules for this item
		return getRuleTemplateItemList(itemName);
	}

	/**
	 * Save rules for a particular item. This can't create new rules, so it is
	 * effectively updating variable values
	 * 
	 * @param itemName
	 * @param ruleData
	 * @return
	 */
	private RuleListBean putItemRules(String itemName, RuleListBean ruleData) {
		// Loop through all the rules in the data
		for (RuleBean rule : ruleData.rule) {
			// Make sure there are variables in this rule
			if (rule.variable == null)
				continue;

			// Get the rule from the config database
			RuleBean bean = HabminConfigDatabase.getItemRule(itemName, rule.name);
			if (bean == null)
				continue;

			// Loop through all the variables in this rule
			for (RuleVariableBean varBean : bean.variable) {
				// Don't allow changing of "Setup" scoped variables
				if (varBean.scope.equalsIgnoreCase("Setup"))
					continue;

				for (RuleVariableBean varIn : rule.variable) {
					if (varIn.name.equals(varBean.name)) {
						// We have a match - update the value
						varBean.value = varIn.value;
					}
				}
			}
		}

		// Write the config database
		HabminConfigDatabase.saveDatabase();

		// Update the rules for openHAB
		writeRules();

		// Return the list of rules for this item
		return getRuleTemplateItemList(itemName);
	}

	private RuleListBean getRuleList() {
		List<HabminItemBean> items = HabminConfigDatabase.getItems();

		FileInputStream fin;
		try {
			fin = new FileInputStream("webapps/habmin/openhab/" + HABMIN_RULES_LIBRARY);

			XStream xstream = new XStream(new StaxDriver());
			xstream.alias("rules", RuleListBean.class);
			xstream.processAnnotations(RuleListBean.class);

			RuleListBean ruleTemplates = (RuleListBean) xstream.fromXML(fin);
			fin.close();

			List<RuleBean> itemRuleList = new ArrayList<RuleBean>();

			// Loop through the items
			for (HabminItemBean item : items) {
				if (item.rules != null) {
					// And now loop through all the rules for this item
					for (RuleBean rule : item.rules) {
						// And finally find the template for this rule
						for (RuleBean template : ruleTemplates.rule) {
							if (rule.name.equals(template.name)) {
								//
								RuleBean newRule = new RuleBean();
								newRule.item = item.name;
								newRule.label = template.label;
								newRule.name = template.name;
								newRule.description = template.description;

								// Add the new rule to the list
								itemRuleList.add(newRule);
							}
						}

					}
				}
			}

			RuleListBean listBean = new RuleListBean();
			listBean.rule = itemRuleList;

			return listBean;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

}
