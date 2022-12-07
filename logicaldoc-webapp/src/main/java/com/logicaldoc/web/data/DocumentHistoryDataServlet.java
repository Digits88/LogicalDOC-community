package com.logicaldoc.web.data;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.logicaldoc.core.PersistenceException;
import com.logicaldoc.core.document.Document;
import com.logicaldoc.core.document.dao.DocumentDAO;
import com.logicaldoc.core.document.dao.DocumentHistoryDAO;
import com.logicaldoc.core.security.Menu;
import com.logicaldoc.core.security.Session;
import com.logicaldoc.core.security.dao.MenuDAO;
import com.logicaldoc.core.util.IconSelector;
import com.logicaldoc.i18n.I18N;
import com.logicaldoc.util.Context;
import com.logicaldoc.util.io.FileUtil;

/**
 * This servlet is responsible for documents history data.
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 6.0
 */
public class DocumentHistoryDataServlet extends AbstractDataServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response, Session session, int max,
			Locale locale) throws PersistenceException, IOException {

		MenuDAO mDao = (MenuDAO) Context.get().getBean(MenuDAO.class);
		boolean showSid = mDao.isReadEnable(Menu.SESSIONS, session.getUserId());

		PrintWriter writer = response.getWriter();
		writer.write("<list>");

		// Used only to cache the already encountered documents when the
		// history
		// is related to a single user (for dashboard visualization)
		Set<Long> docIds = new HashSet<Long>();

		Map<String, Object> params = new HashMap<String, Object>();

		DocumentHistoryDAO dao = (DocumentHistoryDAO) Context.get().getBean(DocumentHistoryDAO.class);
		StringBuilder query = new StringBuilder(
				"select A.username, A.event, A.version, A.date, A.comment, A.filename, A.isNew, A.folderId, A.docId, A.path, A.sessionId, A.userId, A.reason, A.ip, A.device, A.geolocation, A.color, A.fileVersion from DocumentHistory A where 1=1 and A.deleted = 0 ");
		if (request.getParameter("docId") != null) {
			Long docId = Long.parseLong(request.getParameter("docId"));
			DocumentDAO ddao = (DocumentDAO) Context.get().getBean(DocumentDAO.class);
			Document doc = ddao.findDocument(docId);
			if (doc != null)
				docId = doc.getId();
			query.append(" and A.docId = :docId");
			params.put("docId", docId);
		}
		if (request.getParameter("userId") != null) {
			query.append(" and A.userId = :userId");

		}
		if (request.getParameter("event") != null) {
			query.append(" and A.event = :event");
			params.put("event", request.getParameter("event"));
		}
		query.append(" order by A.date desc ");

		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));

		List<Object> records = (List<Object>) dao.findByQuery(query.toString(), params, max);

		/*
		 * Iterate over records composing the response XML document
		 */
		for (Object record : records) {
			Object[] cols = (Object[]) record;
			if (request.getParameter("userId") != null) {
				/*
				 * If the request contains the user specification, we report
				 * just the latest event per each document
				 */
				if (docIds.contains(cols[8]))
					continue;
				else
					docIds.add((Long) cols[8]);
			}

			writer.print("<history>");
			writer.print("<user><![CDATA[" + cols[0] + "]]></user>");
			writer.print("<event><![CDATA[" + I18N.message((String) cols[1], locale) + "]]></event>");
			writer.print("<version>" + cols[2] + "</version>");
			writer.print("<date>" + df.format((Date) cols[3]) + "</date>");
			writer.print("<comment><![CDATA[" + (cols[4] == null ? "" : cols[4]) + "]]></comment>");
			writer.print("<filename><![CDATA[" + (cols[5] == null ? "" : cols[5]) + "]]></filename>");
			writer.print(
					"<icon>" + FileUtil.getBaseName(IconSelector.selectIcon(FileUtil.getExtension((String) cols[5])))
							+ "</icon>");
			writer.print("<new>" + (1 == (Integer) cols[6]) + "</new>");
			writer.print("<folderId>" + cols[7] + "</folderId>");
			writer.print("<docId>" + cols[8] + "</docId>");
			writer.print("<path><![CDATA[" + (cols[9] == null ? "" : cols[9]) + "]]></path>");
			if (showSid)
				writer.print("<sid><![CDATA[" + (cols[10] == null ? "" : cols[10]) + "]]></sid>");
			writer.print("<userId>" + cols[11] + "</userId>");
			writer.print("<reason><![CDATA[" + (cols[12] == null ? "" : cols[12]) + "]]></reason>");
			writer.print("<ip><![CDATA[" + (cols[13] == null ? "" : cols[13]) + "]]></ip>");
			writer.print("<device><![CDATA[" + (cols[14] == null ? "" : cols[14]) + "]]></device>");
			writer.print("<geolocation><![CDATA[" + (cols[15] == null ? "" : cols[15]) + "]]></geolocation>");
			if (cols[16] != null)
				writer.write("<color><![CDATA[" + cols[16] + "]]></color>");
			writer.print("<fileVersion>" + (cols[17] == null ? "" : cols[17]) + "</fileVersion>");
			writer.print("</history>");
		}
		writer.write("</list>");
	}
}