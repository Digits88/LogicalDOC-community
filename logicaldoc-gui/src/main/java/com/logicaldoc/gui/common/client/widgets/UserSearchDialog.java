package com.logicaldoc.gui.common.client.widgets;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.logicaldoc.gui.common.client.beans.GUIUser;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.GuiLog;
import com.logicaldoc.gui.common.client.services.SecurityService;
import com.logicaldoc.gui.common.client.util.ItemFactory;
import com.logicaldoc.gui.common.client.util.LD;
import com.logicaldoc.gui.common.client.widgets.grid.UserListGridField;
import com.smartgwt.client.types.HeaderControls;
import com.smartgwt.client.types.SelectionStyle;
import com.smartgwt.client.widgets.Window;
import com.smartgwt.client.widgets.events.DoubleClickEvent;
import com.smartgwt.client.widgets.events.DoubleClickHandler;
import com.smartgwt.client.widgets.form.fields.SelectItem;
import com.smartgwt.client.widgets.form.fields.TextItem;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.toolbar.ToolStrip;
import com.smartgwt.client.widgets.toolbar.ToolStripButton;

/**
 * This is a form used for quick user selection
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 6.6
 */
public class UserSearchDialog extends Window {
	private static final String LASTNAME = "lastname";

	private static final String FIRSTNAME = "firstname";

	private static final String USERNAME = "username";

	private ListGrid grid = new ListGrid();

	private UserSelector selector;

	private ListGridRecord[] lastResult = new ListGridRecord[0];

	public UserSearchDialog(UserSelector selector) {
		this.selector = selector;

		setHeaderControls(HeaderControls.HEADER_LABEL, HeaderControls.CLOSE_BUTTON);
		setTitle(I18N.message("users"));
		setIsModal(true);
		setShowModalMask(true);
		setCanDragResize(true);
		centerInPage();
		setWidth(500);
		setHeight(300);

		final TextItem username = ItemFactory.newTextItem(USERNAME, null);
		final SelectItem group = ItemFactory.newGroupSelector("group", "group");

		ToolStripButton search = new ToolStripButton(I18N.message("search"));
		search.setAutoFit(true);
		search.addClickHandler(new com.smartgwt.client.widgets.events.ClickHandler() {

			@Override
			public void onClick(com.smartgwt.client.widgets.events.ClickEvent event) {
				search(username.getValueAsString(), group.getValueAsString());
			}
		});

		final ToolStrip toolStrip = new ToolStrip();
		toolStrip.setHeight(20);
		toolStrip.setWidth100();
		toolStrip.addSpacer(2);
		toolStrip.addFormItem(username);
		toolStrip.addFormItem(group);
		toolStrip.addButton(search);

		UserListGridField avatar = new UserListGridField();
		ListGridField usernameField = new ListGridField(USERNAME, I18N.message(USERNAME));
		ListGridField nameField = new ListGridField(FIRSTNAME, I18N.message(FIRSTNAME));
		ListGridField lastnameField = new ListGridField(LASTNAME, I18N.message(LASTNAME));

		grid.setFields(avatar, usernameField, nameField, lastnameField);
		grid.setWidth100();
		grid.setHeight100();
		grid.setSelectionType(SelectionStyle.SINGLE);
		grid.setEmptyMessage(I18N.message("notitemstoshow"));
		grid.setCanFreezeFields(true);
		grid.setAutoFetchData(true);
		grid.setData(lastResult);

		grid.addDoubleClickHandler(new DoubleClickHandler() {
			@Override
			public void onDoubleClick(DoubleClickEvent event) {
				ListGridRecord selection = grid.getSelectedRecord();
				onSelect(selection.getAttributeAsLong("id"));
			}
		});

		addItem(toolStrip);
		addItem(grid);
	}

	protected void search(String username, String groupId) {
		SecurityService.Instance.get().searchUsers(username, groupId, new AsyncCallback<GUIUser[]>() {
			@Override
			public void onFailure(Throwable caught) {
				LD.clearPrompt();
				GuiLog.serverError(caught);
			}

			@Override
			public void onSuccess(GUIUser[] result) {
				lastResult = new ListGridRecord[result.length];
				for (int i = 0; i < result.length; i++) {
					GUIUser hit = result[i];
					ListGridRecord rec = new ListGridRecord();
					lastResult[i] = rec;
					rec.setAttribute("avatar", hit.getId());
					rec.setAttribute("id", hit.getId());
					rec.setAttribute(USERNAME, hit.getUsername());
					rec.setAttribute(FIRSTNAME, hit.getFirstName());
					rec.setAttribute(LASTNAME, hit.getName());
				}

				if (lastResult.length == 1) {
					onSelect(lastResult[0].getAttributeAsLong("id"));
				} else
					grid.setData(lastResult);
			}
		});
	}

	public ListGridRecord[] getLastResult() {
		return lastResult;
	}

	public void onSelect(long id) {
		selector.setValue(Long.toString(id));
		destroy();
	}
}