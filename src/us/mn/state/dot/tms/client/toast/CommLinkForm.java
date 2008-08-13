/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2008  Minnesota Department of Transportation
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
package us.mn.state.dot.tms.client.toast;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import us.mn.state.dot.sched.ActionJob;
import us.mn.state.dot.sched.ListSelectionJob;
import us.mn.state.dot.sonar.client.TypeCache;
import us.mn.state.dot.tms.CommLink;
import us.mn.state.dot.tms.Controller;
import us.mn.state.dot.tms.client.toast.AbstractForm;
import us.mn.state.dot.tms.client.TmsConnection;

/**
 * A form for displaying and editing comm links
 *
 * @author Douglas Lau
 */
public class CommLinkForm extends AbstractForm {

	/** Comm link table row height */
	static protected final int ROW_HEIGHT = 24;

	/** Frame title */
	static protected final String TITLE = "Comm Links";

	/** Tabbed pane */
	protected final JTabbedPane tab = new JTabbedPane();

	/** Table model for comm links */
	protected CommLinkModel model;

	/** Table to hold the comm link list */
	protected final JTable table = new JTable();

	/** Table model for controllers */
	protected ControllerModel cmodel;

	/** Table to hold controllers */
	protected final JTable ctable = new JTable();

	/** Comm link status */
	protected final JLabel link_status = new JLabel();

	/** Button to delete the selected comm link */
	protected final JButton del_button = new JButton("Delete Comm Link");

	/** Table to hold failed controllers */
	protected final JTable ftable = new JTable();

	/** Failed controller table model */
	protected FailedControllerModel fmodel;

	/** Button to show controller properties */
	protected final JButton ctr_props = new JButton("Properties");

	/** Button to delete the selected controller */
	protected final JButton del_ctr = new JButton("Delete Controller");

	/** Button to go to a failed controller */
	protected final JButton go_button = new JButton("Go");

	/** TMS connection */
	protected final TmsConnection connection;

	/** Comm Link type cache */
	protected final TypeCache<CommLink> cache;

	/** Controller type cache */
	protected final TypeCache<Controller> ccache;

	/** Create a new comm link form */
	public CommLinkForm(TmsConnection tc, TypeCache<CommLink> c,
		TypeCache<Controller> cc)
	{
		super(TITLE);
		connection = tc;
		cache = c;
		ccache = cc;
	}

	/** Initializze the widgets in the form */
	protected void initialize() {
		setLayout(new BorderLayout());
		model = new CommLinkModel(cache);
		fmodel = new FailedControllerModel(ccache);
		tab.add("All Links", createCommLinkPanel());
		tab.add("Failed Controllers", createFailedControllerPanel());
		add(tab);
		setBackground(Color.LIGHT_GRAY);
	}

	/** Dispose of the form */
	protected void dispose() {
		model.dispose();
		fmodel.dispose();
		if(cmodel != null)
			cmodel.dispose();
	}

