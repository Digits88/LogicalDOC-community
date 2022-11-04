package com.logicaldoc.core.sequence;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.logicaldoc.core.HibernatePersistentObjectDAO;
import com.logicaldoc.core.PersistenceException;
import com.logicaldoc.util.sql.SqlUtil;

/**
 * Hibernate implementation of <code>SequenceDAO</code>. <br>
 * Sequences are implemented ad Generics whose type is 'sequence' and subtype is
 * the sequence name.
 * 
 * @author Marco Meschieri - LogicalDOC
 * @since 4.0
 */
public class HibernateSequenceDAO extends HibernatePersistentObjectDAO<Sequence> implements SequenceDAO {

	private HibernateSequenceDAO() {
		super(Sequence.class);
		super.log = LoggerFactory.getLogger(HibernateSequenceDAO.class);
	}

	@Override
	public synchronized void reset(String sequence, long objectId, long tenantId, long value) {
		synchronized (SequenceDAO.class) {
			Sequence seq = findByAlternateKey(sequence, objectId, tenantId);
			if (seq == null)
				seq = new Sequence();
			seq.setName(sequence);
			seq.setObjectId(objectId);
			seq.setTenantId(tenantId);
			seq.setLastReset(new Date());
			seq.setValue(value);

			try {
				store(seq);
			} catch (PersistenceException e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	@Override
	public synchronized long next(String sequence, long objectId, long tenantId, long increment) {
		synchronized (SequenceDAO.class) {
			Sequence seq = findByAlternateKey(sequence, objectId, tenantId);
			if (seq == null) {
				seq = new Sequence();
			}

			seq.setName(sequence);
			seq.setObjectId(objectId);
			seq.setTenantId(tenantId);
			seq.setValue(seq.getValue() + increment);
			try {
				store(seq);
				flush();
			} catch (PersistenceException e) {
				log.error(e.getMessage(), e);
			}
			return seq.getValue();
		}
	}

	@Override
	public synchronized long next(String sequence, long objectId, long tenantId) {
		return this.next(sequence, objectId, tenantId, 1L);
	}

	@Override
	public long getCurrentValue(String sequence, long objectId, long tenantId) {
		Sequence seq = findByAlternateKey(sequence, objectId, tenantId);
		if (seq == null)
			return 0L;
		else
			return seq.getValue();
	}

	@Override
	public List<Sequence> findByName(String name, long tenantId) {
		String query = " " + ENTITY + ".tenantId=" + tenantId;
		query += " and " + ENTITY + ".name like '" + SqlUtil.doubleQuotes(name) + "%' ";

		try {
			return findByWhere(query, null, null);
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
			return new ArrayList<Sequence>();
		}
	}

	@Override
	public Sequence findByAlternateKey(String name, long objectId, long tenantId) {
		try {
			Sequence sequence = null;

			String query = " " + ENTITY + ".tenantId = :tenantId ";
			query += " and " + ENTITY + ".objectId = :objectId ";
			query += " and " + ENTITY + ".name = :name ";
			List<Sequence> sequences = new ArrayList<Sequence>();
			try {
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("tenantId", tenantId);
				params.put("objectId", objectId);
				params.put("name", name);

				sequences = findByWhere(query, params, null, null);
			} catch (Throwable t) {
				// Nothing to do
			}

			// It's incredible but the findByWhere sometimes doesn't find the
			// sequence so finding by the ID is safer
			if (sequences.isEmpty()) {
				query = "select ld_id from ld_sequence where ld_name='" + SqlUtil.doubleQuotes(name)
						+ "' and ld_objectid=" + objectId + " and ld_tenantid=" + tenantId;
				try {
					long sequenceId = queryForLong(query);
					if (sequenceId != 0L)
						sequence = findById(sequenceId);
				} catch (Throwable t) {
					log.warn(t.getMessage(), t);
				}
			} else {
				sequence = sequences.get(0);
			}

			if (sequence == null)
				log.debug("Unable to find sequence " + name + "," + objectId + "," + tenantId);
			else
				refresh(sequence);

			return sequence;
		} catch (Throwable t) {
			log.error(t.getMessage(), t);
			return null;
		}
	}

	@Override
	public void delete(String name, long objectId, long tenantId) throws PersistenceException {
		Sequence seq = findByAlternateKey(name, objectId, tenantId);
		if (seq != null)
			delete(seq.getId());
	}

	@Override
	public void delete(long id, int code) throws PersistenceException {
		Sequence seq = findById(id);
		if (seq != null) {
			seq.setName(seq.getId() + "." + seq.getName());
			seq.setDeleted(code);
			store(seq);
		}
	}
}