/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.habmin.services.chart;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.eclipse.emf.common.util.EList;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.persistence.FilterCriteria;
import org.openhab.core.persistence.FilterCriteria.Ordering;
import org.openhab.core.persistence.HistoricItem;
import org.openhab.core.persistence.PersistenceService;
import org.openhab.core.persistence.QueryablePersistenceService;
import org.openhab.core.types.State;
import org.openhab.io.habmin.HABminApplication;
import org.openhab.io.habmin.internal.resources.LabelSplitHelper;
import org.openhab.io.habmin.internal.resources.MediaTypeHelper;
import org.openhab.io.habmin.services.persistence.ItemPersistenceBean;
import org.openhab.io.habmin.services.persistence.PersistenceBean;
import org.openhab.io.habmin.services.persistence.PersistenceModelHelper;
import org.openhab.io.habmin.services.persistence.PersistenceServiceBean;
import org.openhab.model.core.ModelRepository;
import org.openhab.model.items.ItemModel;
import org.openhab.model.items.ModelItem;
import org.openhab.ui.items.ItemUIRegistry;
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
@Path(PersistenceResource.PATH_HISTORY)
public class PersistenceResource {

	private static final Logger logger = LoggerFactory.getLogger(PersistenceResource.class);

	/** The URI path to this resource */
	public static final String PATH_HISTORY = "persistence";

	@Context
	UriInfo uriInfo;

	@GET
	@Path("/services")
	@Produces({ MediaType.WILDCARD })
	public Response httpGetPersistenceServices(@Context HttpHeaders headers, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getPersistenceServices(), callback) : getPersistenceServices();
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@GET
	@Path("/items")
	@Produces({ MediaType.WILDCARD })
	public Response httpGetPersistenceItems(@Context HttpHeaders headers, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getPersistenceItems(), callback) : getPersistenceItems();
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@GET
	@Path("/{servicename: [a-zA-Z_0-9]*}/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response httpGetPersistenceItemData(@Context HttpHeaders headers,
			@PathParam("servicename") String serviceName, @PathParam("itemname") String itemName,
			@QueryParam("starttime") String startTime, @QueryParam("endtime") String endTime,
			@QueryParam("page") long pageNumber, @QueryParam("pagelength") long pageLength,
			@QueryParam("type") String type, @QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		final String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			final Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					getItemHistoryBean(serviceName, itemName, startTime, endTime), callback) : getItemHistoryBean(
					serviceName, itemName, startTime, endTime);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}
