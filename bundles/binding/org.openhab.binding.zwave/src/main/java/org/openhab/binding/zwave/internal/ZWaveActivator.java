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
import org.openhab.binding.zwave.internal.config.ZWaveDbConfigurationListItem;
import org.openhab.binding.zwave.internal.config.ZWaveDbConfigurationParameter;
import org.openhab.binding.zwave.internal.config.ZWaveDbManufacturer;
import org.openhab.binding.zwave.internal.config.ZWaveDbProduct;
import org.openhab.binding.zwave.internal.config.ZWaveProductDatabase;
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
		
		
		
		
		
		ZWaveProductDatabase database = new ZWaveProductDatabase();

		// Loop over the manufacturers
		for (ZWaveDbManufacturer manufacturer : database.GetManufacturers()) {
			if (database.FindManufacturer(manufacturer.Id) == false) {
				break;
			}
			
			if(manufacturer.Id == 0x10f) {
				int x;
				
				x= 1;
			}

			// Loop over the products
			for (ZWaveDbProduct product : database.GetProducts()) {
				
				String outstring = "";
				
				
				outstring += "<zwave:thing-descriptions bindingId=\"zwave\">";

				outstring += "<thing-type id=\"thingTypeID\">";
				outstring += "<label>" + database.getLabel(product.Label) + "</label>";
				outstring += "<description></description>";

				outstring += "<properties>";
				outstring += "<property id=\"vendor\">" + manufacturer.Name + "</property>";
				outstring += "<property id=\"model\">" + product.Model + "</property>";
				outstring += "<property id=\"version\">" + "" + "</property>";
				outstring += "</properties>";

				outstring += "<channels>";

				outstring += "<channel id=\"switch0\" typeId=\"dimmer\">";
				outstring += "<properties>";
				outstring += "<property name=\"endpoint\">0</property>";
				outstring += "<property name=\"command_class\">SWITCH_MULTILEVEL</property>";
				outstring += "<property name=\"set_to_basic\">true</property>";
				outstring += "</properties>";
				outstring += "</channel>";

				outstring += "</channels>";				
				outstring += "</thing-type>";
				
				outstring += "<channel-type id=\"dimmer\">";
				outstring += "<item-type>Dimmer</item-type>";
				outstring += "<label>Dimmer</label>";
				outstring += "<description>Set the light level</description>";
				outstring += "<category>Light</category>";
				outstring += "</channel-type>";
				
				database.FindProduct(manufacturer.Id, product.Reference.get(0).Type, product.Reference.get(0).Id, 255.99);
				
				boolean parms = false;
				List<ZWaveDbConfigurationParameter> configList = database.getProductConfigParameters();
				// Loop through the parameters and add to the records...
				if(configList != null) {
					for (ZWaveDbConfigurationParameter parameter : configList) {
						if(parms == false) {
							outstring += "<config-description>";
							parms = true;
						}
						outstring += "<parameter name=\"" + parameter.Index+"\" type=\"integer\"";
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
						
						outstring += "<label>" + database.getLabel(parameter.Label) + "</label>";
						outstring += "<description>" + database.getLabel(parameter.Help)+ "</description>";
						
						if(parameter.Default != null) {
							outstring += "<default>" + parameter.Default + "</default>";
						}
	
						if(parameter.Item != null) {
							outstring += "<options>";

							for (ZWaveDbConfigurationListItem item : parameter.Item) {
								outstring += "<option value=\"" + item.Value + "\">" + database.getLabel(item.Label) + "</option>";
							}
							outstring += "<options>";
						}
	
						outstring += "</parameter>";
					}
					if(parms == true) {
						outstring += "</config-description>";
					}
				}
				

				
				
				outstring += "</zwave:thing-descriptions>";

				if(outstring.isEmpty() == false) {
					String name = manufacturer.Name.toLowerCase()+"_"+product.Model.toLowerCase() + ".xml";
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
