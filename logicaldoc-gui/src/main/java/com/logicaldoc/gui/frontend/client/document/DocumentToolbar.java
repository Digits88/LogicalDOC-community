package com.logicaldoc.gui.frontend.client.document;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.logicaldoc.gui.common.client.Constants;
import com.logicaldoc.gui.common.client.CookiesManager;
import com.logicaldoc.gui.common.client.Feature;
import com.logicaldoc.gui.common.client.Menu;
import com.logicaldoc.gui.common.client.Session;
import com.logicaldoc.gui.common.client.beans.GUICalendarEvent;
import com.logicaldoc.gui.common.client.beans.GUIDocument;
import com.logicaldoc.gui.common.client.beans.GUIFolder;
import com.logicaldoc.gui.common.client.beans.GUIReminder;
import com.logicaldoc.gui.common.client.beans.GUIUser;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.GuiLog;
import com.logicaldoc.gui.common.client.observer.FolderController;
import com.logicaldoc.gui.common.client.observer.FolderObserver;
import com.logicaldoc.gui.common.client.services.SecurityService;
import com.logicaldoc.gui.common.client.util.AwesomeFactory;
import com.logicaldoc.gui.common.client.util.DocUtil;
import com.logicaldoc.gui.common.client.util.Util;
import com.logicaldoc.gui.common.client.widgets.DropSpotPopup;
import com.logicaldoc.gui.frontend.client.calendar.CalendarEventDialog;
import com.logicaldoc.gui.frontend.client.document.form.AddDocumentUsingForm;
import com.logicaldoc.gui.frontend.client.document.grid.DocumentsGrid;
import com.logicaldoc.gui.frontend.client.document.signature.DigitalSignatureDialog;
import com.logicaldoc.gui.frontend.client.document.stamp.StampDialog;
import com.logicaldoc.gui.frontend.client.document.update.UpdateDialog;
import com.logicaldoc.gui.frontend.client.folder.FolderNavigator;
import com.logicaldoc.gui.frontend.client.subscription.SubscriptionDialog;
import com.smartgwt.client.types.SelectionType;
import com.smartgwt.client.util.SC;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.toolbar.ToolStrip;
import com.smartgwt.client.widgets.toolbar.ToolStripButton;

/**
 * The toolbar to handle some documents aspects
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 6.0
 */
public class DocumentToolbar extends ToolStrip implements FolderObserver {

	protected ToolStripButton refresh = AwesomeFactory.newToolStripButton("sync-alt", "refresh");

	protected ToolStripButton download = AwesomeFactory.newToolStripButton("download", "download");

	protected ToolStripButton saveLayout = AwesomeFactory.newToolStripButton("save", "savelayoutinuserprofile");

	protected ToolStripButton pdf = AwesomeFactory.newToolStripButton("file-pdf", "exportpdf");

	protected ToolStripButton convert = AwesomeFactory.newToolStripButton("copy", "convert");

	protected ToolStripButton add = AwesomeFactory.newToolStripButton("upload", "adddocuments");

	protected ToolStripButton addForm = AwesomeFactory.newToolStripButton("file-alt", "addform");

	protected ToolStripButton subscribe = AwesomeFactory.newToolStripButton("envelope", "subscribe");

	protected ToolStripButton dropSpot = AwesomeFactory.newToolStripButton("eye-dropper", "dropspot");

	protected ToolStripButton scan = AwesomeFactory.newToolStripButton("scanner-image", "scandocument");

	protected ToolStripButton archive = AwesomeFactory.newToolStripButton("archive", "sendtoexparchive");

	protected ToolStripButton startWorkflow = AwesomeFactory.newToolStripButton("cogs", "startworkflow");

	protected ToolStripButton addCalendarEvent = AwesomeFactory.newToolStripButton("calendar-plus", "newcalendarevent");

	protected ToolStripButton list = AwesomeFactory.newToolStripButton("bars", "list");

	protected ToolStripButton gallery = AwesomeFactory.newToolStripButton("images", "gallery");

	protected ToolStripButton office = AwesomeFactory.newToolStripButton("windows", "editwithoffice");

	protected ToolStripButton bulkUpdate = AwesomeFactory.newToolStripButton("edit", "bulkupdate");

	protected ToolStripButton stamp = AwesomeFactory.newToolStripButton("tint", "stamp");

	protected ToolStripButton sign = AwesomeFactory.newToolStripButton("badge-check", "sign");

	protected ToolStripButton bulkCheckout = AwesomeFactory.newToolStripButton("check", "bulkcheckout");