/*
	@POST
	@Path("/{servicename: [a-zA-Z_0-9]*}/{itemname: [a-zA-Z_0-9]*}")
	@Produces({ MediaType.WILDCARD })
	public Response httpPostPersistenceItemData(@Context HttpHeaders headers,
			@PathParam("servicename") String serviceName, @PathParam("itemname") String itemName,
			@QueryParam("time") String time, @QueryParam("state") String state, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		final String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			final Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					updateItemHistory(serviceName, itemName, time, state), callback) : updateItemHistory(serviceName,
					itemName, time, state);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}

	@DELETE
	@Path("/{servicename: [a-zA-Z_0-9]*}/{itemname: [a-zA-Z_0-9]*}}")
	@Produces({ MediaType.WILDCARD })
	public Response httpDeleteItemData(@Context HttpHeaders headers, @PathParam("serviceame") String serviceName,
			@PathParam("itemname") String itemName, @QueryParam("time") String time, @QueryParam("type") String type,
			@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
		logger.debug("Received HTTP GET request at '{}' for media type '{}'.", uriInfo.getPath(), type);

		final String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
		if (responseType != null) {
			final Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ? new JSONWithPadding(
					deleteItemHistory(serviceName, itemName, time), callback) : deleteItemHistory(serviceName,
					itemName, time);
			return Response.ok(responseObject, responseType).build();
		} else {
			return Response.notAcceptable(null).build();
		}
	}
*/
	
	Date convertTime(String sTime) {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

		// replace with your start date string
		Date dateTime;
		try {
			dateTime = df.parse(sTime);
		} catch (ParseException e) {
			// Time doesn't parse as string - try long
			long lTime = Long.parseLong(sTime, 10);
			dateTime = new Date(lTime);
		}

		return dateTime;
	}

	static public Item getItem(String itemname) {
		ItemUIRegistry registry = HABminApplication.getItemUIRegistry();
		if (registry != null) {
			try {
				Item item = registry.getItem(itemname);
				return item;
			} catch (ItemNotFoundException e) {
				logger.debug(e.getMessage());
			}
		}
		return null;
	}

	private ItemHistoryBean getItemHistoryBean(String serviceName, String itemName, String timeBegin, String timeEnd) {
		PersistenceService service = (PersistenceService) HABminApplication.getPersistenceServices().get(serviceName);
		if (service == null) {
			logger.debug("Persistence service not found '{}'.", serviceName);
			throw new WebApplicationException(404);
		}

		if (!(service instanceof QueryablePersistenceService)) {
			logger.debug("Persistence service not queryable '{}'.", serviceName);
			throw new WebApplicationException(404);
		}

		Item item = getItem(itemName);
		if (item == null) {
			logger.info("Received HTTP GET request at '{}' for the unknown item '{}'.", uriInfo.getPath(), itemName);
			throw new WebApplicationException(404);
		}

		QueryablePersistenceService qService = (QueryablePersistenceService) service;

		Date dateTimeBegin = new Date();
		Date dateTimeEnd = dateTimeBegin;
		if (timeBegin != null)
			dateTimeBegin = convertTime(timeBegin);

		if (timeEnd != null)
			dateTimeEnd = convertTime(timeEnd);
		
		// End now...
		if(dateTimeEnd.getTime() == 0)
			dateTimeEnd = new Date();
		if(dateTimeBegin.getTime() == 0)
			dateTimeBegin = new Date(dateTimeEnd.getTime() - 86400000);

		// Default to 1 days data if the times are the same
		if (dateTimeBegin.getTime() >= dateTimeEnd.getTime())
			dateTimeBegin = new Date(dateTimeEnd.getTime() - 86400000);

		FilterCriteria filter = new FilterCriteria();
		filter.setBeginDate(dateTimeBegin);
		filter.setEndDate(dateTimeEnd);
		filter.setItemName(item.getName());
		filter.setOrdering(Ordering.ASCENDING);

		Iterable<HistoricItem> result = qService.query(filter);
		Iterator<HistoricItem> it = result.iterator();

		Long quantity = 0l;
		double average = 0;
		DecimalType minimum = null;
		DecimalType maximum = null;
		Date timeMinimum = null;
		Date timeMaximum = null;

		ItemHistoryBean bean = null;
		bean = new ItemHistoryBean();

		bean.name = item.getName();

		// Iterate through the data
		while (it.hasNext()) {
			HistoricItem historicItem = it.next();
			State state = historicItem.getState();
			if (state instanceof DecimalType) {
				DecimalType value = (DecimalType) state;

				bean.addData(Long.toString(historicItem.getTimestamp().getTime()), value.toString());

				average += value.doubleValue();
				quantity++;

				if (minimum == null || value.compareTo(minimum) < 0) {
					minimum = value;
					timeMinimum = historicItem.getTimestamp();
				}

				if (maximum == null || value.compareTo(maximum) > 0) {
					maximum = value;
					timeMaximum = historicItem.getTimestamp();
				}
			}
		}

		bean.datapoints = Long.toString(quantity);
		if (quantity > 0)
			bean.stateavg = Double.toString(average / quantity);

		if (minimum != null) {
			bean.statemin = minimum.toString();
			bean.timemin = Long.toString(timeMinimum.getTime());
		}

		if (maximum != null) {
			bean.statemax = maximum.toString();
			bean.timemax = Long.toString(timeMaximum.getTime());
		}

		bean.type = item.getClass().getSimpleName();

		return bean;
	}

	/**
	 * Read through an items model. Get all the items and provide the information that's of use
	 * for graphing/stats etc.
	 * Only items with persistence services configured are returned.
	 * 
	 * @param modelItems the item model
	 * @param modelName the model name
	 * @return
	 */
	private List<ItemHistoryBean> readItemModel(ItemModel modelItems, String modelName) {
		List<ItemHistoryBean> beanList = new ArrayList<ItemHistoryBean>();

		EList<ModelItem> modelList = modelItems.getItems();
		for (ModelItem item : modelList) {
			ItemHistoryBean bean = new ItemHistoryBean();

			if (item.getLabel() != null) {
				LabelSplitHelper label = new LabelSplitHelper(item.getLabel());

				bean.label = label.getLabel();
				bean.format = label.getFormat();
				bean.units = label.getUnit();
			}

			bean.icon = item.getIcon();
			bean.name = item.getName();
			if (item.getType() == null)
				bean.type = "GroupItem";
			else
				bean.type = item.getType() + "Item";

			bean.groups = new ArrayList<String>();
			EList<String> groupList = item.getGroups();
			for (String group : groupList) {
				bean.groups.add(group.toString());
			}

			ModelRepository repo = HABminApplication.getModelRepository();
			if (repo == null)
				return null;

			// Loop through all the registered persistence models and read their
			// data...
			bean.services = new ArrayList<String>();
			for (Map.Entry<String, PersistenceService> service : HABminApplication.getPersistenceServices().entrySet()) {
				PersistenceModelHelper helper = new PersistenceModelHelper(service.getKey());
				ItemPersistenceBean p = helper.getItemPersistence(item.getName(), item.getGroups());
				if (p != null)
					bean.services.add(p.service);
			}

			// We're only interested in items with persistence enabled
			if (bean.services.size() > 0)
				beanList.add(bean);
		}

		return beanList;
	}

	/**
	 * Gets a list of persistence services currently configured in the system
	 * 
	 * @return
	 */
	private PersistenceBean getPersistenceServices() {
		PersistenceBean bean = new PersistenceBean();

		bean.serviceEntries.addAll(getPersistenceServiceList());

		return bean;
	}

	private PersistenceBean getPersistenceItems() {
		PersistenceBean bean = new PersistenceBean();

		for (Map.Entry<String, PersistenceService> service : HABminApplication.getPersistenceServices().entrySet()) {
			PersistenceServiceBean serviceBean = new PersistenceServiceBean();

			serviceBean.name = service.getKey();
			serviceBean.actions = new ArrayList<String>();

			serviceBean.actions.add("Create");
			if (service.getValue() instanceof QueryablePersistenceService)
				serviceBean.actions.add("Read");
//			if (service.getValue() instanceof CRUDPersistenceService) {
//				serviceBean.actions.add("Update");
//				serviceBean.actions.add("Delete");
//			}
		}

		bean.itemEntries = new ArrayList<ItemHistoryBean>();
		bean.itemEntries.addAll(getHistory());
		return bean;
	}

	private List<PersistenceServiceBean> getPersistenceServiceList() {
		List<PersistenceServiceBean> beanList = new ArrayList<PersistenceServiceBean>();

		for (Map.Entry<String, PersistenceService> service : HABminApplication.getPersistenceServices().entrySet()) {
			PersistenceServiceBean serviceBean = new PersistenceServiceBean();

			serviceBean.name = service.getKey();
			serviceBean.actions = new ArrayList<String>();

			serviceBean.actions.add("Create");
			if (service.getValue() instanceof QueryablePersistenceService)
				serviceBean.actions.add("Read");
//			if (service.getValue() instanceof CRUDPersistenceService) {
//				serviceBean.actions.add("Update");
//				serviceBean.actions.add("Delete");
//			}

			beanList.add(serviceBean);
		}

		return beanList;
	}

	private List<ItemHistoryBean> getHistory() {
		List<ItemHistoryBean> beanList = new ArrayList<ItemHistoryBean>();

		ModelRepository repo = HABminApplication.getModelRepository();
		if (repo == null)
			return null;

		File folder = new File("configurations/items/");
		File[] listOfFiles = folder.listFiles();

		if (listOfFiles == null)
			return null;

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile() & listOfFiles[i].getName().endsWith(".items")) {
				ItemModel items = (ItemModel) repo.getModel(listOfFiles[i].getName());
				List<ItemHistoryBean> beans = readItemModel(items,
						listOfFiles[i].getName().substring(0, listOfFiles[i].getName().indexOf('.')));
				if (beans != null)
					beanList.addAll(beans);
			}
		}

		return beanList;
	}
