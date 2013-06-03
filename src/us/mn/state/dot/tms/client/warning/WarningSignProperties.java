/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2004-2013  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.client.warning;

import java.awt.Color;
import javax.swing.ListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import us.mn.state.dot.sched.FocusLostJob;
import us.mn.state.dot.sonar.client.TypeCache;
import us.mn.state.dot.tms.Camera;
import us.mn.state.dot.tms.Controller;
import us.mn.state.dot.tms.WarningSign;
import static us.mn.state.dot.tms.client.IrisClient.WORKER;
import us.mn.state.dot.tms.client.Session;
import us.mn.state.dot.tms.client.SonarState;
import us.mn.state.dot.tms.client.comm.ControllerForm;
import us.mn.state.dot.tms.client.proxy.SonarObjectForm;
import us.mn.state.dot.tms.client.roads.LocationPanel;
import us.mn.state.dot.tms.client.widget.FormPanel;
import us.mn.state.dot.tms.client.widget.IAction;
import us.mn.state.dot.tms.client.widget.WrapperComboBoxModel;
import us.mn.state.dot.tms.utils.I18N;

/**
 * WarningSignProperties is a dialog for entering and editing warning signs 
 *
 * @author Douglas Lau
 */
public class WarningSignProperties extends SonarObjectForm<WarningSign> {

	/** Location panel */
	private final LocationPanel loc_pnl;

	/** Notes text area */
	private final JTextArea notes_txt = new JTextArea(3, 24);

	/** Controller action */
	private final IAction controller = new IAction("controller") {
		protected void do_perform() {
			controllerPressed();
		}
	};

	/** Camera action */
	private final IAction camera = new IAction("camera") {
		protected void do_perform() {
			proxy.setCamera((Camera)camera_cbx.getSelectedItem());
		}
	};

	/** Camera combo box */
	private final JComboBox camera_cbx = new JComboBox();

	/** Sign message text area */
	private final JTextArea message_txt = new JTextArea(3, 24);

	/** Create a new warning sign form */
	public WarningSignProperties(Session s, WarningSign ws) {
		super(I18N.get("warning.sign") + ": ", s, ws);
		loc_pnl = new LocationPanel(s);
	}

	/** Get the SONAR type cache */
	@Override protected TypeCache<WarningSign> getTypeCache() {
		return state.getWarningSigns();
	}

	/** Initialize the widgets on the form */
	@Override protected void initialize() {
		super.initialize();
		JTabbedPane tab = new JTabbedPane();
		tab.add(I18N.get("location"), createLocationPanel());
		tab.add(I18N.get("device.setup"), createSetupPanel());
		add(tab);
		updateAttribute(null);
		if(canUpdate())
			createUpdateJobs();
		setBackground(Color.LIGHT_GRAY);
	}

	/** Dispose of the form */
	@Override protected void dispose() {
		loc_pnl.dispose();
		super.dispose();
	}

	/** Create the location panel */
	private JPanel createLocationPanel() {
		loc_pnl.setGeoLoc(proxy.getGeoLoc());
		loc_pnl.initialize();
		loc_pnl.addRow(I18N.get("device.notes"), notes_txt);
		loc_pnl.setCenter();
		loc_pnl.addRow(new JButton(controller));
		return loc_pnl;
	}

	/** Create jobs for updating */
	private void createUpdateJobs() {
		notes_txt.addFocusListener(new FocusLostJob(WORKER) {
			@Override public void perform() {
				proxy.setNotes(notes_txt.getText());
			}
		});
		message_txt.addFocusListener(new FocusLostJob(WORKER) {
			@Override public void perform() {
				proxy.setMessage(message_txt.getText());
			}
		});
	}

	/** Controller lookup button pressed */
	private void controllerPressed() {
		Controller c = proxy.getController();
		if(c != null)
			showForm(new ControllerForm(session, c));
	}

	/** Create the setup panel */
	private JPanel createSetupPanel() {
		FormPanel panel = new FormPanel(canUpdate());
		ListModel m = state.getCamCache().getCameraModel();
		camera_cbx.setAction(camera);
		camera_cbx.setModel(new WrapperComboBoxModel(m));
		panel.addRow(I18N.get("camera"), camera_cbx);
		panel.addRow(I18N.get("warning.sign.text"), message_txt);
		return panel;
	}

	/** Update one attribute on the form */
	@Override protected void doUpdateAttribute(String a) {
		if(a == null || a.equals("controller"))
			controller.setEnabled(proxy.getController() != null);
		if(a == null || a.equals("notes"))
			notes_txt.setText(proxy.getNotes());
		if(a == null || a.equals("camera"))
			camera_cbx.setSelectedItem(proxy.getCamera());
		if(a == null || a.equals("message"))
			message_txt.setText(proxy.getMessage());
	}
}
