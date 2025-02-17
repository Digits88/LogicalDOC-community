package com.logicaldoc.gui.login.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.log.GuiLog;
import com.logicaldoc.gui.login.client.services.LoginService;
import com.smartgwt.client.types.HeaderControls;
import com.smartgwt.client.widgets.Window;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.fields.ButtonItem;
import com.smartgwt.client.widgets.form.fields.StaticTextItem;
import com.smartgwt.client.widgets.form.fields.events.ClickEvent;
import com.smartgwt.client.widgets.form.fields.events.ClickHandler;

/**
 * An utility to generate passwords
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.8.2
 */
public class PasswordGenerator extends Window {

	private DynamicForm form = new DynamicForm();

	private StaticTextItem password;

	private ButtonItem generate;

	private String username;

	public PasswordGenerator(String username) {
		this.username = username;
		setTitle(I18N.message("passwordgenerator"));
		setHeaderControls(HeaderControls.HEADER_LABEL, HeaderControls.CLOSE_BUTTON);
		setAutoSize(true);
		setCanDragResize(true);
		setIsModal(true);
		setShowModalMask(true);
		centerInPage();
	}

	private void generatePassword() {
		generate.setDisabled(true);
		LoginService.Instance.get().generatePassword(username, new AsyncCallback<String>() {
			@Override
			public void onFailure(Throwable caught) {
				GuiLog.serverError(caught);
				generate.setDisabled(false);
			}

			@Override
			public void onSuccess(String pswd) {
				password.setValue(pswd);
				generate.setDisabled(false);
			}
		});
	}

	@Override
	protected void onDraw() {
		password = new StaticTextItem(I18N.message("password"));
		password.setWrapTitle(false);
		password.setWrap(false);

		generate = new ButtonItem("generate", I18N.message("generate"));
		generate.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent event) {
				if (form.validate())
					generatePassword();
			}
		});

		form.setWidth(1);
		form.setHeight(1);
		form.setItems(password, generate);

		addItem(form);
		generatePassword();
	}
}