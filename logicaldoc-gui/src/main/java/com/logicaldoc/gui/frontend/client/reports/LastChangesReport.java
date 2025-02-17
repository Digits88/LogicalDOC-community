package com.logicaldoc.gui.frontend.client.reports;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.logicaldoc.gui.common.client.Feature;
import com.logicaldoc.gui.common.client.beans.GUIDocument;
import com.logicaldoc.gui.common.client.beans.GUIHistory;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.GuiLog;
import com.logicaldoc.gui.common.client.util.DocUtil;
import com.logicaldoc.gui.common.client.util.GridUtil;
import com.logicaldoc.gui.common.client.util.ItemFactory;
import com.logicaldoc.gui.common.client.util.LD;
import com.logicaldoc.gui.common.client.widgets.FolderSelector;
import com.logicaldoc.gui.common.client.widgets.InfoPanel;
import com.logicaldoc.gui.common.client.widgets.grid.DateListGridField;
import com.logicaldoc.gui.common.client.widgets.grid.FileNameListGridField;
import com.logicaldoc.gui.common.client.widgets.grid.UserListGridField;
import com.logicaldoc.gui.common.client.widgets.preview.PreviewPopup;
import com.logicaldoc.gui.frontend.client.administration.AdminPanel;
import com.logicaldoc.gui.frontend.client.document.DocumentsPanel;
import com.logicaldoc.gui.frontend.client.services.DocumentService;
import com.logicaldoc.gui.frontend.client.services.SystemService;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.SelectionStyle;
import com.smartgwt.client.types.SortDirection;
import com.smartgwt.client.types.TitleOrientation;
import com.smartgwt.client.widgets.events.DoubleClickEvent;
import com.smartgwt.client.widgets.events.DoubleClickHandler;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.ValuesManager;
import com.smartgwt.client.widgets.form.fields.ButtonItem;
import com.smartgwt.client.widgets.form.fields.DateItem;
import com.smartgwt.client.widgets.form.fields.SelectItem;
import com.smartgwt.client.widgets.form.fields.SpinnerItem;
import com.smartgwt.client.widgets.form.fields.TextItem;
import com.smartgwt.client.widgets.form.fields.events.ClickEvent;
import com.smartgwt.client.widgets.form.fields.events.ClickHandler;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.grid.events.CellContextClickEvent;
import com.smartgwt.client.widgets.grid.events.CellContextClickHandler;
import com.smartgwt.client.widgets.layout.HStack;
import com.smartgwt.client.widgets.layout.Layout;
import com.smartgwt.client.widgets.layout.VLayout;
import com.smartgwt.client.widgets.menu.Menu;
import com.smartgwt.client.widgets.menu.MenuItem;

/**
 * This panel is used to show the last changes events.
 * 
 * @author Matteo Caruso - LogicalDOC
 * @since 6.0
 */
public class LastChangesReport extends AdminPanel {

	private static final String NAME = "name";

	private static final String DISPLAYMAX = "displaymax";

	private static final String REASON = "reason";

	private static final String COMMENT = "comment";

	private static final String USERNAME = "username";

	private static final String GEOLOCATION = "geolocation";

	private static final String DEVICE = "device";

	private static final String FOLDER_ID = "folderId";

	private static final String DOC_ID = "docId";

	private static final String FOLDER_STR = "folder";

	private static final String USER_ID = "userId";

	private static final String EVENT = "event";

	private static final String TILL_DATE = "tillDate";

	private static final String FROM_DATE = "fromDate";

	private Layout search = new VLayout();

	private Layout results = new VLayout();

	private VLayout lastchanges = new VLayout();

	private ValuesManager vm = new ValuesManager();

	private ListGrid histories;

	private InfoPanel infoPanel;

	private FolderSelector folder;

	public LastChangesReport() {
		super("lastchanges");
	}

