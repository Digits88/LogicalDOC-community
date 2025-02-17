package com.logicaldoc.gui.common.client.beans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.logicaldoc.gui.common.client.Session;

/**
 * This models an OCR template
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.4.2
 */
public class GUIOCRTemplate implements Serializable {

	private static final long serialVersionUID = 1L;

	private long id;

	/**
	 * Unique name of the template
	 */
	private String name;

	private String description;

	/**
	 * Sample image displayed to the user in order to define the zones.
	 */
	private String sample;

	/**
	 * The assigned document template
	 */
	private GUITemplate template;

	/**
	 * The zones
	 */
	private GUIZone[] zones = new GUIZone[0];

	private int batch = 200;

	public GUIOCRTemplate() {
		try {
			batch = Session.get().getConfigAsInt("zonalocr.batch");
		} catch (Throwable t) {
			// Nothing to do
		}
	}

	public GUIZone getZone(String name) {
		if (zones == null || zones.length < 1)
			return null;

		for (GUIZone att : getZones()) {
			if (att.getName().equals(name))
				return att;
		}
		return null;
	}

	public void appendZone(GUIZone a) {
		List<GUIZone> newZones = new ArrayList<>();
		if (getZones() != null)
			newZones.addAll(Arrays.asList(getZones()));
		newZones.add(a);
		zones = newZones.toArray(new GUIZone[0]);
	}

	public void removeZone(String name) {
		if (getZone(name) == null || zones == null || zones.length < 1)
			return;

		List<GUIZone> newAttrs = new ArrayList<>();
		for (GUIZone att : getZones())
			if (!att.getName().equals(name))
				newAttrs.add(att);

		zones = newAttrs.toArray(new GUIZone[0]);
	}

	public GUIZone[] getZones() {
		return zones;
	}

	public void setZones(GUIZone[] zones) {
		this.zones = zones;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getSample() {
		return sample;
	}

	public void setSample(String sample) {
		this.sample = sample;
	}

	public GUITemplate getTemplate() {
		return template;
	}

	public void setTemplate(GUITemplate template) {
		this.template = template;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int getBatch() {
		return batch;
	}

	public void setBatch(int batch) {
		this.batch = batch;
	}
}