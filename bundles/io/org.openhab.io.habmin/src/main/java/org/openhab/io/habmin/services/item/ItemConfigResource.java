/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2013, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.io.habmin.services.item;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
import org.openhab.model.core.ModelRepository;
import org.openhab.model.items.ItemModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.json.JSONWithPadding;

/**
 * <p>
 * This class acts as a REST resource for history data and provides different
 * methods to interact with the, persistence store
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
 * @since 1.3.0
 */
@Path(ItemConfigResource.PATH_CONFIG)
public class ItemConfigResource {

	private static final Logger logger = LoggerFactory.getLogger(ItemConfigResource.class);

	/** The URI path to this resource */
	public static final String PATH_CONFIG = "config/items";

	@Context
	UriInfo uriInfo;

	@GET
	@Produces({ MediaType.WILDCARD })
	public Response getItems(@Context HttpHeaders headers, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					new ItemConfigListBean(getItemConfigBeanList()), callback) : new ItemConfigListBean(
					getItemConfigBeanList());
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@GET
	@Path("/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response getItem(@Context HttpHeaders headers, @QueryParam("type") String type,
			@PathParam("itemname") String itemname,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getItemConfigBean(itemname), callback) : getItemConfigBean(itemname);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@PUT
	@Path("/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response putItem(@Context HttpHeaders headers, @QueryParam("type") String type,
			@PathParam("itemname") String itemname,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback, ItemConfigBean item) {
		logger.debug("Received HTTP PUT request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					updateItemConfigBean(itemname, item, false), callback)
					: updateItemConfigBean(itemname, item, false);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@POST
	@Path("/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response postItem(@Context HttpHeaders headers, @QueryParam("type") String type,
			@PathParam("itemname") String itemname,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback, ItemConfigBean item) {
		logger.debug("Received HTTP POST request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					updateItemConfigBean(itemname, item, false), callback)
					: updateItemConfigBean(itemname, item, false);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@DELETE
	@Path("/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response deleteItem(@Context HttpHeaders headers, @QueryParam("type") String type,
			@PathParam("itemname") String itemname,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP DELETE request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					new ItemConfigListBean(deleteItem(itemname)), callback) : new ItemConfigListBean(
					deleteItem(itemname));
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	private List<ItemConfigBean> getItemConfigBeanList() {
		List<ItemConfigBean> beanList = new ArrayList<ItemConfigBean>();

		ModelRepository repo = HABminApplication.getModelRepository();
		if (repo == null)
			return null;

		File folder = new File("configurations/items/");
		File[] listOfFiles = folder.listFiles();

		if (listOfFiles == null)
			return null;

		ItemModelHelper modelHelper = new ItemModelHelper();

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile() & listOfFiles[i].getName().endsWith(".items")) {
				ItemModel items = (ItemModel) repo.getModel(listOfFiles[i].getName());
				List<ItemConfigBean> beans = modelHelper.readItemModel(items,
						listOfFiles[i].getName().substring(0, listOfFiles[i].getName().indexOf('.')));
				if (beans != null)
					beanList.addAll(beans);
			}
		}

		return beanList;
	}

	private List<ItemConfigBean> deleteItem(String itemname) {
		ItemConfigBean item = getItemConfigBean(itemname);
		if (item == null)
			return getItemConfigBeanList();

		updateItemConfigBean(itemname, item, true);

		return getItemConfigBeanList();
	}

	private ItemConfigBean getItemConfigBean(String itemname) {
		// Get the base information from the openHAB Item model
		ItemModelHelper modelHelper = new ItemModelHelper();
		return modelHelper.getItemConfigBean(itemname);
	}

	private ItemConfigBean updateItemConfigBean(String itemname, ItemConfigBean itemUpdate, boolean deleteItem) {
		ItemModelHelper itemHelper = new ItemModelHelper();

		itemHelper.updateItem(itemname, itemUpdate, deleteItem);
		
		return getItemConfigBean(itemname);
	}
}
