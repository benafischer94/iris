/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2021  Minnesota Department of Transportation
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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Class to handle converting alert XML into JSON
 *
 * @author Douglas Lau
 */
public class AlertHandler extends DefaultHandler {

	/** All elements of interest */
	static private final Set<String> ELEMENTS = Stream.of(
		// "alert" element and sub-elements
		"alert", "identifier", "sender", "sent", "status", "msgType",
		"source", "scope", "restriction", "addresses", "code", "note",
		// IGNORE: "references", "incidents",
		// "info" element and sub-elements
		"info", "language", "category", "event", "responseType",
		"urgency", "severity", "certainty", "audience", "eventCode",
		"effective", "onset", "expires", "senderName", "headline",
		"description", "instruction", "web", "contact", "parameter",
		// "resource" element and sub-elements
		// IGNORE: "resource", "resourceDesc", "mimeType", "size",
		// IGNORE: "uri", "derefUri", "digest",
		// "area" element and sub-elements
		"area", "areaDesc", "polygon", "circle", "geocode", "altitude",
		"ceiling",
		// sub-elements of "eventCode", "parameter" and "geocode"
		"valueName", "value"
	).collect(Collectors.toCollection(HashSet::new));

	/** Array elements (more than one allowed) */
	static private final Set<String> ARRAYS = Stream.of(
		"code", "info", "category", "responseType", "eventCode",
		"parameter", "resource", "area", "polygon", "circle",
		"geocode", "altitude"
	).collect(Collectors.toCollection(HashSet::new));

	/** Element stack */
	private ArrayDeque<Object> stack = new ArrayDeque<Object>();

	/** Start an XML element.
	 *
	 *  Push a new JSON object to the stack.  This object will contain all
	 *  sub-elements of this element. */
	@Override
	public void startElement(String uri, String localName,
		String qName, Attributes attrs)
	{
		// CAP doesn't use attributes, so ignore them
		stack.push(new JSONObject());
	}

	/** Handle characters in current node */
	@Override
	public void characters(char[] ch, int start, int length)
		throws SAXException
	{
		addContent(ch, start, length);
	}

	/** Handle ignorable whitespace in current node */
	@Override
	public void ignorableWhitespace(char[] ch, int start, int length)
		throws SAXException
	{
		addContent(ch, start, length);
	}

	/** Add text content to the leaf node */
	private void addContent(char[] ch, int start, int length)
		throws SAXException
	{
		String content = new String(ch, start, length);
		Object obj = stack.pop();
		if (obj instanceof JSONObject) {
			JSONObject jo = (JSONObject) obj;
			if (jo.isEmpty())
				stack.push(content);
			else {
				// ignore text content in branch nodes
				stack.push(jo);
			}
		} else
			stack.push(obj.toString() + content);
	}

	/** End an XML element.
	 *
	 *  Pop the object on top of the stack.  It will be either a JSON object
	 *  or a String.  Either way, add it to the object now at the top of the
	 *  stack. */
	@Override
	public void endElement(String uri, String localName,
		String qName) throws SAXException
	{
		Object obj = stack.pop();
		if (qName.equals("alert")) {
			if (obj instanceof JSONObject)
				storeAlert((JSONObject) obj);
		} else if (ELEMENTS.contains(qName)) {
			Object parent = stack.peek();
			if (parent instanceof JSONObject) {
				JSONObject jo = (JSONObject) parent;
				if (ARRAYS.contains(qName))
					jo.append(qName, obj);
				else
					jo.put(qName, obj);
			}
		}
	}

	/** Store alert in database */
	private void storeAlert(JSONObject alert) {
		// FIXME
	}
}
