/*
 * IRIS -- Intelligent Roadway Information System
 * Copyright (C) 2000-2021  Minnesota Department of Transportation
 * Copyright (C) 2010 AHMCT, University of California, Davis
 * Copyright (C) 2017-2018  Iteris Inc.
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

import java.awt.BorderLayout;
import java.util.Iterator;
import java.util.Set;
import javax.swing.JPanel;
import us.mn.state.dot.tms.DeviceRequest;
import us.mn.state.dot.tms.DMS;
import us.mn.state.dot.tms.DMSHelper;
import us.mn.state.dot.tms.DmsMsgPriority;
import us.mn.state.dot.tms.Font;
import us.mn.state.dot.tms.Incident;
import us.mn.state.dot.tms.IncidentHelper;
import us.mn.state.dot.tms.MsgCombining;
import us.mn.state.dot.tms.QuickMessage;
import us.mn.state.dot.tms.QuickMessageHelper;
import us.mn.state.dot.tms.RasterGraphic;
import us.mn.state.dot.tms.SignConfig;
import us.mn.state.dot.tms.SignMessage;
import us.mn.state.dot.tms.SignMessageHelper;
import us.mn.state.dot.tms.SystemAttrEnum;
import us.mn.state.dot.tms.WordHelper;
import us.mn.state.dot.tms.client.Session;
import us.mn.state.dot.tms.client.proxy.ProxySelectionListener;
import us.mn.state.dot.tms.client.proxy.ProxySelectionModel;
import us.mn.state.dot.tms.client.widget.IOptionPane;
import us.mn.state.dot.tms.utils.I18N;
import us.mn.state.dot.tms.utils.MultiString;
import static us.mn.state.dot.tms.utils.MultiString.makeCombined;

/**
 * The DMSDispatcher is a GUI component for creating and deploying DMS messages.
 * It contains several other components and keeps their state synchronized.
 *
 * @see us.mn.state.dot.tms.SignMessage
 * @see us.mn.state.dot.tms.client.dms.DMSPanelPager
 * @see us.mn.state.dot.tms.client.dms.SignMessageComposer
 *
 * @author Erik Engstrom
 * @author Douglas Lau
 * @author Michael Darter
 */
public class DMSDispatcher extends JPanel {

	/** Check all the words in the specified MULTI string.
	 * @param ms Multi string to spell check.
	 * @return True to send the sign message else false to cancel. */
	static private boolean checkWords(String ms) {
		String msg = WordHelper.spellCheck(ms);
		String amsg = WordHelper.abbreviationCheck(ms);
		if (msg.isEmpty() && amsg.isEmpty())
			return true;
		if (msg.isEmpty())
			return confirmSend(amsg);
		String imsg = msg + amsg;
		if (WordHelper.spellCheckEnforced()) {
			IOptionPane.showError("word.spell.check", imsg);
			return false;
		} else if (WordHelper.spellCheckRecommend())
			return confirmSend(imsg);
		else
			return false;
	}

	/** Confirm sending message */
	static private boolean confirmSend(String imsg) {
		Object[] options = {
			I18N.get("dms.send.confirmation.ok"),
			I18N.get("dms.send.confirmation.cancel")
		};
		return IOptionPane.showOption("dms.send.confirmation.title",
			imsg, options);
	}

	/** User session */
	private final Session session;

	/** Selection model */
	private final ProxySelectionModel<DMS> sel_mdl;

	/** Selection listener */
	private final ProxySelectionListener sel_listener =
		new ProxySelectionListener()
	{
		public void selectionChanged() {
			updateSelected();
		}
	};

	/** Sign message creator */
	private final SignMessageCreator creator;

	/** Single sign tab */
	private final SingleSignTab singleTab;

	/** Message composer widget */
	private final SignMessageComposer composer;

	/** Composed MULTI string */
	private String multi = "";

	/** Selected quick message */
	private QuickMessage quick_msg = null;

	/** Linked incident */
	private Incident incident;

