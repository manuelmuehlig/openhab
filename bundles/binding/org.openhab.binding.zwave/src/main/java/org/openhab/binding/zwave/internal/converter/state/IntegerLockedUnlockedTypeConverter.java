/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal.converter.state;

import org.openhab.core.library.types.LockedUnlockedType;

/**
 * Converts from a Z-Wave integer value to a {@link LockedUnlockedType}
 * @author Dave Badia
 * @since 1.6.0
 */
public class IntegerLockedUnlockedTypeConverter extends
		ZWaveStateConverter<Integer, LockedUnlockedType> {
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected LockedUnlockedType convert(Integer value) {
		return value == 0xFF ? LockedUnlockedType.LOCKED : LockedUnlockedType.UNLOCKED;
	}

}
