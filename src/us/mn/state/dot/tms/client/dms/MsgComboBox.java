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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.ComboBoxEditor;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import us.mn.state.dot.tms.SignText;
import us.mn.state.dot.tms.utils.MultiString;

/**
 * GUI for composing DMS messages.
 *
 * @author Douglas Lau
 * @author Michael Darter
 */
public class MsgComboBox extends JComboBox<SignText> {

	/** Get the MULTI string of an item */
	static private String getMulti(Object item) {
		if (item != null) {
			String ms = (item instanceof SignText)
				? ((SignText) item).getMulti()
				: item.toString();
			return new MultiString(ms.trim())
				.normalizeLine()
				.toString();
		}
		return "";
	}

	/** Prototype sign text */
	static private final SignText PROTOTYPE_SIGN_TEXT =
		new ClientSignText("12345678901234567890");

	/** Sign message composer containing the combo box */
	private final SignMessageComposer composer;

	/** Sign text line number */
	private final short line;

	/** Edit mode for combo box */
	private EditMode edit_mode = EditMode.NEVER;

	/** Combo box editor */
	private final Editor editor;

	/** Key listener for key events */
	private final KeyAdapter keyListener;

	/** Focus listener for editor focus events */
	private final FocusAdapter focusListener;

	/** Action listener for editor events */
	private final ActionListener editorListener;

	/** Listener for combo box events */
	private final ActionListener comboListener = new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
			composer.updateMessage(shouldUnlinkIncident());
		}
	};

	/** Create a message combo box.
	 * @param c Sign message composer. */
	public MsgComboBox(SignMessageComposer c, short ln) {
		composer = c;
		line = ln;
		setMaximumRowCount(21);
		// NOTE: We use a prototype display value so that combo boxes
		//       are always the same size.  This prevents all the
		//       widgets from being rearranged whenever a new sign is
		//       selected.
		setPrototypeDisplayValue(PROTOTYPE_SIGN_TEXT);
		setRenderer(new SignTextCellRenderer());
		editor = new Editor();
		keyListener = new KeyAdapter() {
			public void keyTyped(KeyEvent ke) {
				doKeyTyped(ke);
			}
		};
		focusListener = new FocusAdapter() {
			public void focusGained(FocusEvent fe) {
				doFocusGained();
			}
			public void focusLost(FocusEvent fe) {
				doFocusLost();
			}
		};
		editorListener = new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				if (edit_mode == EditMode.AFTERKEY)
					setEditable(false);
			}
		};
	}

	/** Initialize the message combo box */
	public void initialize() {
		setEditMode(false);
		setEditor(editor);
		addActionListener(comboListener);
		addKeyListener(keyListener);
		editor.addFocusListener(focusListener);
		editor.addActionListener(editorListener);
	}

	/** Dispose of the message combo box */
	public void dispose() {
		editor.removeActionListener(editorListener);
		editor.removeFocusListener(focusListener);
		removeKeyListener(keyListener);
		removeActionListener(comboListener);
	}

	/** Set the edit mode.
	 * @param cam Flag to indicate messages can be added. */
	public void setEditMode(boolean cam) {
		edit_mode = getEditMode(cam);
		if (isEditable() && edit_mode != EditMode.ALWAYS)
			setEditable(false);
		if (!isEditable() && edit_mode == EditMode.ALWAYS)
			setEditable(true);
	}

	/** Get the edit mode.
	 * @param cam Flag to indicate the user can add messages */
	private EditMode getEditMode(boolean cam) {
		EditMode em = EditMode.getMode();
		return (cam || em != EditMode.AFTERKEY)
		      ? em
		      : EditMode.NEVER;
	}

	/** Key event saved when making combobox editable */
	private KeyEvent key_event;

	/** Respond to a key typed event */
	private void doKeyTyped(KeyEvent ke) {
		if (edit_mode == EditMode.AFTERKEY) {
			if (!isEditable()) {
				setEditable(true);
				key_event = ke;
			}
		}
	}

	/** Respond to a focus gained event */
	private void doFocusGained() {
		if (key_event != null) {
			editor.dispatchEvent(key_event);
			key_event = null;
		}
	}

	/** Respond to a focus lost event */
	private void doFocusLost() {
		if (edit_mode == EditMode.AFTERKEY)
			setEditable(false);
		composer.updateMessage(shouldUnlinkIncident());
	}

	/** Should incident be unlinked on updates? */
	private boolean shouldUnlinkIncident() {
		return line == 1;
	}

	/** Get message text */
	public String getMessage() {
		Object item = (edit_mode == EditMode.ALWAYS)
			? editor.getItem()
			: getSelectedItem();
		return getMulti(item);
	}

	/** Editor for message combo box */
	private class Editor extends JTextField implements ComboBoxEditor {

		/** Get the component for the combo box editor */
		@Override
		public Component getEditorComponent() {
			return this;
		}

		/** Return the edited item */
		@Override
		public Object getItem() {
			return getMulti(getText());
		}

		/** Set the item that should be edited.
		 * @param item New value of item */
		@Override
		public void setItem(Object item) {
			setText(getMulti(item));
		}
	}
}
