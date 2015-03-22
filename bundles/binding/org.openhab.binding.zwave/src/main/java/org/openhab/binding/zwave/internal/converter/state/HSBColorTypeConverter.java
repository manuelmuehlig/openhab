/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal.converter.state;

import org.openhab.core.library.types.HSBType;

/**
 * Converts from a {@link HSBType} to a {@link ZWaveColorType}
 * @author Chris Jackson
 * @since 1.7.0
 */
public class HSBColorTypeConverter extends ZWaveStateConverter<String, HSBType> {
// TODO: Resolve this conversion HSB Type
	/**
	 * {@inheritDoc}
	 */
//	@Override
//	protected ZWaveColorType convert(HSBType value) {
//		return new StringType(value);
//	}

	@Override
	protected HSBType convert(String value) {
		// TODO Auto-generated method stub
		return null;
	}

}
