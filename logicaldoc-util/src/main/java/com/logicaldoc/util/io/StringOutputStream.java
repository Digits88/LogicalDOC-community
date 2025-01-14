package com.logicaldoc.util.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stram that writes into a string
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 8.6.1
 */
public class StringOutputStream extends OutputStream {

	StringBuilder sb = new StringBuilder();

	public StringOutputStream() {
	}

	public StringOutputStream(StringBuilder sb) {
		this.sb = sb;
	}

	public void close() throws IOException {
		sb = new StringBuilder();
	}

	public void flush() throws IOException {
		// Nothing to do
	}

	public void write(byte[] b) throws IOException {
		sb.append(new String(b));
	}

	public void write(byte b) throws IOException {
		sb.append((char) b);
	}

	public void write(int i) throws IOException {
		sb.append((char) i);
	}

	public String getData() {
		return sb.toString();
	}
}