	protected ToolStripButton filter = AwesomeFactory.newToolStripButton("filter", "filter");

	protected ToolStripButton print = AwesomeFactory.newToolStripButton("print", "print");

	protected ToolStripButton export = AwesomeFactory.newToolStripButton("angle-double-down", "export");

	protected ToolStripButton togglePreview = AwesomeFactory.newToolStripButton("toggle-on", "closepreview");

	protected GUIDocument document;

	private static DocumentToolbar instance = null;

	public static DocumentToolbar get() {
		if (instance == null)
			instance = new DocumentToolbar();
		return instance;
	}

	private DocumentToolbar() {
		setWidth100();
		setHeight(27);

		GUIFolder folder = FolderController.get().getCurrentFolder();
		boolean downloadEnabled = folder != null && folder.isDownload();
		boolean writeEnabled = folder != null && folder.isWrite();
		boolean signEnabled = folder != null && folder.hasPermission(Constants.PERMISSION_SIGN);

		prepareButtons(downloadEnabled, writeEnabled, signEnabled);
		update(null, folder);

		FolderController.get().addObserver(this);
	}

	protected void prepareButtons(boolean downloadEnabled, boolean writeEnabled, boolean signEnabled) {

		addRefresh();

		addSeparator();

		addDownload();

		addPdf();

		addConvert();

		addOffice(downloadEnabled, writeEnabled);

		addSeparator();

		addUpload();

		addDropSpot();

		addScan();

		addForm();

		addSubscribe();

		addArchive();

		if (Feature.visible(Feature.BULK_UPDATE) || Feature.visible(Feature.BULK_CHECKOUT))
			addSeparator();

		addBulkUpdate();

		addBulkCheckout();

		addStamp();

		addDigitalSignature();

		addStartWorkflow();

		addCalendar();

		addSeparator();

		addFilter();

		addPrint();

		addExport();

		addSaveLayout();

		addSeparator();

		addList();

		addGallery();

		addSeparator();

		addTogglePreview();

		int mode = DocumentsGrid.MODE_LIST;
		if (CookiesManager.get(CookiesManager.COOKIE_DOCSLIST_MODE) != null
				&& !CookiesManager.get(CookiesManager.COOKIE_DOCSLIST_MODE).equals(""))
			mode = Integer.parseInt(CookiesManager.get(CookiesManager.COOKIE_DOCSLIST_MODE));
		if (mode == DocumentsGrid.MODE_LIST)
			list.setSelected(true);
		else
			gallery.setSelected(true);
	}

