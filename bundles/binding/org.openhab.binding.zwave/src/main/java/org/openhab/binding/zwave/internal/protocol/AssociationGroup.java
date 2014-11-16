/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal.protocol;

import java.util.ArrayList;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * This class provides a storage class for zwave association groups
 * within the node class. This is then serialised to XML.
 * 
 * @author Chris Jackson
 * @since 1.4.0
 *
 */
@XStreamAlias("associationGroup")
public class AssociationGroup {
	int index;
	List<Association> associations = new ArrayList<Association>();

	public AssociationGroup(int index) {
		this.index = index;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int newIndex) {
		index = newIndex;
	}

	public void addAssociation(int node) {
		addAssociation(node, 0);
	}

	public void addAssociation(int node, int endpoint) {
		// Check if we're already associated
		if(isAssociated(node, endpoint)) {
			return;
		}
		
		// No - add a new association
		Association newAssociation = new Association(node, endpoint);
		associations.add(newAssociation);
	}

	public boolean removeAssociation(int node) {
		return removeAssociation(node, 0);
	}

	public boolean removeAssociation(int node, int endpoint) {
		int associationCnt = associations.size();
		for(int index = 0; index < associationCnt; index++) {
			Association association = associations.get(index);
			if(association.node == node && association.endpoint == endpoint) {
				associations.remove(index);
				return true;
			}
		}

		return false;
	}

	public boolean isAssociated(int node) {
		return isAssociated(node, 0);
	}

	public boolean isAssociated(int node, int endpoint) {
		int associationCnt = associations.size();
		for(int index = 0; index < associationCnt; index++) {
			Association association = associations.get(index);
			if(association.node == node && association.endpoint == endpoint) {
				return true;
			}
		}

		return false;
	}

	public List<Association> getAssociations() {
		return associations;
	}

	public class Association {
		int node;
		int endpoint;

		public Association(int node) {
			this.node = node;
			this.endpoint = 0;
		}

		public Association(int node, int endpoint) {
			this.node = node;
			this.endpoint = endpoint;
		}
	}
}
