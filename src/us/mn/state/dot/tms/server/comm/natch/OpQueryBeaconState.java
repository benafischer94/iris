/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2021-2022  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.server.comm.natch;

import java.io.IOException;
import java.nio.ByteBuffer;
import us.mn.state.dot.tms.server.BeaconImpl;
import us.mn.state.dot.tms.server.comm.Operation;

/**
 * Step to query a beacon
 *
 * @author Douglas Lau
 */
public class OpQueryBeaconState extends OpNatch {

	/** Beacon device */
	private final BeaconImpl beacon;

	/** Pin status property */
	private final PinStatusProp prop;

	/** Create a new query beacon state step */
	public OpQueryBeaconState(Counter c, BeaconImpl b) {
		beacon = b;
		prop = new PinStatusProp(c, beacon.getPin());
	}

	/** Poll the controller */
	@Override
	public void poll(Operation op, ByteBuffer tx_buf) throws IOException {
		prop.encodeQuery(op, tx_buf);
		setPolling(false);
	}

	/** Get the property */
	@Override
	protected NatchProp getProp() {
		return prop;
	}

	/** Handle received property */
	@Override
	protected void handleReceived(Operation op, NatchProp pr) {
		assert pr == prop;
		beacon.setFlashingNotify(prop.getStatus());
		setDone(true);
	}
}
