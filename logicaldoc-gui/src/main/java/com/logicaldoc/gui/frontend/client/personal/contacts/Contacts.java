package com.logicaldoc.gui.frontend.client.personal.contacts;

import java.util.Arrays;
import java.util.Map;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.logicaldoc.gui.common.client.Feature;
import com.logicaldoc.gui.common.client.beans.GUIContact;
import com.logicaldoc.gui.common.client.data.ContactsDS;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.GuiLog;
import com.logicaldoc.gui.common.client.util.GridUtil;
import com.logicaldoc.gui.common.client.util.GridUtil.EndScrollListener;
import com.logicaldoc.gui.common.client.util.LD;
import com.logicaldoc.gui.common.client.util.ValuesCallback;
import com.logicaldoc.gui.common.client.widgets.GroupSelectorCombo;
import com.logicaldoc.gui.common.client.widgets.UserSelectorCombo;
import com.logicaldoc.gui.frontend.client.services.ContactService;
import com.logicaldoc.gui.frontend.client.services.DocumentService;
import com.smartgwt.client.types.HeaderControls;
import com.smartgwt.client.types.SelectionAppearance;
import com.smartgwt.client.types.SelectionStyle;
import com.smartgwt.client.types.SortDirection;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.events.DoubleClickEvent;
import com.smartgwt.client.widgets.events.DoubleClickHandler;
import com.smartgwt.client.widgets.events.ResizedEvent;
import com.smartgwt.client.widgets.events.ResizedHandler;
import com.smartgwt.client.widgets.form.fields.FormItem;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.grid.events.CellContextClickEvent;
import com.smartgwt.client.widgets.grid.events.CellContextClickHandler;
import com.smartgwt.client.widgets.menu.Menu;
import com.smartgwt.client.widgets.menu.MenuItem;
import com.smartgwt.client.widgets.menu.events.MenuItemClickEvent;
import com.smartgwt.client.widgets.toolbar.ToolStrip;
import com.smartgwt.client.widgets.toolbar.ToolStripButton;

/**
 * This panel shows the list of the user's contacts.
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 6.0
 */
public class Contacts extends com.smartgwt.client.widgets.Window {

	private static final String EMAIL = "email";

	private ListGrid list;

	private static Contacts instance = null;

	public static Contacts get() {
		if (instance == null)
			instance = new Contacts();
		return instance;
	}