	private void addTogglePreview() {
		try {
			// Retrieve the saved preview width
			String w = CookiesManager.get(CookiesManager.COOKIE_DOCSLIST_PREV_W);
			if (Integer.parseInt(w) <= 0) {
				togglePreview.setTitle(AwesomeFactory.getIconHtml("toggle-off"));
				togglePreview.setTooltip(I18N.message("openpreview"));
			}
		} catch (Throwable t) {
			// Nothing to do
		}
		togglePreview.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				if (DocumentsPanel.get().getPreviewPanel().isVisible()
						&& DocumentsPanel.get().getPreviewPanel().getWidth() > 1) {
					DocumentsPanel.get().getPreviewPanel().setWidth(0);
					togglePreview.setTitle(AwesomeFactory.getIconHtml("toggle-off"));
					togglePreview.setTooltip(I18N.message("openpreview"));
				} else {
					try {
						String w = CookiesManager.get(CookiesManager.COOKIE_DOCSLIST_PREV_W);
						DocumentsPanel.get().getPreviewPanel().setWidth(Integer.parseInt(w));
					} catch (Throwable t) {
						DocumentsPanel.get().getPreviewPanel().setWidth(350);
					}
					DocumentsPanel.get().getPreviewPanel().setDocument(document);
					togglePreview.setTitle(AwesomeFactory.getIconHtml("toggle-on"));
					togglePreview.setTooltip(I18N.message("closepreview"));
				}
			}
		});
		addButton(togglePreview);
	}

	private void addFilter() {
		filter.setActionType(SelectionType.CHECKBOX);
		addButton(filter);
		filter.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				DocumentsPanel.get().toggleFilters();
			}
		});
	}

	private void addGallery() {
		gallery.setActionType(SelectionType.RADIO);
		gallery.setRadioGroup("mode");
		gallery.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				if (FolderController.get().getCurrentFolder() != null)
					CookiesManager.save(CookiesManager.COOKIE_DOCSLIST_MODE, DocumentsGrid.MODE_GALLERY);
				DocumentsPanel.get().refresh(DocumentsGrid.MODE_GALLERY);
			}
		});
		gallery.setDisabled(FolderController.get().getCurrentFolder() == null
				|| !Session.get().getConfigAsBoolean("gui.galleryenabled"));
		gallery.setVisible(Session.get().getConfigAsBoolean("gui.galleryenabled"));
		addButton(gallery);
	}

	private void addList() {
		list.setActionType(SelectionType.RADIO);
		list.setRadioGroup("mode");
		list.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				CookiesManager.save(CookiesManager.COOKIE_DOCSLIST_MODE, DocumentsGrid.MODE_LIST);
				DocumentsPanel.get().refresh(DocumentsGrid.MODE_LIST);
			}
		});
		list.setDisabled(FolderController.get().getCurrentFolder() == null);
		addButton(list);
	}

	private void addSaveLayout() {
		saveLayout.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				saveGridState();
			}
		});
		addButton(saveLayout);
	}

	private void addExport() {
		if (Feature.visible(Feature.EXPORT_CSV)) {
			addButton(export);
			export.addClickHandler(new ClickHandler() {
				@Override
				public void onClick(ClickEvent event) {
					DocumentsPanel.get().export();
				}
			});
			if (!Feature.enabled(Feature.EXPORT_CSV)) {
				setFeatureDisabled(export);
			}
		}
	}

	private void addPrint() {
		addButton(print);
		print.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				DocumentsPanel.get().printPreview();
			}
		});
	}

	private void addCalendar() {
		if (Feature.visible(Feature.CALENDAR)) {
			addSeparator();
			addButton(addCalendarEvent);
			if (!Feature.enabled(Feature.CALENDAR))
				setFeatureDisabled(addCalendarEvent);

			addCalendarEvent.addClickHandler(new ClickHandler() {
				@Override
				public void onClick(ClickEvent event) {
					DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();

					GUIDocument[] docs = grid.getSelectedDocuments();

					GUICalendarEvent calEvent = new GUICalendarEvent();
					calEvent.setCreator(Session.get().getUser().getFullName());
					calEvent.setCreatorId(Session.get().getUser().getId());
					GUIUser user = new GUIUser();
					user.setId(Session.get().getUser().getId());
					user.setUsername(Session.get().getUser().getUsername());
					user.setFirstName(Session.get().getUser().getFirstName());
					user.setName(Session.get().getUser().getName());
					calEvent.addParticipant(user);

					if (docs != null && docs.length > 0) {
						calEvent.setDocuments(docs);
						calEvent.setTitle(Util.getBaseName(docs[0].getFileName()));
						calEvent.setType(docs[0].getTemplate());
					}

					calEvent.addReminder(new GUIReminder(0, GUIReminder.TIME_UNIT_MINUTE));
					CalendarEventDialog eventDialog = new CalendarEventDialog(calEvent, null);
					eventDialog.show();
				}
			});
		}
	}

	private void addStartWorkflow() {
		if (Feature.visible(Feature.WORKFLOW)) {
			addSeparator();
			addButton(startWorkflow);
			if (!Feature.enabled(Feature.WORKFLOW))
				setFeatureDisabled(startWorkflow);

			startWorkflow.addClickHandler(new ClickHandler() {
				@Override
				public void onClick(ClickEvent event) {
					DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();
					if (grid.getSelectedCount() == 0)
						return;

					StartWorkflowDialog workflowDialog = new StartWorkflowDialog(grid.getSelectedIds());
					workflowDialog.show();
				}
			});
		}
	}

	private void addDigitalSignature() {
		if (Feature.visible(Feature.DIGITAL_SIGNATURE)) {
			addButton(sign);
			sign.setTooltip(I18N.message("sign"));
			if (!Feature.enabled(Feature.DIGITAL_SIGNATURE))
				setFeatureDisabled(sign);

			sign.addClickHandler(new ClickHandler() {
				@Override
				public void onClick(ClickEvent event) {
					DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();
					if (grid.getSelectedCount() == 0)
						return;

					DigitalSignatureDialog dialog = new DigitalSignatureDialog(grid.getSelectedIds());
					dialog.show();
				}
			});
		}
	}

	private void addStamp() {
		if (Feature.visible(Feature.STAMP)) {
			addSeparator();
			addButton(stamp);
			stamp.setTooltip(I18N.message("stamp"));
			if (!Feature.enabled(Feature.STAMP))
				setFeatureDisabled(stamp);

			stamp.addClickHandler(new ClickHandler() {
				@Override
				public void onClick(ClickEvent event) {
					DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();
					if (grid.getSelectedCount() == 0)
						return;

					StampDialog dialog = new StampDialog(grid);
					dialog.show();
				}
			});
		}
	}

	private void addBulkCheckout() {
		if (Feature.visible(Feature.BULK_CHECKOUT)) {
			addButton(bulkCheckout);
			if (!Feature.enabled(Feature.BULK_CHECKOUT))
				setFeatureDisabled(bulkCheckout);

			bulkCheckout.addClickHandler(new ClickHandler() {
				@Override
				public void onClick(ClickEvent event) {
					DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();
					if (grid.getSelectedCount() == 0)
						return;

					GUIDocument docs[] = grid.getSelectedDocuments();
					List<Long> unlockedIds = new ArrayList<>();
					for (GUIDocument doc : docs)
						if (doc.getStatus() == 0 && doc.getImmutable() == 0)
							unlockedIds.add(doc.getId());
					Util.openBulkCheckout(unlockedIds);
				}
			});
		}
	}

	private void addBulkUpdate() {
		if (Feature.visible(Feature.BULK_UPDATE)) {
			addButton(bulkUpdate);
			if (!Feature.enabled(Feature.BULK_UPDATE))
				setFeatureDisabled(bulkUpdate);

			bulkUpdate.addClickHandler((ClickEvent event) -> {
				DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();
				if (grid.getSelectedCount() == 0)
					return;

				GUIFolder currentFolder = FolderController.get().getCurrentFolder();
				GUIDocument metadata = currentFolder.newDocument();
				new UpdateDialog(grid.getSelectedIds(), metadata, UpdateDialog.BULKUPDATE, false).show();
			});
		}
	}

	private void addArchive() {
		if (Feature.visible(Feature.IMPEX)) {
			addSeparator();
			addButton(archive);
			if (!Feature.enabled(Feature.IMPEX))
				setFeatureDisabled(archive);

			archive.addClickHandler((ClickEvent event) -> {
				DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();
				if (grid.getSelectedCount() == 0)
					return;

				new SendToArchiveDialog(grid.getSelectedIds(), true).show();
			});
		}
	}

	private void addSubscribe() {
		if (Feature.visible(Feature.AUDIT)) {
			addSeparator();
			addButton(subscribe);
			if (!Feature.enabled(Feature.AUDIT))
				setFeatureDisabled(subscribe);

			subscribe.setDisabled(true);
			subscribe.addClickHandler((ClickEvent event) -> {
				DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();
				if (grid.getSelectedCount() == 0)
					return;

				new SubscriptionDialog(null, grid.getSelectedIds()).show();
			});
		}
	}

	private void addForm() {
		if (Feature.visible(Feature.FORM)) {
			addButton(addForm);
			if (!Feature.enabled(Feature.FORM))
				setFeatureDisabled(addForm);

			addForm.addClickHandler((ClickEvent event) -> new AddDocumentUsingForm().show());
		}
	}

	private void addScan() {
		if (Feature.visible(Feature.SCAN) && Menu.enabled(Menu.SCAN)) {
			addButton(scan);
			if (!Feature.enabled(Feature.SCAN))
				setFeatureDisabled(scan);

			scan.addClickHandler((ClickEvent event) -> Util.openScan());
		}
	}

	private void addUpload() {
		add.addClickHandler((ClickEvent event) -> {
			DocumentsUploader uploader = new DocumentsUploader();
			uploader.show();
			event.cancel();
		});
		addButton(add);
	}

	private void addDropSpot() {
		if (Feature.visible(Feature.DROP_SPOT) && Menu.enabled(Menu.DROP_SPOT)) {
			addButton(dropSpot);
			if (!Feature.enabled(Feature.DROP_SPOT))
				setFeatureDisabled(dropSpot);

			dropSpot.addClickHandler((ClickEvent event) -> DropSpotPopup.openDropSpot());
		}
	}

	private void addOffice(boolean downloadEnabled, boolean writeEnabled) {
		if (Feature.visible(Feature.OFFICE)) {
			addButton(office);
			office.setTooltip(I18N.message("editwithoffice"));
			office.setTitle("<i class='fab fa-windows fa-lg fa-lg' aria-hidden='true'></i>");
			office.addClickHandler((ClickEvent event) -> {
				if (document == null)
					return;
				Util.openEditWithOffice(document.getId());
			});

			if (!Feature.enabled(Feature.OFFICE) || (document != null && !Util.isOfficeFile(document.getFileName()))
					|| !downloadEnabled || !writeEnabled)
				office.setDisabled(true);
			else
				office.setDisabled(false);

			if (!Feature.enabled(Feature.OFFICE))
				setFeatureDisabled(office);
		}
	}

	private void addConvert() {
		if (Feature.visible(Feature.FORMAT_CONVERSION)) {
			addButton(convert);
			convert.setTooltip(I18N.message("convert"));
			if (!Feature.enabled(Feature.PDF))
				setFeatureDisabled(convert);

			convert.addClickHandler((ClickEvent event) -> {
				new ConversionDialog(document).show();
				event.cancel();
			});
		}
	}

	private void addPdf() {
		if (Feature.visible(Feature.PDF)) {
			addButton(pdf);
			pdf.setTooltip(I18N.message("exportpdf"));
			if (!Feature.enabled(Feature.PDF))
				setFeatureDisabled(pdf);

			pdf.addClickHandler((ClickEvent event) -> {
				DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();
				if (grid.getSelectedCount() == 0)
					return;

				long[] selection = grid.getSelectedIds();
				if (selection.length == 1) {
					DocUtil.downloadPdfConversion(document.getId(), document.getVersion());
				} else {
					String url = Util.contextPath() + "convertpdf?open=true&docId=";
					for (long id : selection)
						url += Long.toString(id) + ",";
					Util.download(url);
				}
			});
		}
	}

	private void addDownload() {
		addButton(download);
		download.addClickHandler((ClickEvent event) -> {
			DocumentsGrid grid = DocumentsPanel.get().getDocumentsGrid();
			if (grid.getSelectedCount() == 0)
				return;

			GUIDocument[] selection = grid.getSelectedDocuments();
			if (selection.length == 1) {
				long id = selection[0].getId();
				DocUtil.download(id, null);
			} else {
				String url = GWT.getHostPageBaseURL() + "zip-export?folderId="
						+ FolderController.get().getCurrentFolder().getId();
				for (GUIDocument rec : selection) {
					if (rec.isPasswordProtected()) {
						SC.warn(I18N.message("somedocsprotected"));
						break;
					}
					url += "&docId=" + rec.getId();
				}
				Util.download(url);
			}
		});
	}

	private void addRefresh() {
		refresh.addClickHandler((ClickEvent event) -> {
			if (FolderController.get().getCurrentFolder() != null)
				FolderNavigator.get().selectFolder(FolderController.get().getCurrentFolder().getId());
		});
		refresh.setDisabled(FolderController.get().getCurrentFolder() == null);
		addButton(refresh);
	}

	/**
	 * Updates the toolbar state on the basis of the passed document and/or
	 * folder
	 * 
	 * @param document the currently selected document
	 * @param folder the currently selected folder
	 */
	public void update(final GUIDocument document, GUIFolder folder) {
		try {
			if (folder == null)
				folder = FolderController.get().getCurrentFolder();

			boolean downloadEnabled = folder != null && folder.isDownload();
			boolean writeEnabled = folder != null && folder.isWrite();
			boolean signEnabled = folder != null && folder.hasPermission(Constants.PERMISSION_SIGN);

			this.document = document;

			if (document != null) {
				updateUsingDocument(document, downloadEnabled, writeEnabled, signEnabled);
			} else {
				download.setDisabled(true);
				pdf.setDisabled(true);
				convert.setDisabled(true);
				subscribe.setDisabled(true);
				archive.setDisabled(true);
				startWorkflow.setDisabled(true);
				bulkUpdate.setDisabled(true);
				bulkCheckout.setDisabled(true);
				stamp.setDisabled(true);
				sign.setDisabled(true);
				office.setDisabled(true);
				addForm.setDisabled(true);
			}

			updateUsingFolder(document, folder);
		} catch (Throwable t) {
			// Nothing to do
		}
	}

	private void updateUsingFolder(final GUIDocument document, GUIFolder folder) {
		if (folder != null) {
			refresh.setDisabled(false);
			add.setDisabled(!folder.hasPermission(Constants.PERMISSION_WRITE));
			dropSpot.setDisabled(!folder.hasPermission(Constants.PERMISSION_WRITE)
					|| !folder.hasPermission(Constants.PERMISSION_IMPORT) || !Feature.enabled(Feature.DROP_SPOT));
			addForm.setDisabled(!folder.hasPermission(Constants.PERMISSION_WRITE) || !Feature.enabled(Feature.FORM));
			scan.setDisabled(!folder.hasPermission(Constants.PERMISSION_WRITE) || !Feature.enabled(Feature.SCAN));
			archive.setDisabled(document == null || !folder.hasPermission(Constants.PERMISSION_ARCHIVE)
					|| !Feature.enabled(Feature.IMPEX));
			startWorkflow.setDisabled(document == null || !folder.hasPermission(Constants.PERMISSION_WORKFLOW)
					|| !Feature.enabled(Feature.WORKFLOW));
			addCalendarEvent.setDisabled(
					!folder.hasPermission(Constants.PERMISSION_CALENDAR) || !Feature.enabled(Feature.CALENDAR));
			list.setDisabled(false);
			gallery.setDisabled(false);
			togglePreview.setDisabled(false);
		} else {
			refresh.setDisabled(true);
			add.setDisabled(true);
			addForm.setDisabled(true);
			office.setDisabled(true);
			scan.setDisabled(true);
			archive.setDisabled(true);
			startWorkflow.setDisabled(true);
			bulkUpdate.setDisabled(true);
			bulkCheckout.setDisabled(true);
			dropSpot.setDisabled(true);
			addCalendarEvent.setDisabled(true);
			list.setDisabled(false);
			gallery.setDisabled(false);
			togglePreview.setDisabled(false);
		}
	}

	private void setFeatureDisabled(ToolStripButton button) {
		button.setDisabled(true);
		button.setTooltip(I18N.message("featuredisabled"));
	}

	private void updateUsingDocument(final GUIDocument document, boolean downloadEnabled, boolean writeEnabled,
			boolean signEnabled) {

		download.setDisabled(!downloadEnabled);
		office.setDisabled(!downloadEnabled);
		pdf.setDisabled(!Feature.enabled(Feature.PDF) || !downloadEnabled);
		convert.setDisabled(!Feature.enabled(Feature.FORMAT_CONVERSION));
		subscribe.setDisabled(!Feature.enabled(Feature.AUDIT));
		bulkUpdate.setDisabled(!Feature.enabled(Feature.BULK_UPDATE) || !writeEnabled);
		bulkCheckout.setDisabled(!Feature.enabled(Feature.BULK_CHECKOUT) || !downloadEnabled || !writeEnabled);
		stamp.setDisabled(!Feature.enabled(Feature.STAMP) || !writeEnabled);
		sign.setDisabled(!Feature.enabled(Feature.DIGITAL_SIGNATURE) || !writeEnabled || !signEnabled);

		boolean isOfficeFile = false;
		if (document.getFileName() != null)
			isOfficeFile = Util.isOfficeFile(document.getFileName());
		else if (document.getType() != null)
			isOfficeFile = Util.isOfficeFileType(document.getType());

		office.setDisabled(!Feature.enabled(Feature.OFFICE) || !isOfficeFile || !downloadEnabled || !writeEnabled);
		if (document.getStatus() != Constants.DOC_UNLOCKED
				&& !Session.get().getUser().isMemberOf(Constants.GROUP_ADMIN)) {
			if (document.getLockUserId() != null
					&& Session.get().getUser().getId() != document.getLockUserId().longValue())
				office.setDisabled(true);
		}
	}

	@Override
	public void onFolderSelected(GUIFolder folder) {
		update(null, folder);
	}

	@Override
	public void onFolderChanged(GUIFolder folder) {
		// Nothing to do
	}

	@Override
	public void onFolderDeleted(GUIFolder folder) {
		// Nothing to do
	}

	@Override
	public void onFolderCreated(GUIFolder folder) {
		// Nothing to do
	}

	@Override
	public void onFolderMoved(GUIFolder folder) {
		// Nothing to do
	}

	@Override
	public void onFolderBeginEditing(GUIFolder folder) {
		// Nothing to do
	}

	@Override
	public void onFolderCancelEditing(GUIFolder folder) {
		// Nothing to do
	}

	private void saveGridState() {
		Session.get().getUser().setDocsGrid(DocumentsPanel.get().getDocsGridViewState());
		SecurityService.Instance.get().saveInterfaceSettings(Session.get().getUser(), new AsyncCallback<GUIUser>() {

			@Override
			public void onFailure(Throwable e) {
				GuiLog.serverError(e);
			}

			@Override
			public void onSuccess(GUIUser usr) {
				GuiLog.info(I18N.message("settingssaved"));
			}
		});
	}
}