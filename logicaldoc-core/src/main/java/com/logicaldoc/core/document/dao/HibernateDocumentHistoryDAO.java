package com.logicaldoc.core.document.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.logicaldoc.core.HibernatePersistentObjectDAO;
import com.logicaldoc.core.History;
import com.logicaldoc.core.PersistenceException;
import com.logicaldoc.core.RunLevel;
import com.logicaldoc.core.communication.EventCollector;
import com.logicaldoc.core.document.DocumentHistory;
import com.logicaldoc.util.sql.SqlUtil;

/**
 * Hibernate implementation of <code>HistoryDAO</code>
 * 
 * @author Alessandro Gasparini - LogicalDOC
 * @since 3.0
 */
public class HibernateDocumentHistoryDAO extends HibernatePersistentObjectDAO<DocumentHistory>
		implements DocumentHistoryDAO {

	private static final String DATE_ASC = ".date asc";
	private static final String ORDER_BY = "order by ";
	private static final String AND = " and ";

	private HibernateDocumentHistoryDAO() {
		super(DocumentHistory.class);
		super.log = LoggerFactory.getLogger(HibernateDocumentHistoryDAO.class);
	}

	/**
	 * @see com.logicaldoc.core.document.dao.DocumentHistoryDAO#findByDocId(long)
	 */
	public List<DocumentHistory> findByDocId(long docId) {
		return findByDocIdAndEvent(docId, null);
	}

	@Override
	public List<DocumentHistory> findByDocIdAndEvent(long docId, String event) {
		StringBuilder query = new StringBuilder(" " + ALIAS_ENTITY + ".docId = " + docId);
		if (StringUtils.isNotEmpty(event))
			query.append(AND + ALIAS_ENTITY + ".event='" + SqlUtil.doubleQuotes(event) + "'");

		try {
			return findByWhere(query.toString(), ORDER_BY + ALIAS_ENTITY + ".date desc", null);
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
			return new ArrayList<DocumentHistory>();
		}
	}

	/**
	 * @see com.logicaldoc.core.document.dao.DocumentHistoryDAO#findByUserId(long)
	 */
	public List<DocumentHistory> findByUserId(long userId) {
		return findByUserIdAndEvent(userId, null, null);
	}

	/**
	 * @see com.logicaldoc.core.document.dao.DocumentHistoryDAO#findByFolderId(long)
	 */
	public List<DocumentHistory> findByFolderId(long folderId) {
		try {
			return findByWhere(ALIAS_ENTITY + ".folderId =" + folderId, ORDER_BY + ALIAS_ENTITY + DATE_ASC, null);
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
			return new ArrayList<DocumentHistory>();
		}
	}

	@Override
	public List<DocumentHistory> findNotNotified(Integer max) {
		try {
			return findByWhere(ALIAS_ENTITY + ".notified = 0", ORDER_BY + ALIAS_ENTITY + DATE_ASC, max);
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
			return new ArrayList<DocumentHistory>();
		}
	}

	@Override
	public void cleanOldHistories(int ttl) {
		try {
			log.info("cleanOldHistories rows updated: {}", cleanOldRecords(ttl, "ld_history"));
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
		}
	}

	@Override
	public List<DocumentHistory> findByUserIdAndEvent(long userId, String event, String sessionId) {
		String query = ALIAS_ENTITY + ".userId =" + userId;
		if (event != null && StringUtils.isNotEmpty(event))
			query += AND + ALIAS_ENTITY + ".event = '" + SqlUtil.doubleQuotes(event) + "'";
		if (sessionId != null && StringUtils.isNotEmpty(sessionId))
			query += AND + ALIAS_ENTITY + ".sessionId = '" + sessionId + "'";

		try {
			return findByWhere(query, ORDER_BY + ALIAS_ENTITY + DATE_ASC, null);
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
			return new ArrayList<DocumentHistory>();
		}
	}

	@Override
	public void markHistoriesAsRead(String event, long userId) {
		String statement = "update ld_history set ld_new=0 where ld_new=1 and ld_userid=" + userId + " and ld_event='"
				+ SqlUtil.doubleQuotes(event) + "'";
		try {
			jdbcUpdate(statement);
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
		}
	}

	@Override
	public void store(DocumentHistory history) throws PersistenceException {
		// Write only if the history is enabled
		if (RunLevel.current().aspectEnabled(History.ASPECT)) {
			if (history.getComment() != null && history.getComment().length() > 4000)
				history.setComment(StringUtils.abbreviate(history.getComment(), 4000));
			super.store(history);
			EventCollector.get().newEvent(history);
		}
	}

	@Override
	public List<DocumentHistory> findByPath(String pathExpression, Date oldestDate, Collection<String> events,
			Integer max) {
		StringBuilder query = new StringBuilder("(" + ALIAS_ENTITY + ".path like :pathExpression or " + ALIAS_ENTITY
				+ ".pathOld like :pathExpression) ");
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("pathExpression", pathExpression);

		if (oldestDate != null) {
			query.append(AND + ALIAS_ENTITY + ".date >= :oldestDate ");
			params.put("oldestDate", oldestDate);
		}

		if (events != null && !events.isEmpty()) {
			StringBuilder eventsStr = new StringBuilder("(");
			for (String event : events) {
				if (eventsStr.length() > 1)
					eventsStr.append(",");
				eventsStr.append("'" + event + "'");
			}
			eventsStr.append(")");
			query.append(AND + ALIAS_ENTITY + ".event in " + eventsStr);
		}

		try {
			return findByWhere(query.toString(), params, ORDER_BY + ALIAS_ENTITY + DATE_ASC, max);
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
			return new ArrayList<DocumentHistory>();
		}
	}
}