/*
	private boolean updateItemHistory(String serviceName, String itemName, String time, String state) {
		State nState = new StringType(state);
		Date recordTime = convertTime(time);

		if(recordTime.getTime() == 0)
			return false;

		PersistenceService service = HABminApplication.getPersistenceServices().get(serviceName);
		if (service instanceof CRUDPersistenceService) {
			CRUDPersistenceService qService = (CRUDPersistenceService) service;
			FilterCriteria filter = new FilterCriteria();
			filter.setBeginDate(recordTime);
			filter.setItemName(itemName);
			return qService.update(filter, nState);
		} else {
			logger.warn("The persistence service does not support UPDATE.");
			return false;
		}
	}

	private boolean deleteItemHistory(String serviceName, String itemName, String time) {
		Date recordTime = convertTime(time);

		if(recordTime.getTime() == 0)
			return false;
		
		PersistenceService service = HABminApplication.getPersistenceServices().get(serviceName);
		if (service instanceof CRUDPersistenceService) {
			CRUDPersistenceService qService = (CRUDPersistenceService) service;
			FilterCriteria filter = new FilterCriteria();
			filter.setBeginDate(recordTime);
			filter.setItemName(itemName);
			return qService.delete(filter);
		} else {
			logger.warn("The persistence service does not support DELETE.");
			return false;
		}
	}
*/
}