	@Override
	public void onDraw() {
		HStack formsLayout = new HStack(10);

		DynamicForm form = new DynamicForm();
		form.setValuesManager(vm);
		form.setAlign(Alignment.LEFT);
		form.setTitleOrientation(TitleOrientation.LEFT);
		form.setNumCols(8);
		form.setWrapItemTitles(false);

		// Username
		SelectItem user = ItemFactory.newUserSelector("user", "user", null, false, false);
		user.setEndRow(true);

		// From
		DateItem fromDate = ItemFactory.newDateItem(FROM_DATE, "from");
		fromDate.setColSpan(4);

		// To
		DateItem tillDate = ItemFactory.newDateItem(TILL_DATE, "till");
		tillDate.setEndRow(true);
		tillDate.setColSpan(4);

		// Session ID
		TextItem sessionId = ItemFactory.newTextItem("sid", null);
		sessionId.setWidth(250);
		sessionId.setColSpan(8);
		sessionId.setEndRow(true);

		folder = new FolderSelector(null, true);
		folder.setWidth(200);
		folder.setEndRow(true);
		folder.setColSpan(8);

		// Max results
		SpinnerItem displayMax = ItemFactory.newSpinnerItem(DISPLAYMAX, 100, 5, null);
		displayMax.setHint(I18N.message("elements"));
		displayMax.setStep(10);
		displayMax.setStartRow(true);

		ButtonItem searchButton = new ButtonItem();
		searchButton.setTitle(I18N.message("search"));
		searchButton.setAutoFit(true);
		searchButton.setEndRow(false);
		searchButton.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				onSearch();
			}
		});

		ButtonItem resetButton = new ButtonItem();
		resetButton.setTitle(I18N.message("reset"));
		resetButton.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				vm.clearValues();
			}
		});
		resetButton.setColSpan(2);
		resetButton.setAutoFit(true);
		resetButton.setEndRow(true);
		resetButton.setStartRow(false);

		ButtonItem print = new ButtonItem();
		print.setTitle(I18N.message("print"));
		print.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				GridUtil.print(histories);
			}
		});
		print.setAutoFit(true);
		print.setEndRow(true);
		print.setStartRow(false);

		ButtonItem export = new ButtonItem();
		export.setTitle(I18N.message("export"));
		export.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				GridUtil.exportCSV(histories, false);
			}
		});
		if (!Feature.enabled(Feature.EXPORT_CSV)) {
			export.setDisabled(true);
			export.setTooltip(I18N.message("featuredisabled"));
		}
		export.setAutoFit(true);
		export.setStartRow(true);
		export.setEndRow(false);

		if (Feature.visible(Feature.EXPORT_CSV))
			form.setItems(user, sessionId, fromDate, tillDate, folder, displayMax, searchButton, resetButton, export,
					print);
		else
			form.setItems(user, sessionId, fromDate, tillDate, folder, displayMax, searchButton, resetButton, print);

		DynamicForm eventForm = new DynamicForm();
		eventForm.setValuesManager(vm);
		eventForm.setAlign(Alignment.LEFT);
		eventForm.setTitleOrientation(TitleOrientation.LEFT);
		eventForm.setNumCols(2);
		eventForm.setColWidths(1, "*");

		// Event
		SelectItem event = ItemFactory.newEventsSelector(EVENT, I18N.message(EVENT), null, true, true, true, true);
		event.setColSpan(2);
		event.setEndRow(true);

		eventForm.setItems(event);

		formsLayout.addMember(form);
		formsLayout.addMember(eventForm);
		formsLayout.setMembersMargin(80);

		search.setMembersMargin(10);
		search.setMembers(formsLayout);
		search.setHeight("30%");
		search.setShowResizeBar(true);
		search.setWidth100();
		search.setMargin(10);

		ListGridField eventField = new ListGridField(EVENT, I18N.message(EVENT), 200);
		eventField.setCanFilter(true);

		ListGridField date = new DateListGridField("date", "date");

		ListGridField userField = new UserListGridField("user", USER_ID, "user");
		userField.setCanFilter(true);
		userField.setAlign(Alignment.CENTER);

		FileNameListGridField name = new FileNameListGridField(NAME, "icon", I18N.message(NAME), 150);
		name.setCanFilter(true);

		ListGridField folder = new ListGridField(FOLDER_STR, I18N.message(FOLDER_STR), 100);
		folder.setCanFilter(true);

		ListGridField sid = new ListGridField("sid", I18N.message("sid"), 250);
		sid.setCanFilter(true);
		sid.setAlign(Alignment.CENTER);

		ListGridField docId = new ListGridField(DOC_ID, I18N.message("documentid"), 100);
		docId.setCanFilter(true);
		docId.setHidden(true);

		ListGridField folderId = new ListGridField(FOLDER_ID, I18N.message("folderid"), 100);
		folderId.setCanFilter(true);
		folderId.setHidden(true);

		ListGridField userId = new ListGridField(USER_ID, I18N.message("userid"), 100);
		userId.setCanFilter(true);
		userId.setHidden(true);

		ListGridField ip = new ListGridField("ip", I18N.message("ip"), 100);
		ip.setCanFilter(true);
		ip.setHidden(true);

		ListGridField device = new ListGridField(DEVICE, I18N.message(DEVICE), 200);
		device.setHidden(true);
		ListGridField geolocation = new ListGridField(GEOLOCATION, I18N.message(GEOLOCATION), 200);
		geolocation.setHidden(true);

		ListGridField username = new ListGridField(USERNAME, I18N.message(USERNAME), 100);
		username.setCanFilter(true);
		username.setHidden(true);

		ListGridField comment = new ListGridField(COMMENT, I18N.message(COMMENT), 200);
		comment.setCanFilter(true);
		comment.setHidden(true);

		ListGridField reason = new ListGridField(REASON, I18N.message(REASON), 200);
		reason.setCanFilter(true);
		reason.setHidden(true);

		histories = new ListGrid();
		histories.setEmptyMessage(I18N.message("notitemstoshow"));
		histories.setWidth100();
		histories.setHeight100();
		histories.setFields(eventField, date, userField, name, folder, sid, docId, folderId, userId, username, ip,
				device, geolocation, comment, reason);
		histories.setSelectionType(SelectionStyle.SINGLE);
		histories.setShowRecordComponents(true);
		histories.setShowRecordComponentsByCell(true);
		histories.setCanFreezeFields(true);
		histories.setFilterOnKeypress(true);
		histories.setAutoFetchData(true);
		histories.sort("date", SortDirection.DESCENDING);
		histories.addCellContextClickHandler(new CellContextClickHandler() {
			@Override
			public void onCellContextClick(CellContextClickEvent event) {
				showContextMenu();
				event.cancel();
			}
		});

		results.addMember(histories);

		lastchanges.addMember(search, 0);

		// Prepare a panel containing a title and the documents list
		infoPanel = new InfoPanel("");
		lastchanges.addMember(infoPanel, 1);

		lastchanges.addMember(results, 2);

		body.setMembers(lastchanges);

		histories.addDoubleClickHandler(new DoubleClickHandler() {
			@Override
			public void onDoubleClick(DoubleClickEvent event) {
				vm.setValue("sid", histories.getSelectedRecord().getAttributeAsString("sid"));
			}
		});
	}

	/**
	 * Gets the option items to choose events types
	 * 
	 * @return an array of select items
	 */
	public SelectItem[] getEventTypes() {
		List<SelectItem> items = new ArrayList<>();

		return items.toArray(new SelectItem[0]);
	}

	@SuppressWarnings("unchecked")
	private void onSearch() {
		histories.setData(new ListGridRecord[0]);

		final Map<String, Object> values = (Map<String, Object>) vm.getValues();

		if (Boolean.FALSE.equals(vm.validate()))
			return;

		String[] eventValues = getEvents(values);

		Long userId = getUserId(values);

		Date fromValue = null;
		if (values.get(FROM_DATE) != null)
			fromValue = (Date) values.get(FROM_DATE);
		Date tillValue = null;
		if (values.get(TILL_DATE) != null)
			tillValue = (Date) values.get(TILL_DATE);

		String sid = null;
		if (values.get("sid") != null)
			sid = (String) values.get("sid");

		int displayMaxValue = getDisplayMax(values);

		doSearch(eventValues, userId, fromValue, tillValue, sid, displayMaxValue);
	}

	private String[] getEvents(final Map<String, Object> values) {
		String[] eventValues = new String[0];
		if (values.get(EVENT) != null) {
			String buf = values.get(EVENT).toString().trim().toLowerCase();
			buf = buf.replace('[', ' ');
			buf = buf.replace(']', ' ');
			buf = buf.replace(" ", "");
			eventValues = buf.split(",");
		}
		return eventValues;
	}

	private Long getUserId(final Map<String, Object> values) {
		Long userId = null;
		if (values.get("user") != null) {
			if (values.get("user") instanceof Long)
				userId = (Long) values.get("user");
			else
				userId = Long.parseLong(values.get("user").toString());
		}
		return userId;
	}

	private int getDisplayMax(final Map<String, Object> values) {
		int displayMaxValue = 0;
		if (values.get(DISPLAYMAX) != null) {
			if (values.get(DISPLAYMAX) instanceof Integer)
				displayMaxValue = (Integer) values.get(DISPLAYMAX);
			else
				displayMaxValue = Integer.parseInt((String) values.get(DISPLAYMAX));
		}
		return displayMaxValue;
	}

	private void doSearch(String[] eventValues, Long userId, Date fromValue, Date tillValue, String sid,
			int displayMaxValue) {
		LD.contactingServer();
		SystemService.Instance.get().search(userId, fromValue, tillValue, displayMaxValue, sid, eventValues,
				folder.getFolderId(), new AsyncCallback<GUIHistory[]>() {

					@Override
					public void onFailure(Throwable caught) {
						LD.clearPrompt();
						GuiLog.serverError(caught);
					}

					@Override
					public void onSuccess(GUIHistory[] result) {
						LD.clearPrompt();

						if (result != null && result.length > 0) {
							ListGridRecord[] records = new ListGridRecord[result.length];
							for (int i = 0; i < result.length; i++) {
								ListGridRecord rec = new ListGridRecord();
								rec.setAttribute(EVENT, I18N.message(result[i].getEvent()));
								rec.setAttribute("date", result[i].getDate());
								rec.setAttribute("user", result[i].getUsername());
								rec.setAttribute(NAME, result[i].getFileName());
								rec.setAttribute(FOLDER_STR, result[i].getPath());
								rec.setAttribute("sid", result[i].getSessionId());
								rec.setAttribute(DOC_ID, result[i].getDocId());
								rec.setAttribute(FOLDER_ID, result[i].getFolderId());
								rec.setAttribute(USER_ID, result[i].getUserId());
								rec.setAttribute("ip", result[i].getIp());
								rec.setAttribute(DEVICE, result[i].getDevice());
								rec.setAttribute(GEOLOCATION, result[i].getGeolocation());
								rec.setAttribute(USERNAME, result[i].getUserLogin());
								rec.setAttribute(COMMENT, result[i].getComment());
								rec.setAttribute(REASON, result[i].getReason());
								rec.setAttribute("icon", result[i].getIcon());
								records[i] = rec;
							}
							histories.setData(records);
						}
						lastchanges.removeMember(infoPanel);
						infoPanel = new InfoPanel("");
						infoPanel.setMessage(I18N.message("showelements", Integer.toString(histories.getTotalRows())));
						lastchanges.addMember(infoPanel, 1);
					}
				});
	}

	private void showContextMenu() {
		Menu contextMenu = new Menu();

		ListGridRecord selection = histories.getSelectedRecord();
		if (selection == null)
			return;

		final Long docId = selection.getAttributeAsLong(DOC_ID) != null && selection.getAttributeAsLong(DOC_ID) != 0L
				? selection.getAttributeAsLong(DOC_ID)
				: null;
		final Long folderId = selection.getAttributeAsLong(FOLDER_ID) != null
				&& selection.getAttributeAsLong(FOLDER_ID) != 0L ? selection.getAttributeAsLong(FOLDER_ID) : null;
		if (docId == null && folderId == null)
			return;

		MenuItem openInFolder = new MenuItem();
		openInFolder.setTitle(I18N.message("openinfolder"));
		openInFolder.addClickHandler(event -> DocumentsPanel.get().openInFolder(docId));

		MenuItem preview = preparePreviewItem(docId);

		MenuItem download = new MenuItem();
		download.setTitle(I18N.message("download"));
		download.addClickHandler(event -> DocUtil.download(docId, null));

		MenuItem openFolder = new MenuItem();
		openFolder.setTitle(I18N.message("openfolder"));
		openFolder.addClickHandler(event -> DocumentsPanel.get().openInFolder(folderId, null));

		if (docId != null)
			contextMenu.setItems(download, preview, openInFolder);
		else if (folderId != null)
			contextMenu.setItems(openFolder);
		contextMenu.showContextMenu();
	}

	private MenuItem preparePreviewItem(final Long docId) {
		MenuItem preview = new MenuItem();
		preview.setTitle(I18N.message("preview"));
		if (docId != null)
			preview.addClickHandler(
					event -> DocumentService.Instance.get().getById(docId, new AsyncCallback<GUIDocument>() {

						@Override
						public void onFailure(Throwable caught) {
							GuiLog.serverError(caught);
						}

						@Override
						public void onSuccess(GUIDocument doc) {
							PreviewPopup iv = new PreviewPopup(doc);
							iv.show();
						}
					}));
		return preview;
	}
}