	private Contacts() {
		super();

		setHeaderControls(HeaderControls.HEADER_LABEL, HeaderControls.CLOSE_BUTTON);
		setTitle(I18N.message("contacts"));
		setWidth(500);
		setHeight(400);
		setIsModal(true);
		setShowModalMask(true);
		setCanDragResize(true);
		centerInPage();
		setAutoSize(true);

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
				refresh();
			}
		});

		ToolStripButton selectAll = new ToolStripButton();
		selectAll.setTitle(I18N.message("selectall"));
		toolStrip.addButton(selectAll);
		selectAll.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				GridUtil.scrollGrid(list, new EndScrollListener() {

					@Override
					public void endScroll(ListGrid listGrid) {
						list.selectAllRecords();
					}
				});
			}
		});

		ToolStripButton add = new ToolStripButton();
		add.setTitle(I18N.message("addcontact"));
		toolStrip.addButton(add);
		add.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				ContactDetails details = new ContactDetails(new GUIContact(), Contacts.this);
				details.show();
			}
		});

		ToolStripButton importCsv = new ToolStripButton();
		importCsv.setTitle(I18N.message("iimport"));
		importCsv.setTooltip(I18N.message("importfromcsv"));
		importCsv.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				DocumentService.Instance.get().cleanUploadedFileFolder(new AsyncCallback<Void>() {

					@Override
					public void onFailure(Throwable caught) {
						GuiLog.serverError(caught);
					}

					@Override
					public void onSuccess(Void arg0) {
						ContactsUploader uploader = new ContactsUploader();
						uploader.show();
					}
				});
			}
		});

		toolStrip.addSeparator();
		toolStrip.addButton(importCsv);

		ToolStripButton export = new ToolStripButton();
		export.setTitle(I18N.message("export"));
		export.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				GridUtil.exportCSV(list, true);
			}
		});
		if (Feature.visible(Feature.EXPORT_CSV)) {
			toolStrip.addButton(export);
			if (!Feature.enabled(Feature.EXPORT_CSV)) {
				export.setDisabled(true);
				export.setTooltip(I18N.message("featuredisabled"));
			}
		}

		toolStrip.addFill();
		addItem(toolStrip);

		prepareGrid();
		addItem(list);

		list.fetchData();
	}

	private void prepareGrid() {
		ListGridField id = new ListGridField("id", 50);
		id.setHidden(true);

		ListGridField email = new ListGridField(EMAIL, I18N.message(EMAIL));
		email.setWidth("*");
		email.setCanFilter(true);

		ListGridField firstName = new ListGridField("firstName", I18N.message("firstname"));
		firstName.setCanFilter(true);
		firstName.setWidth(80);

		ListGridField lastName = new ListGridField("lastName", I18N.message("lastname"));
		lastName.setCanFilter(true);
		lastName.setWidth(80);

		ListGridField company = new ListGridField("company", I18N.message("company"));
		company.setCanFilter(true);
		company.setWidth(110);

		ListGridField phone = new ListGridField("phone", I18N.message("phone"));
		phone.setCanFilter(true);
		phone.setWidth(100);
		phone.setHidden(true);

		ListGridField mobile = new ListGridField("mobile", I18N.message("cell"));
		mobile.setCanFilter(true);
		mobile.setWidth(100);
		mobile.setHidden(true);

		ListGridField address = new ListGridField("address", I18N.message("address"));
		address.setCanFilter(true);
		address.setWidth(150);
		address.setHidden(true);

		list = new ListGrid();
		list.setWidth100();
		list.setHeight(getHeight());
		list.setEmptyMessage(I18N.message("notitemstoshow"));
		list.setCanFreezeFields(true);
		list.setAutoFetchData(true);
		list.setSelectionType(SelectionStyle.SIMPLE);
		list.setSelectionAppearance(SelectionAppearance.CHECKBOX);
		list.setFilterOnKeypress(true);
		list.setShowFilterEditor(true);
		list.setDataSource(new ContactsDS());
		list.setFields(id, email, firstName, lastName, company, phone, mobile, address);
		list.sort(EMAIL, SortDirection.ASCENDING);

		list.addCellContextClickHandler(new CellContextClickHandler() {
			@Override
			public void onCellContextClick(CellContextClickEvent event) {
				showContextMenu();
				event.cancel();
			}
		});

		list.addDoubleClickHandler(new DoubleClickHandler() {
			@Override
			public void onDoubleClick(DoubleClickEvent event) {
				onEdit();
			}
		});

		addResizedHandler(new ResizedHandler() {

			@Override
			public void onResized(ResizedEvent event) {
				list.setHeight(getHeight() - 68);
			}
		});
	}

	public void refresh() {
		list.setDataSource(new ContactsDS());
		list.fetchData();
		GridUtil.scrollGrid(list, null);
	}

	private void showContextMenu() {
		Menu contextMenu = new Menu();

		final ListGridRecord[] selection = list.getSelectedRecords();
		if (selection == null || selection.length == 0)
			return;
		final long[] ids = new long[selection.length];
		for (int i = 0; i < selection.length; i++) {
			ids[i] = Long.parseLong(selection[i].getAttribute("id"));
		}

		MenuItem delete = new MenuItem();
		delete.setTitle(I18N.message("ddelete"));
		delete.addClickHandler(new com.smartgwt.client.widgets.menu.events.ClickHandler() {
			public void onClick(MenuItemClickEvent event) {
				LD.ask(I18N.message("question"), I18N.message("confirmdelete"), (Boolean value) -> {
					if (Boolean.TRUE.equals(value))
						ContactService.Instance.get().delete(ids, new AsyncCallback<Void>() {
							@Override
							public void onFailure(Throwable caught) {
								GuiLog.serverError(caught);
							}

							@Override
							public void onSuccess(Void result) {
								list.removeSelectedData();
								list.deselectAllRecords();
							}
						});
				});
			}
		});

		MenuItem edit = new MenuItem();
		edit.setTitle(I18N.message("edit"));
		edit.addClickHandler((MenuItemClickEvent event) -> onEdit());

		MenuItem share = new MenuItem();
		share.setTitle(I18N.message("share"));
		share.addClickHandler((MenuItemClickEvent event) -> onShare());

		contextMenu.setItems(edit, share, delete);
		contextMenu.showContextMenu();
	}

	protected void onShare() {
		ListGridRecord[] selection = list.getSelectedRecords();

		long[] ids = new long[selection.length];
		for (int i = 0; i < ids.length; i++)
			ids[i] = selection[i].getAttributeAsLong("id");

		final UserSelectorCombo usersSelector = new UserSelectorCombo("users", "users", null, true, true);

		final GroupSelectorCombo groupsSelector = new GroupSelectorCombo("groups", "groups");

		LD.askForValues("sharecontacts", null, Arrays.asList(new FormItem[] { usersSelector, groupsSelector }), 350,
				new ValuesCallback() {
					@Override
					public void execute(String value) {
						// Nothing to do
					}

					@Override
					public void execute(Map<String, Object> values) {
						LD.contactingServer();
						ContactService.Instance.get().shareContacts(ids, usersSelector.getUserIds(),
								groupsSelector.getGroupIds(), new AsyncCallback<Void>() {

									@Override
									public void onFailure(Throwable caught) {
										LD.clearPrompt();
										GuiLog.serverError(caught);
									}

									@Override
									public void onSuccess(Void arg0) {
										LD.clearPrompt();
									}
								});
					}
				});
	}

	private void onEdit() {
		final ListGridRecord[] selection = list.getSelectedRecords();
		ContactService.Instance.get().load(Long.parseLong(selection[0].getAttribute("id")),
				new AsyncCallback<GUIContact>() {
					@Override
					public void onFailure(Throwable caught) {
						GuiLog.serverError(caught);
					}

					@Override
					public void onSuccess(GUIContact result) {
						if (result != null) {
							ContactDetails dialog = new ContactDetails(result, Contacts.this);
							dialog.show();
						}
					}
				});
	}

	@Override
	protected void onDraw() {
		GridUtil.scrollGrid(list, null);
	}
}