	/** Create a new DMS dispatcher */
	public DMSDispatcher(Session s, DMSManager manager) {
		super(new BorderLayout());
		session = s;
		DmsCache dms_cache = session.getSonarState().getDmsCache();
		creator = new SignMessageCreator(s);
		sel_mdl = manager.getSelectionModel();
		singleTab = new SingleSignTab(session, this);
		composer = new SignMessageComposer(session, this, manager);
		add(singleTab, BorderLayout.CENTER);
		add(composer, BorderLayout.SOUTH);
	}

	/** Initialize the dispatcher */
	public void initialize() {
		singleTab.initialize();
		sel_mdl.addProxySelectionListener(sel_listener);
		clearSelected();
	}

	/** Dispose of the dispatcher */
	public void dispose() {
		sel_mdl.removeProxySelectionListener(sel_listener);
		clearSelected();
		removeAll();
		singleTab.dispose();
		composer.dispose();
	}

	/** Set the sign message */
	public void setSignMessage(DMS dms) {
		String ms = DMSHelper.getOperatorMulti(dms);
		composer.setComposedMulti(ms);
		multi = composer.getComposedMulti();
		quick_msg = null;
		incident = DMSHelper.lookupIncident(dms);
	}

	/** Set the composed MULTI string.  This will update all the widgets
	 * on the dispatcher with the specified message. */
	public void setComposedMulti(String ms) {
		composer.setComposedMulti(ms);
		multi = ms;
		singleTab.setMessage();
	}

	/** Set the quick message */
	public void setQuickMessage(QuickMessage qm) {
		quick_msg = qm;
		unlinkIncident();
		if (!QuickMessageHelper.isMsgCombiningFirst(qm))
			setComposedMulti("");
		else
			singleTab.setMessage();
	}

	/** Get the composed MULTI string */
	private String getComposedMulti(DMS dms) {
		return DMSHelper.addMultiOverrides(dms, multi);
	}

	/** Get the preview MULTI string */
	public String getPreviewMulti(DMS dms, boolean combining) {
		Font of = dms.getOverrideFont();
		Integer f_num = (of != null) ? of.getNumber() : null;
		String ms = getComposedMulti(dms);
		if (new MultiString(ms).isBlank())
			return getPreviewBlank(f_num, combining);
		if (combining) {
			String quick = getQuickMsgFirst();
			if (quick != null)
				return makeCombined(quick, ms, f_num);
			String sched = getSchedCombining();
			if (sched != null)
				return makeCombined(sched, ms, f_num);
		}
		return ms;
	}

	/** Get preview with blank composed message */
	private String getPreviewBlank(Integer f_num, boolean combining) {
		String quick = getQuickMsg();
		if (combining) {
			String quick2 = getQuickMsgSecond();
			String sched = getSchedCombining();
			if (quick2 != null && sched != null)
				return makeCombined(sched, quick2, f_num);
			else if (quick != null)
				return quick;
			else if (sched != null)
				return sched;
		}
		return (quick != null) ? quick : "";
	}

	/** Get MULTI string from scheduled combining message */
	private String getSchedCombining() {
		DMS dms = getSingleSelection();
		if (dms != null) {
			SignMessage sm = dms.getMsgSched();
			if (SignMessageHelper.isMsgCombiningFirst(sm))
				return sm.getMulti();
		}
		return null;
	}

	/** Get quick message */
	private String getQuickMsg() {
		QuickMessage qm = quick_msg;
		return (qm != null) ? qm.getMulti() : null;
	}

	/** Get combining quick message (if first) */
	private String getQuickMsgFirst() {
		QuickMessage qm = quick_msg;
		return QuickMessageHelper.isMsgCombiningFirst(qm)
		      ? qm.getMulti()
		      : null;
	}

	/** Get combining quick message (if second) */
	private String getQuickMsgSecond() {
		QuickMessage qm = quick_msg;
		return QuickMessageHelper.isMsgCombiningSecond(qm)
		      ? qm.getMulti()
		      : null;
	}

