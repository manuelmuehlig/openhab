/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.habmin.services.zwave;


import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;


import org.openhab.io.habmin.internal.resources.MediaTypeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>This class acts as a REST resource for items and provides different methods to interact with them,
 * like retrieving lists of items, sending commands to them or checking a single status.</p>
 * 
 * <p>The typical content types are plain text for status values and XML or JSON(P) for more complex data
 * structures</p>
 * 
 * <p>This resource is registered with the Jersey servlet.</p>
 *
 * @author Chris Jackson
 * @since 1.4.0
 */
@Path(ZWaveConfigResource.PATH)
public class ZWaveConfigResource {

	private static final Logger logger = LoggerFactory.getLogger(ZWaveConfigResource.class); 
	
	/** The URI path to this resource */
    public static final String PATH = "zwave";
    
	@Context UriInfo uriInfo;
	@GET
    @Produces( { MediaType.WILDCARD })
    public Response getItems(
    		@Context HttpHeaders headers,
    		@QueryParam("type") String type, 
    		@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", new String[] { uriInfo.getPath(), type });

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
//		if(responseType!=null) {
//	    	Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ?
//	    			new JSONWithPadding(new ItemListBean(getItemBeans()), callback) : new ItemListBean(getItemBeans());
//	    	return Response.ok(responseObject, responseType).build();
//		} else {
			return Response.notAcceptable(null).build();
//		}
    }

}
