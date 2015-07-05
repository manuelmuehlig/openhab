/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.openhab.binding.zwave.internal.config.OpenHABConfigurationRecord;
import org.openhab.binding.zwave.internal.config.ZWaveDbAssociationGroup;
import org.openhab.binding.zwave.internal.config.ZWaveDbCommandClass;
import org.openhab.binding.zwave.internal.config.ZWaveDbConfigurationListItem;
import org.openhab.binding.zwave.internal.config.ZWaveDbConfigurationParameter;
import org.openhab.binding.zwave.internal.config.ZWaveDbManufacturer;
import org.openhab.binding.zwave.internal.config.ZWaveDbProduct;
import org.openhab.binding.zwave.internal.config.ZWaveDbProduct.ZWaveDbConfigFile;
import org.openhab.binding.zwave.internal.config.ZWaveDbProductReference;
import org.openhab.binding.zwave.internal.config.ZWaveProductDatabase;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass.CommandClass;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;


/**
 * Extension of the default OSGi bundle activator.
 * 
 * @author Victor Belov
 * @since 1.3.0
 */
public final class ZWaveActivator implements BundleActivator {

	private static Logger logger = LoggerFactory.getLogger(ZWaveActivator.class); 
	
	private static BundleContext context;
	
	/**
	 * Called whenever the OSGi framework starts our bundle
	 * @param bc the bundle's execution context within the framework
	 */
	public void start(BundleContext bc) throws Exception {
		context = bc;
		logger.debug("Z-Wave binding started. Version {}", ZWaveActivator.getVersion());
		
		
		
		
		
		ZWaveProductDatabase mdatabase = new ZWaveProductDatabase();

		int maxCfg = 0;
		// Loop over the manufacturers
		for (ZWaveDbManufacturer manufacturer : mdatabase.GetManufacturers()) {
			ZWaveProductDatabase database = new ZWaveProductDatabase();
			if (database.FindManufacturer(manufacturer.Id) == false) {
				continue;
			}
			
			if(manufacturer.Id == 0x10f) {
				int x;
				
				x= 1;
			}

			// Loop over the products
			for (ZWaveDbProduct product : database.GetProducts()) {
				
				if(product.ConfigFile == null)
					continue;
				
				for(ZWaveDbConfigFile cfg : product.ConfigFile) {

				int cfgCnt = 0;
				
				String outstring = "";
				
				
				outstring += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" ;
				outstring += "<thing:thing-descriptions bindingId=\"zwave\"" ;
				outstring += "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" ;
				outstring += "xmlns:thing=\"http://eclipse.org/smarthome/schemas/zwave-thing-description/v1.0.0\"" ;
				outstring += "xsi:schemaLocation=\"http://eclipse.org/smarthome/schemas/zwave-thing-description/v1.0.0 http://eclipse.org/smarthome/schemas/zwave-thing-description/v1.0.0\">";

				Version vmin = Version.emptyVersion;
				if(cfg.VersionMin != null) {
					vmin = new Version(cfg.VersionMin);
				}
				Version vmax =  Version.emptyVersion;
				if(cfg.VersionMax != null) {
					vmax = new Version(cfg.VersionMax);
				}
				
				String id = manufacturer.Name + "_" + product.Model + "_" + String.format("%02d", vmin.getMajor())  + "_" + String.format("%03d", vmin.getMinor()) ; 
				outstring += "<thing-type id=\"" + id.replaceAll("\\s+","").toLowerCase() + "\">";
				outstring += "<label>" + database.getLabel(product.Label) + "</label>";
				outstring += "<description><![CDATA[ ]]></description>";

				outstring += "<properties>";
				outstring += "<property name=\"vendor\">" + manufacturer.Name + "</property>";
				outstring += "<property name=\"model\">" + product.Model + "</property>";
				outstring += "<property name=\"versionMin\">" + vmin.getMajor() + "." + vmin.getMinor() + "</property>";
				outstring += "<property name=\"versionMax\">" + vmax.getMajor() + "." + vmax.getMinor() + "</property>";
				outstring += "<property name=\"manufacturerId\">" + String.format("%04X", manufacturer.Id).toUpperCase() + "</property>";
				outstring += "<property name=\"manufacturerRef\">[";
				boolean first = true;
				for(ZWaveDbProductReference ref : product.Reference) {
					if(first == false) {
						outstring += ",";
					}
					outstring += String.format("%04X", ref.Type).toUpperCase() + ":" + String.format("%04X",ref.Id).toUpperCase();
					first = false;
				}
				
				outstring += "]</property>";
				outstring += "</properties>";

				outstring += "<channels>";

				outstring += "<channel id=\"switch0\" typeId=\"dimmer\">";
//				outstring += "<properties>";
				outstring += "<property name=\"endpoint\">0</property>";
				outstring += "<property name=\"command_class\">SWITCH_MULTILEVEL</property>";
				outstring += "<property name=\"set_to_basic\">true</property>";
//				outstring += "</properties>";
				outstring += "</channel>";

				outstring += "</channels>";	
				
				if(product.Model.equals("FGD211")) {
					logger.debug("");
				}
				if(database.FindProduct(manufacturer.Id, product.Reference.get(0).Type, product.Reference.get(0).Id, vmin.toString()) == false) {
					logger.debug("Product not found for {}", product.Model);
				}
				
				// 
				List<ZWaveDbConfigurationParameter> configList = database.getProductConfigParameters();
				List<ZWaveDbAssociationGroup> groupList = database.getProductAssociationGroups();

				if(configList != null || groupList != null) {
					outstring += "<config-description>";

					if(configList != null) {
						outstring += "<group name=\"configuration\">";
						outstring += "<context>setup</context>";
						outstring += "<label>Configuration Parameters</label>";
						outstring += "<description></description>";
						outstring += "</group>";
					}
					if(groupList != null) {
						outstring += "<group name=\"association\">";
						outstring += "<context>setup</context>";
						outstring += "<label>Association Groups</label>";
						outstring += "<description></description>";
						outstring += "</group>";
					}

					// Loop through the parameters and add to the records...
					if(configList != null) {
						for (ZWaveDbConfigurationParameter parameter : configList) {
							cfgCnt++;
							outstring += "<parameter name=\"config_" + parameter.Index+"\" type=\"integer\"";
							if(parameter.Minimum != null) {
								outstring += " min=\"" + parameter.Minimum + "\"";
							}
							if(parameter.Maximum != null) {
								outstring += " max=\"" + parameter.Maximum + "\"";
							}
							if(parameter.ReadOnly != null) {
								outstring += " readOnly=\"true\"";
							}
							outstring += ">";
							
							outstring += "<groupName>configuration</groupName>";
							outstring += "<label>" + database.getLabel(parameter.Label) + "</label>";
							if(database.getLabel(parameter.Help)!=null && database.getLabel(parameter.Help).startsWith("<![CDATA[") == true) {
								outstring += "<description>" + database.getLabel(parameter.Help)+ "</description>";
							}
							else {
								outstring += "<description><![CDATA[" + database.getLabel(parameter.Help)+ "]]></description>";
							}
							
							if(parameter.Default != null) {
								outstring += "<default>" + parameter.Default + "</default>";
							}
		
							if(parameter.Item != null) {
								outstring += "<options>";
	
								for (ZWaveDbConfigurationListItem item : parameter.Item) {
									outstring += "<option value=\"" + item.Value + "\">" + database.getLabel(item.Label) + "</option>";
								}
								outstring += "</options>";
							}
		
							outstring += "</parameter>";
						}
					}
	
					if (groupList != null) {
						// Loop through the associations and add to the
						// records...
						for (ZWaveDbAssociationGroup group : groupList) {
							cfgCnt++;
							outstring += "<parameter name=\"group_" + group.Index+"\" type=\"integer\"";
							if(group.Maximum > 1) {
								outstring += " multiple=\"true\" multipleLimit=\"" + group.Maximum + "\"";
							}
							outstring += ">";
							outstring += "<groupName>association</groupName>";
							outstring += "<label>" + database.getLabel(group.Label) + "</label>";
							if(database.getLabel(group.Help) != null) {
								outstring += "<description><![CDATA[" + database.getLabel(group.Help) + "]]></description>";
							}
							outstring += "</parameter>";
						}
					}
					
					outstring += "</config-description>";
				}
				
				List<ZWaveDbCommandClass> classList = database.getProductCommandClasses();
				if (classList != null) {
					// First, loop through looking for the wakeup class
					for (ZWaveDbCommandClass dbClass : classList) {
						// needed or not?????
					}

					// Loop through the command classes
					for (ZWaveDbCommandClass dbClass : classList) {
						if(dbClass.add==null&&dbClass.isGetSupported==null&&dbClass.meterCanReset==null&&dbClass.remove==null&&dbClass.meterScale==null&&dbClass.meterType==null) {
							continue;
						}
						outstring += "<property name=\"command_class:" + CommandClass.getCommandClass(dbClass.Id);
						if(dbClass.endpoint != null) {
							outstring += dbClass.endpoint + ":";
						}
						// If we want to remove the class, then remove it!
						if(dbClass.remove != null && dbClass.remove == true) {
							outstring += "REMOVE";
						}
						if(dbClass.add != null && dbClass.add == true) {
							outstring += "ADD";
						}

						if(dbClass.isGetSupported != null && dbClass.isGetSupported == false) {
							outstring += "SUPPORT_GET=false";
						}

						if(dbClass.meterCanReset != null && dbClass.meterCanReset == true) {
							outstring += "METER_CAN_RESET";
						}
						
						if(dbClass.meterType != null) {
							outstring += "METER_TYPE=" + dbClass.meterType;
						}
						if(dbClass.meterScale != null) {
							outstring += "METER_SCALE=" + dbClass.meterScale;
						}
						
						outstring += "</property-group>";
					}
				}
				
				
				outstring += "</thing-type>";
				
				outstring += "<channel-type id=\"dimmer\">";
				outstring += "<item-type>Dimmer</item-type>";
				outstring += "<label>Dimmer</label>";
				outstring += "<description>Set the light level</description>";
				outstring += "<category>Light</category>";
				outstring += "</channel-type>";

				
				if(cfgCnt > maxCfg)
					maxCfg = cfgCnt;
				
				
				outstring += "</thing:thing-descriptions>";

				if(outstring.isEmpty() == false) {
					String name = manufacturer.Name.toLowerCase()+"_"+product.Model.toLowerCase() + "_" + String.format("%02d", vmin.getMajor())  + "_" + String.format("%03d", vmin.getMinor()) +".xml";
					name = "aa-zwave/" + name.toLowerCase().replaceAll("[^a-z0-9.-]", "_");
					final File folder = new File(name);
					try {
						BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(name), "UTF-8"));
	
						out.write(outstring);
						out.close();
					} catch (IOException e) {
						logger.error("Error writing habmin rule file :", e);
					}
				}
				}
			}
		}

		logger.debug("done Max cfg = {}", maxCfg);

		
		
		
		
		
		
		
	}

	/**
	 * Called whenever the OSGi framework stops our bundle
	 * @param bc the bundle's execution context within the framework
	 */
	public void stop(BundleContext bc) throws Exception {
		context = null;
		logger.debug("Z-Wave binding stopped.");
	}
	
	/**
	 * Returns the bundle context of this bundle
	 * @return the bundle context
	 */
	public static BundleContext getContext() {
		return context;
	}

	/**
	 * Returns the current version of the bundle.
	 * @return the current version of the bundle.
	 */
	public static Version getVersion() {
		return context.getBundle().getVersion();
	}

}