	/** Get the single selected DMS */
	private DMS getSingleSelection() {
		return sel_mdl.getSingleSelection();
	}

	/** Get set of selected DMS with the same sign configuration */
	private Set<DMS> getValidSelected() {
		SignConfig sc = null;
		Set<DMS> sel = sel_mdl.getSelected();
		Iterator<DMS> it = sel.iterator();
		while (it.hasNext()) {
			DMS dms = it.next();
			if (sc != null && dms.getSignConfig() != sc)
				it.remove();
			else
				sc = dms.getSignConfig();
		}
		return sel;
	}

	/** Send the currently selected message */
	public void sendSelectedMessage() {
		if (shouldSendMessage()) {
			sendMessage();
			removeInvalidSelections();
		}
	}

	/** Remove all invalid selected DMS */
	private void removeInvalidSelections() {
		sel_mdl.setSelected(getValidSelected());
	}

	/** Determine if the message should be sent, which is a function
 	 * of spell checking options and send confirmation options.
	 * @return True to send the message else false to cancel. */
	private boolean shouldSendMessage() {
		if (WordHelper.spellCheckEnabled() && !checkWords(multi))
			return false;
		if (SystemAttrEnum.DMS_SEND_CONFIRMATION_ENABLE.getBoolean())
			return showConfirmDialog();
		else
			return true;
	}

	/** Show a message confirmation dialog.
	 * @return True if message should be sent. */
	private boolean showConfirmDialog() {
		String m = buildConfirmMsg();
		return m.isEmpty() || confirmSend(m);
	}

	/** Build a confirmation message containing all selected DMS.
	 * @return Confirmation message, or empty string if no selection. */
	private String buildConfirmMsg() {
		String sel = buildSelectedList();
		if (!sel.isEmpty()) {
			return I18N.get("dms.send.confirmation.msg") + " " +
				sel + "?";
		} else
			return "";
	}

