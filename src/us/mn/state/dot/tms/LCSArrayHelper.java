/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2009-2024  Minnesota Department of Transportation
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
package us.mn.state.dot.tms;

import java.util.Iterator;
import java.util.TreeMap;

/**
 * Helper class for LCSArrays.
 *
 * @author Douglas Lau
 */
public class LCSArrayHelper extends BaseHelper {

	/** Prevent object creation */
	private LCSArrayHelper() {
		assert false;
	}

	/** Get an LCS array iterator */
	static public Iterator<LCSArray> iterator() {
		return new IteratorWrapper<LCSArray>(namespace.iterator(
			LCSArray.SONAR_TYPE));
	}

	/** Lookup the LCS objects for an array */
	static public LCS[] lookupLCSs(LCSArray lcs_array) {
		TreeMap<Integer, LCS> lanes = new TreeMap<Integer, LCS>();
		Iterator<LCS> it = LCSHelper.iterator();
		while(it.hasNext()) {
			LCS lcs = it.next();
			if(lcs.getArray() == lcs_array)
				lanes.put(lcs.getLane(), lcs);
		}
		int n_lanes = 0;
		if(lanes.size() > 0)
			n_lanes = lanes.lastKey();
		LCS[] lcss = new LCS[n_lanes];
		for(int i = 0; i < n_lanes; i++)
			lcss[i] = lanes.get(i + 1);
		return lcss;
	}

	/** Lookup the LCS in the specified lane */
	static public LCS lookupLCS(LCSArray lcs_array, int lane) {
		Iterator<LCS> it = LCSHelper.iterator();
		while(it.hasNext()) {
			LCS lcs = it.next();
			if(lcs.getArray() == lcs_array &&
			   lcs.getLane() == lane)
				return lcs;
		}
		return null;
	}

	/** Lookup the DMS for the LCS in the specified lane */
	static public DMS lookupDMS(LCSArray lcs_array, int lane) {
		LCS lcs = lookupLCS(lcs_array, lane);
		return (lcs != null) ? DMSHelper.lookup(lcs.getName()) : null;
	}

	/** Lookup the camera preset for an LCS array */
	static public CameraPreset getPreset(LCSArray lcs_array) {
		if (lcs_array != null)
			return DMSHelper.getPreset(lookupDMS(lcs_array, 1));
		else
			return null;
	}

	/** Lookup the location of the LCS array */
	static public String lookupLocation(LCSArray lcs_array) {
		return GeoLocHelper.getLocation(lookupGeoLoc(lcs_array));
	}

	/** Lookup the location of the LCS array */
	static public GeoLoc lookupGeoLoc(LCSArray lcs_array) {
		DMS dms = lookupDMS(lcs_array, 1);
		return (dms != null) ? dms.getGeoLoc() : null;
	}

	/** Check if an LCS array is offline */
	static public boolean isOffline(LCSArray lcs_array) {
		return ItemStyle.OFFLINE.checkBit(lcs_array.getStyles());
	}

	/** Check if an LCS array is deployed */
	static public boolean isDeployed(LCSArray lcs_array) {
		return ItemStyle.DEPLOYED.checkBit(lcs_array.getStyles());
	}

	/** Get LCS array faults */
	static public String getFaults(LCSArray lcs_array) {
		Iterator<LCS> it = LCSHelper.iterator();
		while (it.hasNext()) {
			LCS lcs = it.next();
			if (lcs.getArray() == lcs_array) {
				String f = optFaults(lcs);
				if (f != null)
					return lcs.getName() + ": " + f;
			}
		}
		return "";
	}

	/** Get optional LCS faults, or null */
	static private String optFaults(LCS lcs) {
		DMS dms = DMSHelper.lookup(lcs.getName());
		return (dms != null) ? DMSHelper.optFaults(dms) : null;
	}
}
