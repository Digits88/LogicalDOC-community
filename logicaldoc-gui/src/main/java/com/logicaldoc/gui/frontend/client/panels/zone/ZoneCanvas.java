package com.logicaldoc.gui.frontend.client.panels.zone;

import com.logicaldoc.gui.common.client.beans.GUIZone;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.util.LD;
import com.logicaldoc.gui.common.client.util.Util;
import com.logicaldoc.gui.common.client.widgets.ImageWithCanvases;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.DragAppearance;
import com.smartgwt.client.types.Overflow;
import com.smartgwt.client.types.VerticalAlignment;
import com.smartgwt.client.widgets.Label;
import com.smartgwt.client.widgets.events.DoubleClickEvent;
import com.smartgwt.client.widgets.events.DoubleClickHandler;
import com.smartgwt.client.widgets.events.MovedEvent;
import com.smartgwt.client.widgets.events.MovedHandler;
import com.smartgwt.client.widgets.events.ResizedEvent;
import com.smartgwt.client.widgets.events.ResizedHandler;
import com.smartgwt.client.widgets.events.RightMouseDownEvent;
import com.smartgwt.client.widgets.events.RightMouseDownHandler;
import com.smartgwt.client.widgets.menu.Menu;
import com.smartgwt.client.widgets.menu.MenuItem;
import com.smartgwt.client.widgets.menu.events.MenuItemClickEvent;

/**
 * A generic panel displaying a zone
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.8.3
 */
public abstract class ZoneCanvas extends Label {

	protected GUIZone zone;

	protected ZoneTemplatePanel zonePanel;

	public ZoneCanvas(GUIZone zone, ZoneTemplatePanel zonePanel) {
		this.zone = zone;
		this.zonePanel = zonePanel;

		setOverflow(Overflow.HIDDEN);
		setBorder("1px solid #AAAAAA");
		setBackgroundColor("#FFFF88");
		setOpacity(60);

		setOverflow(Overflow.HIDDEN);
		setBorder("1px solid #999999");

		setContents("<span style='color: blue'>" + Util.escapeHTML(zone.getDisplayContent()) + "</span>");
		setAlign(Alignment.LEFT);
		setValign(VerticalAlignment.TOP);

		setCanDragReposition(true);
		setDragAppearance(DragAppearance.TARGET);
		setCanDragResize(true);
		setSmoothFade(true);

		ImageWithCanvases sample = zonePanel.getSample();
		setLeft(sample.computeLeftPixel(zone.getLeft()));
		setTop(sample.computeTopPixel(zone.getTop()));
		setWidth(sample.computeWidthPixels(zone.getWidth()));
		setHeight(sample.computeHeightPixels(zone.getHeight()));

		addDoubleClickHandler(new DoubleClickHandler() {

			@Override
			public void onDoubleClick(DoubleClickEvent event) {
				showContextMenu();
			}
		});

		addRightMouseDownHandler(new RightMouseDownHandler() {

			@Override
			public void onRightMouseDown(RightMouseDownEvent event) {
				showContextMenu();
			}
		});

		addMovedHandler(new MovedHandler() {

			@Override
			public void onMoved(MovedEvent event) {
				captureZonePosition();
			}
		});

		addResizedHandler(new ResizedHandler() {

			@Override
			public void onResized(ResizedEvent event) {
				captureZonePosition();
			}
		});
	}

	protected void captureZonePosition() {
		ImageWithCanvases sample = zonePanel.getSample();
		zone.setLeft(sample.getLeft(getLeft()));
		zone.setTop(sample.getTop(getTop()));
		zone.setWidth(sample.getWidth(getWidth()));
		zone.setHeight(sample.getHeight(getHeight()));

		zone.setSample(null);
		zone.setSampleText(null);
	}

	public GUIZone getZone() {
		return zone;
	}

	public ZoneTemplatePanel getZonePanel() {
		return zonePanel;
	}

	protected void showContextMenu() {
		Menu contextMenu = new Menu();
		MenuItem delete = new MenuItem();
		delete.setTitle(I18N.message("ddelete"));
		delete.addClickHandler(new com.smartgwt.client.widgets.menu.events.ClickHandler() {
			public void onClick(MenuItemClickEvent event) {
				LD.ask(I18N.message("question"), I18N.message("confirmdelete"), (Boolean value) -> {
					if (Boolean.TRUE.equals(value)) {
						zonePanel.getSelectedOcrTemplate().removeZone(zone.getName());
						zonePanel.getSample().clearCanvases();
						zonePanel.showZones();
					}
				});
			}
		});

		MenuItem edit = new MenuItem();
		edit.setTitle(I18N.message("edit"));
		edit.addClickHandler((MenuItemClickEvent event) -> onEdit());

		contextMenu.setItems(edit, delete);
		contextMenu.showContextMenu();
	}

	/**
	 * Abstract metod invoked when the user wants to edit a zone
	 */
	protected abstract void onEdit();
}