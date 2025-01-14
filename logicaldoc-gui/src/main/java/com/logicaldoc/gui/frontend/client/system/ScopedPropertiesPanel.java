package com.logicaldoc.gui.frontend.client.system;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.logicaldoc.gui.common.client.data.PropertiesDS;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.GuiLog;
import com.logicaldoc.gui.common.client.util.LD;
import com.logicaldoc.gui.common.client.widgets.grid.RefreshableListGrid;
import com.logicaldoc.gui.frontend.client.services.ClusterService;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.SelectionStyle;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.grid.CellFormatter;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.grid.events.CellContextClickEvent;
import com.smartgwt.client.widgets.grid.events.CellContextClickHandler;
import com.smartgwt.client.widgets.layout.Layout;
import com.smartgwt.client.widgets.layout.VLayout;
import com.smartgwt.client.widgets.menu.Menu;
import com.smartgwt.client.widgets.menu.MenuItem;
import com.smartgwt.client.widgets.menu.events.MenuItemClickEvent;
import com.smartgwt.client.widgets.toolbar.ToolStrip;
import com.smartgwt.client.widgets.toolbar.ToolStripButton;

/**
 * This panel shows the list of configuration parameters.
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 6.5
 */
public class ScopedPropertiesPanel extends VLayout {

	private static final String SCOPE = "scope";
	private RefreshableListGrid list;

	public ScopedPropertiesPanel() {
		setWidth100();
	}

	@Override
	public void onDraw() {
		ToolStrip toolStrip = new ToolStrip();
		toolStrip.setHeight(20);
		toolStrip.setWidth100();
		toolStrip.addSpacer(2);
		ToolStripButton refresh = new ToolStripButton();
		refresh.setTitle(I18N.message("refresh"));
		toolStrip.addButton(refresh);
		refresh.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				list.refresh(new PropertiesDS());
			}
		});
		toolStrip.addFill();

		ListGridField name = new ListGridField("name", I18N.message("setting"), 210);
		name.setCanFilter(true);
		name.setCanSort(true);

		ListGridField value = new ListGridField("value", I18N.message("value"), 280);
		value.setCanFilter(true);
		value.setCanSort(true);

		ListGridField scope = new ListGridField(SCOPE, I18N.message(SCOPE), 80);
		scope.setCanFilter(true);
		scope.setCanSort(true);
		scope.setCellFormatter(new CellFormatter() {
			@Override
			public String format(Object value, ListGridRecord rec, int rowNum, int colNum) {
				return I18N.message(value.toString());
			}
		});

		list = new RefreshableListGrid();
		list.setEmptyMessage(I18N.message("notitemstoshow"));
		list.setCanExpandRecords(false);
		list.setShowRecordComponents(true);
		list.setShowRecordComponentsByCell(true);
		list.setCanFreezeFields(true);
		list.setAutoFetchData(true);
		list.setSelectionType(SelectionStyle.MULTIPLE);
		list.setFilterOnKeypress(true);
		list.setShowFilterEditor(true);
		list.setDataSource(new PropertiesDS());
		list.setFields(scope, name, value);

		Layout listing = new VLayout();
		listing.setAlign(Alignment.CENTER);
		listing.setHeight100();
		listing.addMember(list);

		list.addCellContextClickHandler(new CellContextClickHandler() {
			@Override
			public void onCellContextClick(CellContextClickEvent event) {
				showContextMenu();
				event.cancel();
			}
		});

		setMembers(toolStrip, listing);
	}

	private void showContextMenu() {
		Menu contextMenu = new Menu();

		ListGridRecord[] selection = list.getSelectedRecords();
		if (selection == null || selection.length == 0)
			return;
		final String[] selectedSettings = new String[selection.length];
		for (int i = 0; i < selection.length; i++) {
			selectedSettings[i] = selection[i].getAttribute("name");
		}

		MenuItem makeglobal = prepareMakeGlobalMenuItem(selectedSettings);

		MenuItem makelocal = prepareMakeLocalMenuItem(selectedSettings);

		contextMenu.setItems(makeglobal, makelocal);
		contextMenu.showContextMenu();
	}

	private MenuItem prepareMakeLocalMenuItem(final String[] selectedSettings) {
		MenuItem makelocal = new MenuItem();
		makelocal.setTitle(I18N.message("makelocal"));
		makelocal.addClickHandler(new com.smartgwt.client.widgets.menu.events.ClickHandler() {
			public void onClick(MenuItemClickEvent event) {
				LD.ask(I18N.message("question"), I18N.message("confirmmakelocal"), (Boolean yes) -> {
					if (Boolean.TRUE.equals(yes)) {
						ClusterService.Instance.get().makeLocal(selectedSettings, new AsyncCallback<Void>() {
							@Override
							public void onFailure(Throwable caught) {
								GuiLog.serverError(caught);
							}

							@Override
							public void onSuccess(Void result) {
								ListGridRecord[] selection = list.getSelectedRecords();
								for (int i = 0; i < selectedSettings.length; i++) {
									selection[i].setAttribute(SCOPE, "local");
									list.refreshRow(list.getRecordIndex(selection[i]));
								}
							}
						});
					}
				});
			}
		});
		return makelocal;
	}

	private MenuItem prepareMakeGlobalMenuItem(final String[] selectedSettings) {
		MenuItem makeglobal = new MenuItem();
		makeglobal.setTitle(I18N.message("makeglobal"));
		makeglobal.addClickHandler((MenuItemClickEvent event) -> {
			LD.ask(I18N.message("question"), I18N.message("confirmmakeglobal"), (Boolean yes) -> {
				if (Boolean.TRUE.equals(yes)) {
					ClusterService.Instance.get().makeGlobal(selectedSettings, new AsyncCallback<Void>() {
						@Override
						public void onFailure(Throwable caught) {
							GuiLog.serverError(caught);
						}

						@Override
						public void onSuccess(Void result) {
							ListGridRecord[] selection = list.getSelectedRecords();
							for (int i = 0; i < selectedSettings.length; i++) {
								selection[i].setAttribute(SCOPE, "global");
								list.refreshRow(list.getRecordIndex(selection[i]));
							}
						}
					});
				}
			});
		});
		return makeglobal;
	}
}