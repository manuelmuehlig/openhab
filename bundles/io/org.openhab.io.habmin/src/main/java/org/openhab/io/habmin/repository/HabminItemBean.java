package org.openhab.io.habmin.repository;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.openhab.io.habmin.services.rule.RuleBean;

import com.thoughtworks.xstream.annotations.XStreamImplicit;


@XmlRootElement(name="item")
public class HabminItemBean {
	public String name;
	
	@XmlElement(name="extendedProperties")
	@XStreamImplicit(itemFieldName="extendedProperties")
	public List<String> extendedProperties;
	
	@XmlElement(name="rules")
	@XStreamImplicit(itemFieldName="rules")
	public List<RuleBean> rules;

	public HabminItemBean() {};
}
