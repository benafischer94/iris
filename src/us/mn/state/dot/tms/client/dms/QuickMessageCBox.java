/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2008-2021  Minnesota Department of Transportation
 * Copyright (C) 2010  AHMCT, University of California
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Iterator;
import java.util.TreeSet;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import us.mn.state.dot.tms.DMS;
import us.mn.state.dot.tms.DmsSignGroup;
import us.mn.state.dot.tms.DmsSignGroupHelper;
import us.mn.state.dot.tms.QuickMessage;
import us.mn.state.dot.tms.QuickMessageHelper;
import us.mn.state.dot.tms.SignGroup;
import us.mn.state.dot.tms.utils.MultiString;
import us.mn.state.dot.tms.utils.NumericAlphaComparator;

/**
 * The quick message combobox is a widget which allows the user to select
 * a precomposed "quick" message.  When the user changes a quick message
 * selection via this combobox, the dispatcher is flagged that it should update
 * its widgets with the newly selected message.
 *
 * @see us.mn.state.dot.tms.QuickMessage
 * @see us.mn.state.dot.tms.client.dms.DMSDispatcher
 *
 * @author Michael Darter
 * @author Douglas Lau
 */
public class QuickMessageCBox extends JComboBox<QuickMessage> {

	/** Check if a quick message should be included in combo box */
	static private boolean isValidMulti(QuickMessage qm) {
		MultiString ms = new MultiString(qm.getMulti());
		return ms.isValid() && !ms.isSpecial();
	}

	/** Lookup a quick message by name, or QuickMessage object.
	 * @return Quick message or null if not found. */
	static private QuickMessage lookupQuickMsg(Object obj) {
		if (obj instanceof QuickMessage)
			return (QuickMessage) obj;
		else if (obj instanceof String)
			return QuickMessageHelper.lookup((String) obj);
		else
			return null;
	}

	/** Combo box model for quick messages */
	private final DefaultComboBoxModel<QuickMessage> model =
		new DefaultComboBoxModel<QuickMessage>();

	/** DMS dispatcher */
	private final DMSDispatcher dispatcher;

	/** Focus listener for editor component */
	private final FocusListener focus_listener;

	/** Action listener for combo box */
	private final ActionListener action_listener;

	/** Counter to indicate we're adjusting widgets.  This needs to be
	 * incremented before calling dispatcher methods which might cause
	 * callbacks to this class.  This prevents infinite loops. */
	private int adjusting = 0;

	/** Create a new quick message combo box */
	public QuickMessageCBox(DMSDispatcher d) {
		setModel(model);
		dispatcher = d;
		setEditable(true);
		focus_listener = new FocusAdapter() {
			public void focusGained(FocusEvent e) {
				getEditor().selectAll();
			}
			public void focusLost(FocusEvent e) {
				handleEditorFocusLost(e);
			}
		};
		getEditor().getEditorComponent().addFocusListener(
			focus_listener);
		action_listener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updateDispatcher();
			}
		};
		addActionListener(action_listener);
	}

	/** Handle editor focus lost */
	private void handleEditorFocusLost(FocusEvent e) {
		Object item = getEditor().getItem();
		if (item instanceof String)
			handleEditorFocusLost((String) item);
	}

	/** Handle editor focus lost */
	private void handleEditorFocusLost(String item) {
		String name = item.replace(" ", "");
		getEditor().setItem(name);
		QuickMessage qm = lookupQuickMsg(name);
		if (qm != null)
			setSelectedItem(qm);
	}

	/** Update the dispatcher with the selected quick message */
	private void updateDispatcher() {
		if (adjusting == 0) {
			dispatcher.setQuickMessage(getSelectedMessage());
			dispatcher.selectPreview(true);
		}
	}

	/** Get the currently selected quick message */
	public QuickMessage getSelectedMessage() {
		Object item = getSelectedItem();
		return (item instanceof QuickMessage)
		      ? (QuickMessage) item
		      : null;
	}

	/** Set selected item, but only if it is different from the
	 * currently selected item.  Triggers a call to actionPerformed().
	 * @param obj May be a String, or QuickMessage. */
	@Override
	public void setSelectedItem(Object obj) {
		QuickMessage qm = lookupQuickMsg(obj);
		if (qm != getSelectedMessage())
			super.setSelectedItem(qm);
	}

	/** Populate the quick message model, with sorted quick messages */
	public void populateModel(DMS dms) {
		TreeSet<QuickMessage> msgs = createMessageSet(dms);
		adjusting++;
		model.removeAllElements();
		model.addElement(null);
		for (QuickMessage qm: msgs)
			model.addElement(qm);
		adjusting--;
	}

	/** Create a set of quick messages for the specified DMS */
	private TreeSet<QuickMessage> createMessageSet(DMS dms) {
		TreeSet<QuickMessage> msgs = new TreeSet<QuickMessage>(
			new NumericAlphaComparator<QuickMessage>());
		Iterator<DmsSignGroup> it = DmsSignGroupHelper.iterator();
		while (it.hasNext()) {
			DmsSignGroup dsg = it.next();
			if (dsg.getDms() == dms) {
				SignGroup sg = dsg.getSignGroup();
				Iterator<QuickMessage> qit =
					QuickMessageHelper.iterator();
				while (qit.hasNext()) {
					QuickMessage qm = qit.next();
					if (qm.getSignGroup() == sg) {
						if (isValidMulti(qm))
							msgs.add(qm);
					}
				}
			}
		}
		return msgs;
	}

	/** Set the enabled status */
	@Override
	public void setEnabled(boolean e) {
		super.setEnabled(e);
		if (!e) {
			setSelectedItem(null);
			removeAllItems();
		}
	}

	/** Dispose */
	public void dispose() {
		removeActionListener(action_listener);
		getEditor().getEditorComponent().
			removeFocusListener(focus_listener);
	}
}
