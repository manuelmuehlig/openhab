package org.openhab.io.habmin.services.rule;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.thoughtworks.xstream.annotations.XStreamImplicit;

@XmlRootElement(name="rule")
public class RuleBean {
	@XmlElement(name="item")
	public String item;
	
	@XmlElement(name="name")
	public String name;
	
	@XmlElement(name="label")
	public String label;
	
	@XmlElement(name="type")
	public String type;

	@XmlElement(name="description")
	public String description;

	@XmlElement(name="itemtype")
	@XStreamImplicit(itemFieldName="itemtype")
	public List<String> itemType;

	@XmlElement(name="variable")
	@XStreamImplicit(itemFieldName="variable")
	public List<RuleVariableBean> variable;

	@XmlElement(name="import")
	@XStreamImplicit(itemFieldName="import")
	public List<String> imports;

	@XmlElement(name="trigger")
	@XStreamImplicit(itemFieldName="trigger")
	public List<String> trigger;
	
	@XmlElement(name="action")
	@XStreamImplicit(itemFieldName="action")
	public List<String> action;

	@XmlElement(name="linkeditem")
	public String linkeditem;

	public RuleBean() {}
}