	/** Build a string of selected DMS */
	private String buildSelectedList() {
		StringBuilder sb = new StringBuilder();
		for (DMS dms: getValidSelected()) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(dms.getName());
		}
		return sb.toString();
	}

	/** Send a new message to all selected DMS */
	private void sendMessage() {
		Set<DMS> signs = getValidSelected();
		if (signs.size() > 1)
			unlinkIncident();
		for (DMS dms: signs) {
			SignMessage sm = createMessage(dms);
			if (sm != null)
				dms.setMsgUser(sm);
			composer.updateMessageLibrary();
		}
		selectPreview(false);
	}

	/** Create a new message for the specified sign.
	 * @return A SignMessage from composer selection, or null on error. */
	private SignMessage createMessage(DMS dms) {
		SignConfig sc = dms.getSignConfig();
		if (sc == null)
			return null;
		String ms = getComposedMulti(dms);
		QuickMessage qm = quick_msg;
		if (new MultiString(ms).isBlank()) {
			if (qm != null) {
				String quick = qm.getMulti();
				MsgCombining mc = MsgCombining.fromOrdinal(
					qm.getMsgCombining());
				return createMessage(sc, quick, mc);
			} else
				return creator.createBlankMessage(sc);
		} else {
			if (QuickMessageHelper.isMsgCombiningFirst(qm)) {
				Font of = dms.getOverrideFont();
				Integer f_num = (of != null)
					? of.getNumber()
					: null;
				String quick = qm.getMulti();
				String combined = makeCombined(quick, ms, f_num);
				// Does combined message fit?
				if (DMSHelper.createRasters(dms, combined)
				    != null)
				{
					MsgCombining mc = MsgCombining.DISABLE;
					return createMessage(sc, combined, mc);
				}
			}
		}
		MsgCombining mc = MsgCombining.EITHER;
		Incident inc = incident;
		return (inc != null)
		      ? createMessage(sc, incident, ms, mc)
		      : createMessage(sc, ms, mc);
	}

	/** Create a new message using the specified MULTI */
	private SignMessage createMessage(SignConfig sc, String ms,
		MsgCombining mc)
	{
		Integer d = composer.getDuration();
		boolean be = composer.isBeaconEnabled();
		return creator.create(sc, ms, be, mc, d);
	}

	/** Create a new message linked to an incident */
	private SignMessage createMessage(SignConfig sc, Incident inc,
		String ms, MsgCombining mc)
	{
		String inc_orig = IncidentHelper.getOriginalName(inc);
		DmsMsgPriority prio = IncidentHelper.getPriority(inc);
		Integer d = composer.getDuration();
		return creator.create(sc, inc_orig, ms, prio, d);
	}

	/** Blank the select DMS */
	public void sendBlankMessage() {
		for (DMS dms: sel_mdl.getSelected()) {
			SignConfig sc = dms.getSignConfig();
			if (sc != null) {
				SignMessage sm = creator.createBlankMessage(sc);
				if (sm != null)
					dms.setMsgUser(sm);
			}
		}
	}

	/** Pixel test the selected DMS */
	public void pixelTestDms() {
		for (DMS dms: sel_mdl.getSelected()) {
			dms.setDeviceRequest(
				DeviceRequest.TEST_PIXELS.ordinal());
		}
	}

	/** Query status the selected DMS */
	public void queryStatusDms() {
		for (DMS dms: sel_mdl.getSelected()) {
			dms.setDeviceRequest(
				DeviceRequest.QUERY_STATUS.ordinal());
		}
	}

	/** Query the current message on all selected signs */
	public void queryMessage() {
		for (DMS dms: sel_mdl.getSelected()) {
			dms.setDeviceRequest(
				DeviceRequest.QUERY_MESSAGE.ordinal());
		}
		selectPreview(false);
	}

	/** Update the selected sign(s) */
	private void updateSelected() {
		Set<DMS> sel = sel_mdl.getSelected();
		if (sel.size() == 0)
			clearSelected();
		else {
			for (DMS dms: sel) {
				composer.setSign(dms);
				setSelected(dms);
				break;
			}
			if (sel.size() > 1) {
				singleTab.setSelected(null);
				setEnabled(true);
			}
		}
	}

	/** Clear the selection */
	private void clearSelected() {
		setEnabled(false);
		composer.setSign(null);
		setComposedMulti("");
		quick_msg = null;
		unlinkIncident();
		singleTab.setSelected(null);
	}

	/** Set a single selected DMS */
	private void setSelected(DMS dms) {
		setEnabled(DMSHelper.isActive(dms));
		singleTab.setSelected(dms);
	}

	/** Unlink incident */
	public void unlinkIncident() {
		incident = null;
	}

	/** Set the enabled status of the dispatcher */
	public void setEnabled(boolean e) {
		composer.setEnabled(e && canSend());
		if (e)
			selectPreview(false);
	}

	/** Select the preview mode */
	public void selectPreview(boolean p) {
		singleTab.selectPreview(p);
	}

	/** Can a message be sent to all selected DMS? */
	public boolean canSend() {
		Set<DMS> sel = getValidSelected();
		if (sel.isEmpty())
			return false;
		for (DMS dms: sel) {
			if (!canSend(dms))
				return false;
		}
		return true;
	}

	/** Can a message be sent to the specified DMS? */
	public boolean canSend(DMS dms) {
		return creator.canCreate() &&
		       isWritePermitted(dms, "msgUser");
	}

	/** Is DMS attribute write permitted? */
	private boolean isWritePermitted(DMS dms, String a) {
		return session.isWritePermitted(dms, a);
	}

	/** Can a device request be sent to all selected DMS? */
	public boolean canRequest() {
		boolean req = false;
		for (DMS dms: sel_mdl.getSelected()) {
			if (canRequest(dms))
				req = true;
			else
				return false;
		}
		return req;
	}

	/** Can a device request be sent to the specified DMS? */
	public boolean canRequest(DMS dms) {
		return isWritePermitted(dms, "deviceRequest");
	}
}
