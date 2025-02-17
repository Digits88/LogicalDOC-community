package com.logicaldoc.gui.frontend.client.document.grid;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.logicaldoc.gui.common.client.beans.GUIAutomationRoutine;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.GuiLog;
import com.logicaldoc.gui.common.client.widgets.ExtendedPropertiesPanel;
import com.logicaldoc.gui.frontend.client.services.AutomationService;
import com.smartgwt.client.types.HeaderControls;
import com.smartgwt.client.widgets.IButton;
import com.smartgwt.client.widgets.Window;
import com.smartgwt.client.widgets.layout.HLayout;
import com.smartgwt.client.widgets.layout.VLayout;

/**
 * This popup window is used get the automation routine's parameters values to
 * launch it.
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.5.3
 */
public class FillRoutineParams extends Window {
	private ExtendedPropertiesPanel propertiesPanel;

	private GUIAutomationRoutine routine;

	private long folderId;

	private long[] docIds;

	public FillRoutineParams(String title, GUIAutomationRoutine routine, long folderId, long[] docIds) {
		this.routine = routine;
		this.folderId = folderId;
		this.docIds = docIds;

		setHeaderControls(HeaderControls.HEADER_LABEL, HeaderControls.CLOSE_BUTTON);
		setTitle(title);
		setWidth(500);
		setHeight(400);
		setCanDragResize(true);
		setIsModal(true);
		setShowModalMask(true);
		centerInPage();

		IButton execute = new IButton();
		execute.setTitle(I18N.message("execute"));
		execute.setAutoFit(true);
		execute.addClickHandler(new com.smartgwt.client.widgets.events.ClickHandler() {
			@Override
			public void onClick(com.smartgwt.client.widgets.events.ClickEvent event) {
				onExecute();
			}
		});

		HLayout buttonsBar = new HLayout();
		buttonsBar.setWidth100();
		buttonsBar.setHeight(25);
		buttonsBar.setMembers(execute);

		propertiesPanel = new ExtendedPropertiesPanel(routine, null, true, true, false);

		VLayout layout = new VLayout();
		layout.setMargin(3);
		layout.setMembersMargin(3);
		layout.setMembers(propertiesPanel, buttonsBar);

		addItem(layout);
	}

	public void onExecute() {
		if (!propertiesPanel.validate())
			return;

		AutomationService.Instance.get().execute(routine, docIds, folderId, new AsyncCallback<Void>() {

			@Override
			public void onFailure(Throwable caught) {
				GuiLog.serverError(caught);
			}

			@Override
			public void onSuccess(Void arg0) {
				destroy();
			}
		});
	}
}