	/** Create comm link panel */
	protected JPanel createCommLinkPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints bag = new GridBagConstraints();
		bag.insets.left = HGAP;
		bag.insets.right = HGAP;
		bag.insets.top = VGAP;
		bag.insets.bottom = VGAP;
		bag.gridwidth = 3;
		bag.fill = GridBagConstraints.BOTH;
		bag.weightx = 1;
		bag.weighty = 0.6f;
		final ListSelectionModel s = table.getSelectionModel();
		s.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		new ListSelectionJob(this, s) {
			public void perform() {
				if(!event.getValueIsAdjusting())
					selectCommLink();
			}
		};
		table.setModel(model);
		table.setAutoCreateColumnsFromModel(false);
		table.setColumnModel(model.createColumnModel());
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setRowHeight(ROW_HEIGHT);
		table.setPreferredScrollableViewportSize(new Dimension(
			table.getPreferredSize().width, ROW_HEIGHT * 8));
		JScrollPane pane = new JScrollPane(table);
		panel.add(pane, bag);
		bag.gridwidth = 1;
		bag.gridx = 0;
		bag.gridy = 1;
		bag.anchor = GridBagConstraints.WEST;
		bag.fill = GridBagConstraints.NONE;
		bag.weightx = 0;
		bag.weighty = 0;
		panel.add(new JLabel("Seleccted Comm Link:"), bag);
		bag.gridx = 1;
		panel.add(link_status, bag);
		bag.gridx = 2;
		bag.anchor = GridBagConstraints.EAST;
		del_button.setEnabled(false);
		panel.add(del_button, bag);
		new ActionJob(this, del_button) {
			public void perform() throws Exception {
				int row = s.getMinSelectionIndex();
				if(row >= 0)
					model.deleteRow(row);
			}
		};
		bag.gridx = 0;
		bag.gridy = 2;
		bag.gridwidth = 3;
		bag.anchor = GridBagConstraints.CENTER;
		bag.fill = GridBagConstraints.BOTH;
		bag.weightx = 1;
		bag.weighty = 1;
		final ListSelectionModel cs = ctable.getSelectionModel();
		cs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		new ListSelectionJob(this, cs) {
			public void perform() {
				if(!event.getValueIsAdjusting())
					selectController();
			}
		};
		ctable.setAutoCreateColumnsFromModel(false);
		ctable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		ctable.setRowHeight(ROW_HEIGHT);
		// NOTE: the width of the controller table is the same as the
		// comm link table on purpose.
		ctable.setPreferredScrollableViewportSize(new Dimension(
			table.getPreferredSize().width, ROW_HEIGHT * 16));
		pane = new JScrollPane(ctable);
		panel.add(pane, bag);
		bag.gridwidth = 1;
		bag.gridx = 0;
		bag.gridy = 3;
		bag.anchor = GridBagConstraints.WEST;
		bag.fill = GridBagConstraints.NONE;
		bag.weightx = 0;
		bag.weighty = 0;
		panel.add(new JLabel("Seleccted Controller:"), bag);
		bag.gridx = 1;
		bag.anchor = GridBagConstraints.EAST;
		ctr_props.setEnabled(false);
		panel.add(ctr_props, bag);
		new ActionJob(this, ctr_props) {
			public void perform() throws Exception {
				doPropertiesAction();
			}
		};
		ctable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if(e.getClickCount() > 1)
					ctr_props.doClick();
			}
		});
		bag.gridx = 2;
		del_ctr.setEnabled(false);
		panel.add(del_ctr, bag);
		new ActionJob(this, del_ctr) {
			public void perform() throws Exception {
				int row = cs.getMinSelectionIndex();
				if(row >= 0)
					cmodel.deleteRow(row);
			}
		};
		return panel;
	}

	/** Change the selected comm link */
	protected void selectCommLink() {
		int row = table.getSelectedRow();
		CommLink cl = model.getProxy(row);
		if(cl != null)
			link_status.setText(cl.getStatus());
		else
			link_status.setText("");
		del_button.setEnabled(row >= 0 && !model.isLastRow(row));
		del_ctr.setEnabled(false);
		ControllerModel old_model = cmodel;
		cmodel = new ControllerModel(ccache, cl);
		ctable.setModel(cmodel);
		ctable.setColumnModel(cmodel.createColumnModel());
		if(old_model != null)
			old_model.dispose();
	}

	/** Change the selected controller */
	protected void selectController() {
		int row = ctable.getSelectedRow();
		Controller c = cmodel.getProxy(row);
		boolean exists = row >= 0 && !cmodel.isLastRow(row);
		ctr_props.setEnabled(exists);
		// FIXME: should check for "deletable"
		del_ctr.setEnabled(exists);
	}

	/** Do the action for controller properties button */
	protected void doPropertiesAction() throws Exception {
		ListSelectionModel cs = ctable.getSelectionModel();
		int row = cs.getMinSelectionIndex();
		if(row >= 0) {
			Controller c = cmodel.getProxy(row);
			connection.getDesktop().show(new ControllerForm(
				connection, c));
		}
	}

	/** Create the failed controller panel */
	protected JPanel createFailedControllerPanel() {
		JPanel panel = new JPanel(new FlowLayout());
		panel.setBorder(BORDER);
		final ListSelectionModel s = ftable.getSelectionModel();
		s.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		ftable.setModel(fmodel);
		ftable.setAutoCreateColumnsFromModel(false);
		ftable.setColumnModel(fmodel.createColumnModel());
		ftable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		ftable.setRowHeight(ROW_HEIGHT);
		ftable.setPreferredScrollableViewportSize(new Dimension(
			ftable.getPreferredSize().width, ROW_HEIGHT * 26));
		JScrollPane pane = new JScrollPane(ftable);
		panel.add(pane);
		new ActionJob(this, go_button) {
			public void perform() throws Exception {
				goFailedController();
			}
		};
		ftable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if(e.getClickCount() > 1)
					go_button.doClick();
			}
		});
		panel.add(go_button);
		return panel;
	}

	/** Go to the failed controller (on the main tab) */
	protected void goFailedController() {
		int row = ftable.getSelectedRow();
		Controller c = fmodel.getProxy(row);
		if(c != null)
			goController(c);
	}

	/** Go to the specified controller (on the main tab) */
	protected void goController(Controller c) {
		CommLink l = c.getCommLink();
		int row = model.getRow(l);
		if(row >= 0) {
			ListSelectionModel s = table.getSelectionModel();
			s.setSelectionInterval(row, row);
			table.scrollRectToVisible(
				table.getCellRect(row, 0, true));
			tab.setSelectedIndex(0);
		}
	}
}
