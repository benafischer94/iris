/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2021  Minnesota Department of Transportation
 * Copyright (C) 2020  SRF Consulting Group, Inc.
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
package us.mn.state.dot.tms.server.comm.ipaws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import us.mn.state.dot.sched.TimeSteward;
import us.mn.state.dot.tms.SystemAttrEnum;

/**
 * Common Alerting Protocol (CAP) reader.
 *
 * Reads CAP XML documents, converts alerts to JSON and stores to the database.
 *
 * @author Douglas Lau
 * @author Michael Janson
 * @author Gordon Parikh
 */
public class CapReader {

	/** Date formatter for formatting/parsing dates in ISO 8601 format */
	static public final SimpleDateFormat ISO8601 =
		new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

	/** Most recent successful request date
	 *  (at startup, initialize to an hour ago) */
	static private Date REQ_SUCCESS = new Date(
		TimeSteward.currentTimeMillis() - 60 * 60 * 1000);

	/** Get date for REST API request */
	static public String getReqDate() {
		return ISO8601.format(REQ_SUCCESS);
	}

	/** Get XML save enabled setting */
	static private boolean getXmlSaveEnabled() {
		return SystemAttrEnum.IPAWS_XML_SAVE_ENABLE.getBoolean();
	}

	/** Input stream */
	private final InputStream input;

	/** Output stream to cache copy of XML */
	private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

	/** Alert handler */
	private final AlertHandler handler = new AlertHandler();

	/** Create a new CAP reader */
	public CapReader(InputStream is) {
		input = is;
	}

	/** Parse alerts */
	public void parse() throws IOException {
		Date now = TimeSteward.getDateInstance();
		try {
			SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser parser = spf.newSAXParser();
			parser.parse(inputStream(), handler);
			REQ_SUCCESS = now;
		}
		catch (ParserConfigurationException | SAXException e) {
			IpawsPoller.slog("parse error: " + e.getMessage());
			saveXmlFile();
		}
	}

	/** Get input stream containing the XML */
	private InputStream inputStream() throws IOException {
		if (getXmlSaveEnabled()) {
			// make a copy of the input stream - if we hit an
			// exception we will save the XML and the text of the
			// exception on the server
			byte[] buf = new byte[1024];
			int len;
			while ((len = input.read(buf)) > -1)
				baos.write(buf, 0, len);
			baos.flush();
			return new ByteArrayInputStream(baos.toByteArray());
		} else
			return input;
	}

	/** Save the XML contents to a file */
	private void saveXmlFile() throws IOException {
		if (getXmlSaveEnabled()) {
			String fn = "/var/log/iris/ipaws_err_" + ISO8601.format(
				TimeSteward.getDateInstance()) + ".xml";
			baos.writeTo(new FileOutputStream(fn));
		}
	}
}
