package com.logicaldoc.gui.common.client.widgets;

import java.util.HashMap;
import java.util.Map;

import com.logicaldoc.gui.common.client.i18n.I18N;
import com.smartgwt.client.types.HeaderControls;
import com.smartgwt.client.widgets.HeaderControl;
import com.smartgwt.client.widgets.Window;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.events.ResizedEvent;
import com.smartgwt.client.widgets.events.ResizedHandler;

public abstract class StickyWindow extends Window {

	/**
	 * The key is a class name while the value is it's descriptor
	 */
	protected static Map<String, WindowStatus> statuses = new HashMap<>();

	public StickyWindow(String title) {

		HeaderControl restore = new HeaderControl(HeaderControl.REFRESH, new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				restoreDefaultStatus();
			}
		});
		restore.setTooltip(I18N.message("refresh"));

		if (getDefaultStatus() != null)
			setHeaderControls(HeaderControls.HEADER_LABEL, restore, HeaderControls.CLOSE_BUTTON);
		else
			setHeaderControls(HeaderControls.HEADER_LABEL, HeaderControls.CLOSE_BUTTON);
		setTitle(I18N.message(title));
		setCanDragResize(true);
		setIsModal(true);
		setShowModalMask(true);
		centerInPage();

		restoreCurrentStatus();

		if (getDefaultStatus() != null) {
			addResizedHandler(new ResizedHandler() {

				@Override
				public void onResized(ResizedEvent event) {
					saveWindowStatus();
				}
			});
		} else
			setAutoSize(getAutoSize());
	}

	@Override
	protected void onDraw() {
		super.onDraw();
		restoreCurrentStatus();
	}

	private void restoreDefaultStatus() {
		centerInPage();
		statuses.remove(this.getClass().getName());
		WindowStatus status = getDefaultStatus();
		setWidth(status.getWidth());
		setHeight(status.getHeight());
		saveWindowStatus();
	}

	protected WindowStatus getWindowStatus() {
		String key = this.getClass().getName();
		WindowStatus status = statuses.get(key);
		if (status == null) {
			status = getDefaultStatus();
			statuses.put(key, status);
		}
		return status;
	}

	protected void restoreCurrentStatus() {
		centerInPage();
		WindowStatus status = getWindowStatus();
		if (status != null) {
			setWidth(status.getWidth());
			setHeight(status.getHeight());
		} else {
			status = getDefaultStatus();
		}
		if (status == null)
			setAutoSize(true);
		saveWindowStatus();
	}

	private void saveWindowStatus() {
		WindowStatus status = getWindowStatus();
		if (status != null) {
			status.setWidth(getWidth());
			status.setHeight(getHeight());
		}
	}

	/**
	 * Concrete implementations should override this method to give the default
	 * informations for the window
	 * 
	 * @return a default status or null in case the window does not support the
	 *         sticky
	 */
	protected WindowStatus getDefaultStatus() {
		return null;
	}

	/**
	 * Represents a status of the window
	 * 
	 * @author Marco Meschieri - LogicalDOC
	 * @since 8.7.3
	 */
	public class WindowStatus {
		private int width = 500;

		private int height = 400;

		public WindowStatus(int width, int height) {
			super();
			this.width = width;
			this.height = height;
		}

		public int getWidth() {
			return width;
		}

		public void setWidth(int width) {
			this.width = width;
		}

		public int getHeight() {
			return height;
		}

		public void setHeight(int height) {
			this.height = height;
		}
	}
}