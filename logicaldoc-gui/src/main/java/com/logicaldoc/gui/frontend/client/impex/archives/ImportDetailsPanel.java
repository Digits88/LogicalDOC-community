package com.logicaldoc.gui.frontend.client.impex.archives;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.logicaldoc.gui.common.client.beans.GUIArchive;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.GuiLog;
import com.logicaldoc.gui.common.client.widgets.EditingTabSet;
import com.logicaldoc.gui.frontend.client.services.ImpexService;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.form.fields.events.ChangedEvent;
import com.smartgwt.client.widgets.layout.HLayout;
import com.smartgwt.client.widgets.layout.Layout;
import com.smartgwt.client.widgets.layout.VLayout;
import com.smartgwt.client.widgets.tab.Tab;

/**
 * This panel collects all documents details
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 6.0
 */
public class ImportDetailsPanel extends VLayout {

	private Layout settingsTabPanel;

	private ImportSettingsPanel settingsPanel;

	private EditingTabSet tabSet;

	private GUIArchive archive;

	private ImportArchivesList listPanel;

	public ImportDetailsPanel(GUIArchive archive, ImportArchivesList listPanel) {
		super();
		this.listPanel = listPanel;
		this.archive = archive;
		setHeight100();
		setWidth100();
		setMembersMargin(10);

		tabSet = new EditingTabSet(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				onSave();
			}
		}, new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				ImpexService.Instance.get().load(getArchive().getId(), new AsyncCallback<GUIArchive>() {
					@Override
					public void onFailure(Throwable caught) {
						GuiLog.serverError(caught);
					}

					@Override
					public void onSuccess(GUIArchive archive) {
						setArchive(archive);
						tabSet.hideSave();
					}
				});
			}
		});

		Tab versionsTab = new Tab(I18N.message("settings"));
		settingsTabPanel = new HLayout();
		settingsTabPanel.setWidth100();
		settingsTabPanel.setHeight100();
		versionsTab.setPane(settingsTabPanel);
		tabSet.addTab(versionsTab);

		addMember(tabSet);

		refresh();
	}

	private void refresh() {
		/*
		 * Prepare the import settings tab
		 */
		if (settingsPanel != null) {
			settingsPanel.destroy();
			if (Boolean.TRUE.equals(settingsTabPanel.contains(settingsPanel)))
				settingsTabPanel.removeMember(settingsPanel);
		}
		settingsPanel = new ImportSettingsPanel(archive, (ChangedEvent event) -> onModified());
		settingsTabPanel.addMember(settingsPanel);
	}

	public void onModified() {
		tabSet.displaySave();
	}

	public void onSave() {
		if (settingsPanel.validate()) {
			ImpexService.Instance.get().save(archive, new AsyncCallback<GUIArchive>() {
				@Override
				public void onFailure(Throwable caught) {
					GuiLog.serverError(caught);
				}

				@Override
				public void onSuccess(GUIArchive result) {
					tabSet.hideSave();
					listPanel.updateRecord(result);
				}
			});
		}
	}

	public GUIArchive getArchive() {
		return archive;
	}

	public void setArchive(GUIArchive archive) {
		this.archive = archive;
		refresh();
	}
}