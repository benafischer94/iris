/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2011-2021  Minnesota Department of Transportation
 * Copyright (C) 2009-2010  AHMCT, University of California
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package us.mn.state.dot.tms.client.dms;

import us.mn.state.dot.tms.SystemAttrEnum;

/**
 * Combobox edit mode.  These values correspond to the dms_composer_edit_mode
 * system attribute.
 *
 * @author Douglas Lau
 * @author Michael Darter
 */
public enum EditMode {
	NEVER, ALWAYS, AFTERKEY;

	/** Convert an int to enum */
	static private EditMode fromOrdinal(int o) {
		return (o >= 0 && o < values().length) ? values()[o] : NEVER;
	}

	/** Get the edit mode */
	static public EditMode getMode() {
		return fromOrdinal(
			SystemAttrEnum.DMS_COMPOSER_EDIT_MODE.getInt());
	}
}
