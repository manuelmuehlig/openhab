package org.openhab.io.habmin.repository;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


import com.thoughtworks.xstream.annotations.XStreamImplicit;


@XmlRootElement(name="configuation")
public class HabminConfigDatabaseBean {

	@XmlElement(name="items")
	@XStreamImplicit(itemFieldName="items")
	public List<HabminItemBean> items;
	
	public HabminConfigDatabaseBean() {};
}
