package org.openhab.io.habmin.services.rule;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.thoughtworks.xstream.annotations.XStreamImplicit;

@XmlRootElement(name="rules")
public class RuleListBean {
	public String item;
	
	@XmlElement(name="rule")
	@XStreamImplicit(itemFieldName="rule")
	public List<RuleBean> rule;

	public RuleListBean() {};
}


