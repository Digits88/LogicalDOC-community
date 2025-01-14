package com.logicaldoc.web.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.java.plugin.JpfException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logicaldoc.core.PersistenceException;
import com.logicaldoc.core.communication.EMail;
import com.logicaldoc.core.communication.EMailSender;
import com.logicaldoc.core.document.AbstractDocument;
import com.logicaldoc.core.document.Bookmark;
import com.logicaldoc.core.document.Document;
import com.logicaldoc.core.document.DocumentHistory;
import com.logicaldoc.core.document.DocumentLink;
import com.logicaldoc.core.document.DocumentManager;
import com.logicaldoc.core.document.DocumentNote;
import com.logicaldoc.core.document.dao.BookmarkDAO;
import com.logicaldoc.core.document.dao.DocumentDAO;
import com.logicaldoc.core.document.dao.DocumentHistoryDAO;
import com.logicaldoc.core.document.dao.DocumentLinkDAO;
import com.logicaldoc.core.document.dao.DocumentNoteDAO;
import com.logicaldoc.core.folder.Folder;
import com.logicaldoc.core.folder.FolderDAO;
import com.logicaldoc.core.metadata.Attribute;
import com.logicaldoc.core.metadata.Template;
import com.logicaldoc.core.metadata.TemplateDAO;
import com.logicaldoc.core.searchengine.SearchEngine;
import com.logicaldoc.core.security.User;
import com.logicaldoc.core.store.Storer;
import com.logicaldoc.core.ticket.Ticket;
import com.logicaldoc.core.ticket.TicketDAO;
import com.logicaldoc.gui.common.client.ServerException;
import com.logicaldoc.gui.common.client.beans.GUIAttribute;
import com.logicaldoc.gui.common.client.beans.GUIBookmark;
import com.logicaldoc.gui.common.client.beans.GUIContact;
import com.logicaldoc.gui.common.client.beans.GUIDocument;
import com.logicaldoc.gui.common.client.beans.GUIDocumentNote;
import com.logicaldoc.gui.common.client.beans.GUIEmail;
import com.logicaldoc.gui.common.client.beans.GUIRating;
import com.logicaldoc.gui.common.client.beans.GUIVersion;
import com.logicaldoc.i18n.I18N;
import com.logicaldoc.util.io.FileUtil;
import com.logicaldoc.util.plugin.PluginRegistry;
import com.logicaldoc.web.AbstractWebappTCase;
import com.logicaldoc.web.UploadServlet;

import junit.framework.Assert;

@RunWith(MockitoJUnitRunner.class)
public class DocumentServiceImplTest extends AbstractWebappTCase {

	private static final String UTF_8 = "UTF-8";

	private static Logger log = LoggerFactory.getLogger(DocumentServiceImplTest.class);

	@Mock
	private EMailSender emailSender;

	// Instance under test
	private DocumentServiceImpl service = new DocumentServiceImpl();

	private DocumentDAO docDao;

	private FolderDAO folderDao;

	private Storer storer;

	private TemplateDAO templateDao;

	private DocumentLinkDAO linkDao;

	private DocumentNoteDAO noteDao;

	private BookmarkDAO bookDao;

	private DocumentHistoryDAO documentHistoryDao;

	protected SearchEngine searchEngine;

	@Before
	public void setUp() throws Exception {
		super.setUp();

		docDao = (DocumentDAO) context.getBean("DocumentDAO");
		linkDao = (DocumentLinkDAO) context.getBean("DocumentLinkDAO");
		noteDao = (DocumentNoteDAO) context.getBean("DocumentNoteDAO");
		documentHistoryDao = (DocumentHistoryDAO) context.getBean("DocumentHistoryDAO");
		bookDao = (BookmarkDAO) context.getBean("BookmarkDAO");
		templateDao = (TemplateDAO) context.getBean("TemplateDAO");
		folderDao = (FolderDAO) context.getBean("FolderDAO");
		storer = (Storer) context.getBean("Storer");

		searchEngine = (SearchEngine) context.getBean("SearchEngine");

		prepareUploadedFiles();

		emailSender = mock(EMailSender.class);
		doNothing().when(emailSender).send(any(EMail.class));
		DocumentServiceImpl.setEmailSender(emailSender);

		activateCorePlugin();
	}

	@After
	public void tearDown() throws Exception {
		searchEngine.unlock();
		searchEngine.close();

		super.tearDown();
	}

	private void activateCorePlugin() throws JpfException, IOException {
		File pluginsDir = new File("target/tests-plugins");
		pluginsDir.mkdir();

		File corePluginFile = new File(pluginsDir, "logicaldoc-core-plugin.jar");

		// copy plugin file to target resources
		copyResource("/logicaldoc-core-8.8.3-plugin.jar", corePluginFile.getAbsolutePath());

		PluginRegistry registry = PluginRegistry.getInstance();
		registry.init(pluginsDir.getAbsolutePath());
	}

	private void prepareUploadedFiles() throws IOException {
		File file3 = new File(repositoryDir.getPath() + "/docs/3/doc/1.0");
		file3.getParentFile().mkdirs();
		copyResource("/test.zip", file3.getCanonicalPath());

		File file5 = new File(repositoryDir.getPath() + "/docs/5/doc/1.0");
		file5.getParentFile().mkdirs();
		copyResource("/Joyce Jinks shared the Bruce Duo post.eml", file5.getCanonicalPath());

		File file6 = new File(repositoryDir.getPath() + "/docs/6/doc/1.0");
		file6.getParentFile().mkdirs();
		copyResource("/Hurry up! Only a few hours for the Prime Day VGA promos !!!.msg", file6.getCanonicalPath());

		File file7 = new File(repositoryDir.getPath() + "/docs/7/doc/1.0");
		file7.getParentFile().mkdirs();
		copyResource("/New error indexing documents.eml", file7.getCanonicalPath());

		Map<String, File> uploadedFiles = new HashMap<>();
		uploadedFiles.put("file3.zip", file3);
		uploadedFiles.put("file5.eml", file5);
		uploadedFiles.put("file6.msg", file6);
		uploadedFiles.put("file7.eml", file7);

		session.getDictionary().put(UploadServlet.RECEIVED_FILES, uploadedFiles);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDeleteFromTrash() throws ServerException, PersistenceException {
		service.delete(new long[] { 7 });
		List<Long> docIds = (List<Long>) docDao.queryForList("select ld_id from ld_document where ld_deleted=1",
				Long.class);
		Assert.assertEquals(1, docIds.size());

		service.deleteFromTrash(docIds.toArray(new Long[] {}));
		docIds = (List<Long>) docDao.queryForList("select ld_id from ld_document where ld_deleted=1", Long.class);
		Assert.assertEquals(0, docIds.size());
		docIds = (List<Long>) docDao.queryForList("select ld_id from ld_document where ld_deleted=2", Long.class);
		Assert.assertEquals(1, docIds.size());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testEmptyTrash() throws ServerException, PersistenceException {
		service.delete(new long[] { 7 });
		List<Long> docIds = (List<Long>) docDao.queryForList("select ld_id from ld_document where ld_deleted=1",
				Long.class);
		Assert.assertEquals(1, docIds.size());

		service.emptyTrash();
		docIds = (List<Long>) docDao.queryForList("select ld_id from ld_document where ld_deleted=1", Long.class);
		Assert.assertEquals(0, docIds.size());
		docIds = (List<Long>) docDao.queryForList("select ld_id from ld_document where ld_deleted=2", Long.class);
		Assert.assertEquals(1, docIds.size());
	}

	@Test
	public void testArchiveAndUnarchiveDocuments() throws ServerException, PersistenceException {
		GUIDocument doc = service.getById(7);
		service.archiveDocuments(new long[] { doc.getId() }, "archive comment");

		Document document = docDao.findById(7);
		Assert.assertEquals(Document.DOC_ARCHIVED, document.getStatus());

		service.unarchiveDocuments(new long[] { doc.getId() });
		document = docDao.findById(7);
		Assert.assertEquals(Document.DOC_UNLOCKED, document.getStatus());
	}

	@Test
	public void testArchiveFolder() throws ServerException, PersistenceException {

		// Move a document inside the tree to archive
		Document doc = docDao.findById(5);
		docDao.initialize(doc);
		doc.setFolder(folderDao.findById(1201));
		docDao.store(doc);

		long count = service.archiveFolder(1200, "archive comment");
		Assert.assertEquals(1, count);

		Document document = docDao.findById(5);
		Assert.assertEquals(Document.DOC_ARCHIVED, document.getStatus());
	}

	@Test
	public void testCreateDownloadTicket() throws ServerException, PersistenceException {
		String[] ticket = service.createDownloadTicket(5, null, null, null, null);
		// We do not have a HTTP request so expect that the first string is the
		// exact ticket ID
		Assert.assertEquals("http://server:port/download-ticket?ticketId=" + ticket[0], ticket[1]);

		TicketDAO tDao = (TicketDAO) context.getBean("TicketDAO");
		Ticket t = tDao.findByTicketId(ticket[0]);
		Assert.assertNotNull(t);
		Assert.assertEquals(5L, t.getDocId());
	}

	@Test
	public void testDeleteEnableDisableTicket() throws ServerException, PersistenceException {
		String[] ticket = service.createDownloadTicket(5, null, null, null, null);

		// We do not have a HTTP request so expect that the first string is the
		// exact ticket ID
		TicketDAO tDao = (TicketDAO) context.getBean("TicketDAO");
		Ticket t = tDao.findByTicketId(ticket[0]);
		Assert.assertNotNull(t);
		Assert.assertEquals(5L, t.getDocId());
		Assert.assertEquals(1, t.getEnabled());

		service.disableTicket(t.getId());
		t = tDao.findByTicketId(ticket[0]);
		Assert.assertEquals(0, t.getEnabled());

		service.enableTicket(t.getId());
		t = tDao.findByTicketId(ticket[0]);
		Assert.assertEquals(1, t.getEnabled());

		service.deleteTicket(t.getId());
		t = tDao.findByTicketId(ticket[0]);
		Assert.assertNull(t);
	}

	@Test
	public void testRename() throws ServerException, PersistenceException {
		GUIDocument doc = service.getById(7);
		System.out.println(doc.getFileName());
		Assert.assertEquals("New error indexing documents.eml", doc.getFileName());

		service.rename(doc.getId(), "newname.eml");
		doc = service.getById(7);
		Assert.assertEquals("newname.eml", doc.getFileName());
	}

	@Test
	public void testSetAndUnsetPassword() throws ServerException, PersistenceException {
		service.setPassword(5, "pippo");
		Document doc = docDao.findById(5);
		Assert.assertNotNull(doc.getPassword());

		// Try to unset with wrong password but admin user
		service.unsetPassword(5, "paperino");
		doc = docDao.findById(5);
		Assert.assertNull(doc.getPassword());
	}

	@Test
	public void testUprotect() throws ServerException, PersistenceException {
		service.setPassword(5, "pippo");
		Document doc = docDao.findById(5);
		Assert.assertNotNull(doc.getPassword());

		// Try to uprotect with wrong password
		service.unprotect(5, "paperino");
		Assert.assertTrue(session.getUnprotectedDocs().isEmpty());

		// Try to uprotect with correct password
		service.unprotect(5, "pippo");
		Assert.assertTrue(session.getUnprotectedDocs().containsKey(5L));
	}

	@Test
	public void testCreateWithContent() throws ServerException {
		GUIDocument doc = service.getById(7);
		doc.setId(0);
		doc.setFileName("testcontent.txt");
		doc.setCustomId(null);
		doc = service.createWithContent(doc, "text content", true);
		Assert.assertNotNull(doc);
		Assert.assertTrue(doc.getId() != 0L);

		doc = service.getById(doc.getId());
		Assert.assertNotNull(doc);
		Assert.assertEquals("text content",
				storer.getString(doc.getId(), storer.getResourceName(doc.getId(), null, null)));
		service.checkout(new long[] { doc.getId() });

		doc.setId(0);
		doc.setFileName("testcontent2.txt");
		doc.setCustomId(null);
		doc = service.createWithContent(doc, " ", true);
		Assert.assertNotNull(doc);
		Assert.assertTrue(doc.getId() != 0L);

		doc = service.getById(doc.getId());
		Assert.assertNotNull(doc);
		Assert.assertEquals(" ", storer.getString(doc.getId(), storer.getResourceName(doc.getId(), null, null)));
		service.checkout(new long[] { doc.getId() });
	}

	@Test
	public void testCheckinContext() throws ServerException {
		testCreateWithContent();
		service.checkout(new long[] { 7 });

		service.checkinContent(7, "checkedin contents");
		Assert.assertEquals("checkedin contents", service.getContentAsString(7));
	}

	@Test
	public void testSaveAndGetRating() throws ServerException {
		GUIDocument doc = service.getById(7);
		Assert.assertEquals(0, doc.getRating());

		GUIRating rating = new GUIRating();
		rating.setDocId(doc.getId());
		rating.setUserId(User.USERID_ADMIN);
		rating.setUsername("admin");
		rating.setVote(4);

		service.saveRating(rating);
		doc = service.getById(doc.getId());
		Assert.assertEquals(4, doc.getRating());

		rating = service.getRating(doc.getId());
		Assert.assertEquals("4.0", rating.getAverage().toString());
		Assert.assertEquals(Integer.valueOf(1), rating.getCount());

		rating = service.getUserRating(doc.getId());
		Assert.assertEquals(4, rating.getVote());

		Assert.assertEquals(Integer.valueOf(0), service.deleteRating(rating.getId()));
		rating = service.getRating(doc.getId());
		Assert.assertEquals(0, rating.getVote());
	}

	@Test
	public void testReplaceFile() throws ServerException {
		GUIDocument doc = service.getById(7);
		Assert.assertFalse(service.getContentAsString(7).contains("replaced contents"));

		service.cleanUploadedFileFolder();

		File tmpFile = new File("target/replacefile.txt");
		try {
			FileUtil.writeFile("replaced contents", tmpFile.getAbsolutePath());
			Map<String, File> uploadedFiles = new HashMap<>();
			uploadedFiles.put(doc.getFileName(), tmpFile);

			session.getDictionary().put(UploadServlet.RECEIVED_FILES, uploadedFiles);
			service.replaceFile(doc.getId(), doc.getFileVersion(), "replace");
			Assert.assertTrue(service.getContentAsString(7).contains("replaced contents"));
		} finally {
			FileUtil.strongDelete(tmpFile);
		}
	}

	@Test
	public void testCreateDocument() throws ServerException {
		GUIDocument doc = service.getById(7);
		doc.setId(0);
		doc.setFileName("test.txt");
		doc.setCustomId(null);

		doc = service.createDocument(doc, "document content");
		Assert.assertNotNull(doc);

		Assert.assertEquals("document content", service.getContentAsString(doc.getId()));
	}

	@Test
	public void testEnforceFilesIntoFolderStorage() throws ServerException, PersistenceException, InterruptedException {
		Folder folder = folderDao.findById(1200);
		folderDao.initialize(folder);
		Assert.assertEquals(Integer.valueOf(2), folder.getStorage());

		Document doc = docDao.findById(5);
		docDao.initialize(doc);
		doc.setFolder(folderDao.findById(1201));
		docDao.store(doc);

		doc = docDao.findById(5);
		Assert.assertNull(doc.getFolder().getStorage());

		service.enforceFilesIntoFolderStorage(1200);

		Thread.sleep(3000);

		File movedFile = new File(repositoryDir + "/docs2/5/doc/1.0");
		Assert.assertTrue(movedFile.exists());
	}

	@Test
	public void testMakeImmutable() throws ServerException, IOException, InterruptedException {
		GUIDocument doc = service.getById(7);
		Assert.assertEquals(0, doc.getImmutable());

		service.makeImmutable(new long[] { 7 }, "immutable comment");

		doc = service.getById(7);
		Assert.assertEquals(1, doc.getImmutable());
	}

	@Test
	public void testPromoteVersion() throws ServerException, IOException, InterruptedException {
		testCheckin();
		GUIDocument doc = service.getById(7);
		Assert.assertEquals(GUIDocument.DOC_UNLOCKED, doc.getStatus());
		Assert.assertEquals("1.1", doc.getVersion());
		Assert.assertEquals("1.1", doc.getFileVersion());

		service.promoteVersion(doc.getId(), "1.0");

		doc = service.getById(7);
		Assert.assertEquals(GUIDocument.DOC_UNLOCKED, doc.getStatus());
		Assert.assertEquals("1.2", doc.getVersion());
		Assert.assertEquals("1.2", doc.getFileVersion());

		// Unexisting version
		boolean exceptionHappened = false;
		try {
			service.promoteVersion(doc.getId(), "xxxx");
		} catch (ServerException e) {
			exceptionHappened = true;
			Assert.assertEquals("Unexisting version xxxx of document 7", e.getMessage());
		}
		Assert.assertTrue(exceptionHappened);

		// Document locked
		service.lock(new long[] { 7 }, "lock comment");
		exceptionHappened = false;
		try {
			service.promoteVersion(doc.getId(), "1.0");
		} catch (ServerException e) {
			exceptionHappened = true;
			Assert.assertEquals("The document 7 is locked", e.getMessage());
		}
		Assert.assertTrue(exceptionHappened);
	}

	@Test
	public void testCheckout() throws ServerException, IOException, InterruptedException {
		GUIDocument doc = service.getById(7);
		Assert.assertEquals(Document.DOC_UNLOCKED, doc.getStatus());

		service.checkout(new long[] { 7 });
		doc = service.getById(7);
		Assert.assertEquals(Document.DOC_CHECKED_OUT, doc.getStatus());
	}

	@Test
	public void testCheckin() throws ServerException, IOException, InterruptedException {
		testCheckout();

		// Prepare the file to checkin
		Map<String, File> uploadedFiles = new HashMap<>();
		File file3 = new File("target/repository/docs/3/doc/1.0");
		uploadedFiles.put("test.zip", file3);
		session.getDictionary().put(UploadServlet.RECEIVED_FILES, uploadedFiles);

		GUIDocument doc = service.getById(7);
		Assert.assertEquals("1.0", doc.getVersion());
		Assert.assertEquals("1.0", doc.getFileVersion());

		doc.setComment("version comment");
		service.checkin(doc, false);

		doc = service.getById(7);
		Assert.assertEquals(GUIDocument.DOC_UNLOCKED, doc.getStatus());
		Assert.assertEquals("1.1", doc.getVersion());
		Assert.assertEquals("1.1", doc.getFileVersion());
	}

	@Test
	public void testAddDocuments() throws ServerException, IOException, InterruptedException {
		GUIDocument doc = service.getById(7);
		doc.setId(0L);
		doc.setCustomId(null);
		doc.setIndexed(0);
		doc.setNotifyUsers(new long[] { 2, 3 });

		GUIDocument[] createdDocs = service.addDocuments(false, UTF_8, false, doc);
		Assert.assertEquals(4, createdDocs.length);

		Thread.sleep(3000);

		service.cleanUploadedFileFolder();
		prepareUploadedFiles();
		doc = service.getById(7);
		doc.setId(0L);
		doc.setCustomId(null);
		doc.setIndexed(0);

		// Request immediate indexing
		createdDocs = service.addDocuments(false, UTF_8, true, doc);
		Assert.assertEquals(4, createdDocs.length);

		prepareUploadedFiles();
		doc = service.getById(7);
		doc.setId(0L);
		doc.setCustomId(null);
		doc.setIndexed(0);

		// Request zip import so just the other 3 documents are imported
		// immediately
		createdDocs = service.addDocuments(true, UTF_8, false, doc);
		Assert.assertEquals(3, createdDocs.length);

		// Remove the uploaded files
		@SuppressWarnings("unchecked")
		Map<String, File> uploadedFiles = (Map<String, File>) session.getDictionary().get(UploadServlet.RECEIVED_FILES);
		uploadedFiles.clear();

		doc.setId(0L);
		doc.setCustomId(null);
		doc.setIndexed(0);
		boolean exceptionHappened = false;
		try {
			service.addDocuments(false, UTF_8, false, doc);
		} catch (ServerException e) {
			exceptionHappened = true;
			Assert.assertEquals("No file uploaded", e.getMessage());
		}
		Assert.assertTrue(exceptionHappened);

		// Try with a user without permissions
		prepareUploadedFiles();
		
		doc = service.getById(7);
		doc.setId(0L);
		doc.setCustomId(null);
		doc.setIndexed(0);
		doc.setFolder(new FolderServiceImpl().getFolder(1201, false, false, false));
		prepareSession("boss", "admin");
		prepareUploadedFiles();
		
		createdDocs = service.addDocuments(true, UTF_8, false, doc);
		Assert.assertEquals(0, createdDocs.length);

		prepareSession("admin", "admin");
		prepareUploadedFiles();
		createdDocs = service.addDocuments("en", 1201, false, UTF_8, false, null);
		Assert.assertEquals(4, createdDocs.length);

		// Cannot add documents into the root
		exceptionHappened = false;
		try {
			service.addDocuments("en", Folder.ROOTID, false, UTF_8, false, null);
		} catch (ServerException e) {
			exceptionHappened = true;
			Assert.assertEquals("Cannot add documents in the root", e.getMessage());
		}
		Assert.assertTrue(exceptionHappened);
	}

	@Test
	public void testMerge() throws ServerException, IOException, InterruptedException {
		GUIDocument doc = service.getById(7);
		doc.setId(0L);
		doc.setCustomId(null);
		doc.setIndexed(0);

		File pdf1 = new File("target/pdf1.pdf");
		File pdf2 = new File("target/pdf2.pdf");

		try {
			copyResource("/pdf1.pdf", pdf1.getCanonicalPath());
			copyResource("/pdf2.pdf", pdf2.getCanonicalPath());

			Map<String, File> uploadedFiles = new HashMap<>();
			uploadedFiles.put(pdf1.getName(), pdf1);
			uploadedFiles.put(pdf2.getName(), pdf2);

			session.getDictionary().put(UploadServlet.RECEIVED_FILES, uploadedFiles);

			GUIDocument[] createdDocs = service.addDocuments(false, UTF_8, false, doc);
			Assert.assertEquals(2, createdDocs.length);

			GUIDocument mergedDoc = service.merge(new long[] { createdDocs[0].getId(), createdDocs[1].getId() }, 1200,
					"merged.pdf");
			mergedDoc = service.getById(mergedDoc.getId());
			Assert.assertNotNull(mergedDoc);
			Assert.assertEquals("merged.pdf", mergedDoc.getFileName());
		} finally {
			FileUtil.strongDelete(pdf1);
			FileUtil.strongDelete(pdf2);
		}
	}

	@Test
	public void testUpdatePages() throws ServerException, IOException, InterruptedException {
		GUIDocument doc = service.getById(7);
		doc.setId(0L);
		doc.setCustomId(null);
		doc.setIndexed(0);

		File pdf2 = new File("target/pdf2.pdf");
		try {
			copyResource("/pdf2.pdf", pdf2.getCanonicalPath());

			Map<String, File> uploadedFiles = new HashMap<>();
			uploadedFiles.put(pdf2.getName(), pdf2);

			session.getDictionary().put(UploadServlet.RECEIVED_FILES, uploadedFiles);

			GUIDocument[] createdDocs = service.addDocuments(false, UTF_8, false, doc);
			Assert.assertEquals(1, createdDocs.length);
			Assert.assertEquals(2, createdDocs[0].getPages());

			Assert.assertEquals(2, service.updatePages(createdDocs[0].getId()));
		} finally {
			FileUtil.strongDelete(pdf2);
		}
	}

	@Test
	public void testReplaceAlias() throws ServerException, IOException, InterruptedException, PersistenceException {
		DocumentManager manager = (DocumentManager) context.getBean("DocumentManager");
		DocumentHistory transaction = new DocumentHistory();
		transaction.setUser(session.getUser());
		transaction.setSession(session);

		Document alias = manager.createAlias(docDao.findById(5), folderDao.findById(1201), null, transaction);
		Assert.assertEquals(Long.valueOf(5), alias.getDocRef());
		GUIDocument newFile = service.replaceAlias(alias.getId());

		Assert.assertNull(docDao.findById(alias.getId()));
		Document newDoc = docDao.findById(newFile.getId());
		Assert.assertNotNull(newDoc);
		Assert.assertNull(newDoc.getDocRef());
		Assert.assertEquals(alias.getFileName(), newDoc.getFileName());
	}

	@Test
	public void testDeduplicate() throws ServerException, IOException, InterruptedException, PersistenceException {
		Document doc5 = docDao.findById(5);
		docDao.initialize(doc5);
		doc5.setDigest("pippo");
		docDao.store(doc5);

		Document newDoc = new Document(doc5);
		newDoc.setId(0);
		newDoc.setCustomId(null);
		newDoc.setCreation(new Date());
		newDoc.setDate(new Date());
		newDoc.setFolder(folderDao.findById(1201));
		docDao.store(newDoc);

		service.deDuplicate(null, true);

		Assert.assertNull(service.getById(doc5.getId()));
		GUIDocument dc = service.getById(newDoc.getId());
		Assert.assertNotNull(dc);
		Assert.assertEquals(1201, dc.getFolder().getId());
	}

	@Test
	public void testConvert() throws ServerException, IOException {
		GUIDocument doc = service.getById(7);
		GUIDocument conversion = service.convert(doc.getId(), doc.getFileVersion(), "pdf");
		conversion = service.getById(conversion.getId());
		Assert.assertNotNull(conversion);
		Assert.assertTrue(conversion.getFileName().endsWith(".pdf"));
	}

	@Test
	public void testIndex() throws ServerException, IOException {
		searchEngine.init();

		GUIDocument doc = service.getById(7);
		doc.setIndexed(0);
		doc.setFileName("test.txt");
		service.save(doc);

		doc = service.getById(7);
		Assert.assertEquals(0, doc.getIndexed());
		Assert.assertEquals("test.txt", doc.getFileName());

		copyResource("/New error indexing documents.eml",
				"target/repository/docs/" + doc.getId() + "/doc/" + doc.getFileVersion());

		service.indexDocuments(new Long[] { doc.getId() });
		doc = service.getById(doc.getId());
		Assert.assertEquals(1, doc.getIndexed());

		service.indexDocuments(null);
	}

	@Test
	public void testGetContentAsString() throws ServerException, IOException {
		GUIDocument doc = service.getById(7);
		doc.setIndexed(0);
		doc.setFileName("test.txt");
		service.save(doc);

		doc = service.getById(7);
		Assert.assertEquals(0, doc.getIndexed());
		Assert.assertEquals("test.txt", doc.getFileName());

		copyResource("/New error indexing documents.eml",
				"target/repository/docs/" + doc.getId() + "/doc/" + doc.getFileVersion());
		Assert.assertTrue(service.getContentAsString(doc.getId()).contains("Gracias por tu pronta respuesta"));
	}

	@Test
	public void testGetVersionsById() throws ServerException {
		GUIVersion[] versions = service.getVersionsById(1, 2);
		Assert.assertNotNull(versions);
		Assert.assertEquals(2, versions.length);

		// only the first version of the two
		versions = service.getVersionsById(1, 23);
		Assert.assertNotNull(versions);
		Assert.assertEquals(1, versions.length);

		// only the 2nd version of the two
		versions = service.getVersionsById(21, 2);
		Assert.assertNotNull(versions);
		Assert.assertEquals(1, versions.length);

		// no versions
		versions = service.getVersionsById(21, 22);
		Assert.assertNotNull(versions);
		Assert.assertEquals(0, versions.length);
	}

	@Test
	public void testDeleteVersions() throws ServerException {
		long[] ids = new long[] { 21, 22, 23 };
		GUIDocument gdoc;
		boolean exceptionHappened = false;
		try {
			gdoc = service.deleteVersions(ids);
		} catch (AssertionError | ServerException e) {
			exceptionHappened = true;
		}
		Assert.assertTrue(exceptionHappened);

		ids = new long[] { 1, 2 };
		gdoc = service.deleteVersions(ids);
		assertNotNull(gdoc);
		assertEquals(1, gdoc.getId());
	}

	@Test
	public void testGetById() throws ServerException {
		GUIDocument doc = service.getById(1);
		Assert.assertEquals(1, doc.getId());
		Assert.assertEquals("pippo", doc.getFileName());
		Assert.assertNotNull(doc.getFolder());
		Assert.assertEquals(5, doc.getFolder().getId());
		Assert.assertEquals("/", doc.getFolder().getName());

		doc = service.getById(3);
		Assert.assertEquals(3, doc.getId());
		Assert.assertEquals("test.zip", doc.getFileName());

		// Try with unexisting document
		doc = service.getById(99);
		Assert.assertNull(doc);
	}

	@Test
	public void testSave() throws Exception {
		GUIDocument doc = service.getById(1);

		doc = service.save(doc);
		Assert.assertNotNull(doc);
		Assert.assertEquals("myself", doc.getPublisher());

		doc = service.getById(3);
		Assert.assertEquals("test.zip", doc.getFileName());

		doc = service.save(doc);
		Assert.assertNotNull(doc);
	}

	@Test
	public void testUpdateLink() throws ServerException, PersistenceException {
		DocumentLink link = linkDao.findById(1);
		Assert.assertNotNull(link);
		Assert.assertEquals("test", link.getType());

		service.updateLink(1, "pippo");

		link = linkDao.findById(1);
		Assert.assertNotNull(link);
		Assert.assertEquals("pippo", link.getType());
	}

	@Test
	public void testDeleteLinks() throws ServerException, PersistenceException {
		DocumentLink link = linkDao.findById(1);
		Assert.assertNotNull(link);
		Assert.assertEquals("test", link.getType());
		link = linkDao.findById(2);
		Assert.assertNotNull(link);
		Assert.assertEquals("xyz", link.getType());

		service.deleteLinks(new long[] { 1, 2 });

		link = linkDao.findById(1);
		Assert.assertNull(link);
		link = linkDao.findById(2);
		Assert.assertNull(link);
	}

	@Test
	public void testDelete() throws ServerException, PersistenceException {
		Document doc = docDao.findById(1);
		Assert.assertNotNull(doc);
		Assert.assertEquals("pippo", doc.getFileName());
		doc = docDao.findById(2);
		Assert.assertNotNull(doc);
		Assert.assertEquals("pippo", doc.getFileName());
		Assert.assertEquals(1, doc.getDocRef().longValue());
		doc = docDao.findById(3);
		Assert.assertNotNull(doc);
		Assert.assertEquals("test.zip", doc.getFileName());

		doc = docDao.findById(1);
		Assert.assertNotNull(doc);
		service.delete(new long[] { 2, 3 });

		doc = docDao.findById(1);
		Assert.assertNotNull(doc);
		doc = docDao.findById(2);
		Assert.assertNull(doc);
		doc = docDao.findById(3);
		Assert.assertNull(doc);
	}

	@Test
	public void testDeleteNotes() throws ServerException {
		List<DocumentNote> notes = noteDao.findByDocId(1, "1.0");
		Assert.assertNotNull(notes);
		Assert.assertEquals(2, notes.size());
		Assert.assertEquals("message for note 1", notes.get(0).getMessage());

		service.deleteNotes(new long[] { 1 });

		notes = noteDao.findByDocId(1, "1.0");
		Assert.assertNotNull(notes);
		Assert.assertEquals(1, notes.size());
	}

	@Test
	public void testAddNote() throws ServerException, PersistenceException {
		List<DocumentNote> notes = noteDao.findByDocId(1L, "1.0");
		Assert.assertNotNull(notes);
		Assert.assertEquals(2, notes.size());

		long noteId = service.addNote(1L, "pippo");

		DocumentNote note = noteDao.findById(noteId);
		Assert.assertNotNull(note);
		Assert.assertEquals("pippo", note.getMessage());

		notes = noteDao.findByDocId(1L, "1.0");
		Assert.assertNotNull(notes);
		Assert.assertEquals(2, notes.size());

		boolean exceptionHappened = false;
		try {
			// add note to a non existent doc
			service.addNote(21L, "Midnight Rain");
		} catch (ServerException e) {
			exceptionHappened = true;
		}
		Assert.assertTrue(exceptionHappened);
	}

	@Test
	public void testLock() throws ServerException, PersistenceException {
		Document doc = docDao.findById(1);
		Assert.assertNotNull(doc);
		Assert.assertEquals(3L, doc.getLockUserId().longValue());
		doc = docDao.findById(2);
		Assert.assertNotNull(doc);
		Assert.assertEquals(3L, doc.getLockUserId().longValue());

		service.unlock(new long[] { 1, 2 });

		doc = docDao.findDocument(1);
		Assert.assertNotNull(doc);
		Assert.assertNull(doc.getLockUserId());
		doc = docDao.findDocument(2);
		Assert.assertNotNull(doc);
		Assert.assertNull(doc.getLockUserId());

		service.lock(new long[] { 1, 2 }, "comment");

		doc = docDao.findDocument(1);
		Assert.assertEquals(1L, doc.getLockUserId().longValue());
		doc = docDao.findDocument(2);
		Assert.assertEquals(1L, doc.getLockUserId().longValue());
	}

	@Test
	public void testLinkDocuments() throws ServerException {
		service.linkDocuments(new long[] { 1, 2 }, new long[] { 3, 4 });

		DocumentLink link = linkDao.findByDocIdsAndType(1, 3, "default");
		Assert.assertNotNull(link);
		link = linkDao.findByDocIdsAndType(1, 4, "default");
		Assert.assertNotNull(link);
		link = linkDao.findByDocIdsAndType(2, 3, "default");
		Assert.assertNotNull(link);
		link = linkDao.findByDocIdsAndType(2, 4, "default");
		Assert.assertNotNull(link);
		link = linkDao.findByDocIdsAndType(3, 4, "default");
		Assert.assertNull(link);
	}

	@Test
	public void testRestore() throws ServerException, PersistenceException {
		docDao.delete(4);
		Assert.assertNull(docDao.findById(4));
		service.restore(new Long[] { 4L }, 5);
		Assert.assertNotNull(docDao.findById(4));
		Assert.assertNotNull(docDao.findById(4));
		Assert.assertEquals(5L, docDao.findById(4).getFolder().getId());
	}

	@Test
	public void testBookmarks() throws ServerException, PersistenceException {
		service.addBookmarks(new long[] { 1, 2 }, 0);

		Bookmark book = bookDao.findByUserIdAndDocId(1, 1);
		Assert.assertNotNull(book);
		book = bookDao.findByUserIdAndDocId(1, 2);
		Assert.assertNotNull(book);

		GUIBookmark bookmark = new GUIBookmark();
		bookmark.setId(book.getId());
		bookmark.setName("bookmarkTest");
		bookmark.setDescription("bookDescr");

		service.updateBookmark(bookmark);
		book = bookDao.findById(bookmark.getId());
		Assert.assertNotNull(book);
		Assert.assertEquals("bookmarkTest", book.getTitle());
		Assert.assertEquals("bookDescr", book.getDescription());

		service.deleteBookmarks(new long[] { bookmark.getId() });

		book = bookDao.findById(1);
		Assert.assertNull(book);
		book = bookDao.findById(2);
		Assert.assertNull(book);

		// delete an already deleted bookmark
		service.deleteBookmarks(new long[] { bookmark.getId() });

		// Add bookmarks on folders
		service.addBookmarks(new long[] { 6, 7 }, Bookmark.TYPE_FOLDER);

		// Add bookmarks on non existent documents
		boolean exceptionHappened = false;
		try {
			service.addBookmarks(new long[] { 21, 22 }, Bookmark.TYPE_DOCUMENT);
		} catch (ServerException e) {
			exceptionHappened = true;
		}
		Assert.assertTrue(exceptionHappened);
	}

	@Test
	public void testMarkHistoryAsRead() throws ServerException {
		List<DocumentHistory> histories = documentHistoryDao.findByUserIdAndEvent(1, "data test 01", null);
		Assert.assertEquals(2, histories.size());
		Assert.assertEquals(1, histories.get(0).getIsNew());
		Assert.assertEquals(1, histories.get(1).getIsNew());

		service.markHistoryAsRead("data test 01");

		histories = documentHistoryDao.findByUserIdAndEvent(1, "data test 01", null);
		Assert.assertEquals(2, histories.size());
		Assert.assertEquals(0, histories.get(0).getIsNew());
		Assert.assertEquals(0, histories.get(1).getIsNew());
	}

	@Test
	public void testIndexable() throws ServerException, PersistenceException {
		Document doc1 = docDao.findById(1);
		Assert.assertNotNull(doc1);
		Assert.assertEquals(AbstractDocument.INDEX_INDEXED, doc1.getIndexed());
		Document doc2 = docDao.findById(2);
		Assert.assertNotNull(doc2);
		Assert.assertEquals(AbstractDocument.INDEX_TO_INDEX, doc2.getIndexed());
		Document doc3 = docDao.findById(3);
		Assert.assertNotNull(doc3);
		Assert.assertEquals(AbstractDocument.INDEX_INDEXED, doc3.getIndexed());
		service.markUnindexable(new long[] { 1, 2, 3 });

		doc1 = docDao.findById(1);
		Assert.assertNotNull(doc1);
		Assert.assertEquals(AbstractDocument.INDEX_SKIP, doc1.getIndexed());
		doc2 = docDao.findById(2);
		Assert.assertNotNull(doc2);
		Assert.assertEquals(AbstractDocument.INDEX_SKIP, doc2.getIndexed());
		doc3 = docDao.findById(3);
		Assert.assertNotNull(doc3);
		Assert.assertEquals(AbstractDocument.INDEX_SKIP, doc3.getIndexed());

		service.markIndexable(new long[] { 1, 3 }, AbstractDocument.INDEX_TO_INDEX);

		doc1 = docDao.findById(1);
		Assert.assertNotNull(doc1);
		Assert.assertEquals(AbstractDocument.INDEX_TO_INDEX, doc1.getIndexed());
		doc3 = docDao.findById(3);
		Assert.assertNotNull(doc3);
		Assert.assertEquals(AbstractDocument.INDEX_TO_INDEX, doc3.getIndexed());
	}

	@Test
	public void testCountDocuments() throws ServerException {
		Assert.assertEquals(7, service.countDocuments(new long[] { 5 }, 0));
		Assert.assertEquals(0, service.countDocuments(new long[] { 5 }, 3));
	}

	@Test
	public void testValidate() throws PersistenceException, ServerException {
		/*
		 * validate a simple document (no template assigned)
		 */
		GUIDocument gdoc = service.getById(1);
		service.validate(gdoc);

		// Update the document add a template
		Document doc = docDao.findDocument(6);
		docDao.initialize(doc);

		Template template = templateDao.findById(5L);
		templateDao.initialize(template);

		// Set the validator for attribute "attr1" to be email format
		template.getAttribute("attr1").setValidation(
				"#if(!$value.matches('^([\\w-\\.]+){1,64}@([\\w&&[^_]]+){2,255}.[a-z]{2,}$')) $error.setDescription($I18N.get('invalidformat')); #end");
		templateDao.store(template);

		doc.setTemplate(template);
		docDao.store(doc);

		gdoc = service.getById(6);

		/*
		 * validate a document with template assigned
		 */

		// // The value of attribute "attr1" is: "val1" so this should produce
		// an error

		try {
			service.validate(gdoc);
			fail("Expected exception was not thrown");
		} catch (ServerException e) {
			String lcal = I18N.message("invalidformat");
			assertTrue(e.getMessage().contains("attr1"));
			assertTrue(e.getMessage().contains(lcal));
		}

		/*
		 * validate a document with template assigned
		 */

		// Update the document add a template
		doc = docDao.findDocument(6);
		docDao.initialize(doc);

		// update the attribute and set the value as an email format
		Attribute xxx = new Attribute();
		xxx.setValue("test.xx@acme.de");
		doc.setAttribute("attr1", xxx);
		docDao.store(doc);

		gdoc = service.getById(6);

		// The value of attribute "attr1" is "test.xx@acme.de" this will
		// validate correctly
		service.validate(gdoc);
	}

	@Test
	public void testSendAsEmail() throws Exception {
		// Send the email as download ticket
		GUIEmail gmail = service.extractEmail(5, "1.0");
		log.info(gmail.getFrom().getEmail());
		gmail.setDocIds(new long[] { 5 });

		List<GUIContact> tos = new ArrayList<>();
		GUIContact gc = new GUIContact("Kenneth", "Botterill", "ken-botterill@acme.com");
		tos.add(gc);

		GUIContact[] carr = new GUIContact[] {};
		gmail.setTos(tos.toArray(carr));

		tos = new ArrayList<>();
		gc = new GUIContact("Riley", "Arnold", "riley-arnold@acme.com");
		tos.add(gc);
		gmail.setBccs(tos.toArray(carr));

		tos = new ArrayList<>();
		gc = new GUIContact("Scout", "Marsh", "s.marsh@acme.com");
		tos.add(gc);
		gmail.setCcs(tos.toArray(carr));
		gmail.setSendAsTicket(true);

		String retvalue = service.sendAsEmail(gmail, "en-US");
		log.info("returned message: {}", retvalue);
		assertEquals("ok", retvalue);

		// Send the email with attached .zip
		gmail = service.extractEmail(5, "1.0");
		log.info(gmail.getFrom().getEmail());
		gmail.setDocIds(new long[] { 5 });

		tos = new ArrayList<>();
		gc = new GUIContact("Kenneth", "Botterill", "ken-botterill@acme.com");
		tos.add(gc);

		carr = new GUIContact[] {};
		gmail.setTos(tos.toArray(carr));
		gmail.setBccs(null);
		gmail.setCcs(null);

		gmail.setSendAsTicket(false);
		gmail.setZipCompression(true);

		retvalue = service.sendAsEmail(gmail, "en-US");
		log.info("returned message: {}", retvalue);
		assertEquals("ok", retvalue);

		// Send the email with attached file
		gmail.setZipCompression(false);

		retvalue = service.sendAsEmail(gmail, "en-US");
		log.info("returned message: {}", retvalue);
		assertEquals("ok", retvalue);

		// Send the email with attached file as pdf conversion
		gmail.setPdfConversion(true);
		retvalue = service.sendAsEmail(gmail, "en-US");
		log.info("returned message: {}", retvalue);
		assertEquals("ok", retvalue);
	}

	@Test
	public void testGetNotes() throws ServerException {
		// test on a non existent doc
		try {
			service.getNotes(600, "1.0", null);
			fail("Expected exception was not thrown");
		} catch (ServerException e) {
			// nothing to do
		}

		// test on a doc without notes
		GUIDocumentNote[] notes = service.getNotes(6, "1.0", null);
		assertEquals(0, notes.length);

		// get a document with a single note
		notes = service.getNotes(4, "1.0", null);
		assertEquals(1, notes.length);

		notes = service.getNotes(4, null, null);
		assertEquals(1, notes.length);

	}

	@Test
	public void testUpdateNote() throws ServerException {
		GUIDocumentNote[] notes = service.getNotes(4, null, null);
		assertEquals(1, notes.length);
		assertEquals("message for note 3", notes[0].getMessage());

		service.updateNote(4, notes[0].getId(), "updated message");
		GUIDocumentNote[] notes2 = service.getNotes(4, null, null);
		assertEquals(1, notes2.length);
		assertEquals(notes[0].getId(), notes2[0].getId());
		assertEquals("updated message", notes2[0].getMessage());
	}

	@Test
	public void testSaveNotes() throws ServerException {
		boolean exceptionHappened = false;
		try {
			List<GUIDocumentNote> notes = new ArrayList<>();
			service.saveNotes(888, notes.toArray(new GUIDocumentNote[] {}), null);
		} catch (ServerException e) {
			exceptionHappened = true;
		}
		Assert.assertTrue(exceptionHappened);

		List<GUIDocumentNote> notes = new ArrayList<>();
		GUIDocumentNote gdn01 = new GUIDocumentNote();
		gdn01.setDocId(5);
		gdn01.setMessage("Vigilante Shit");
		GUIDocumentNote gdn02 = new GUIDocumentNote();
		gdn02.setDocId(5);
		gdn02.setMessage("Karma");
		gdn02.setRecipient("Kenneth Botterill");
		gdn02.setRecipientEmail("ken-botterill@acme.com");
		notes.add(gdn01);
		notes.add(gdn02);
		service.saveNotes(5, notes.toArray(new GUIDocumentNote[] {}), null);
	}

	@Test
	public void testBulkUpdate() throws ParseException, PersistenceException {

		long[] ids = new long[] { 2, 3, 4 };
		GUIDocument vo = new GUIDocument();
		vo.setPublished(1);

		String sDate1 = "10-21-2022";
		Date date1 = new SimpleDateFormat("MM-dd-yyyy").parse(sDate1);
		String sDate2 = "12-31-2089";
		Date date2 = new SimpleDateFormat("MM-dd-yyyy").parse(sDate2);

		vo.setStartPublishing(date1);
		vo.setStopPublishing(date2);
		vo.setLanguage("en");
		vo.setTags(new String[] { "Maroon", "Anti-Hero", "Karma" });
		vo.setTemplateId(5L);

		// set attributes
		GUIAttribute[] attributes = new GUIAttribute[1];
		GUIAttribute gat = new GUIAttribute();
		gat.setName("attr1");
		gat.setType(0);
		gat.setStringValue("Snow on the Beach");

		attributes[0] = gat;
		vo.setAttributes(attributes);

		try {
			GUIDocument[] gdocs = service.bulkUpdate(ids, vo, true);
			assertNotNull(gdocs);
			assertTrue(gdocs.length > 0);
			assertNotNull(gdocs[0].getTags());
			assertTrue(gdocs[0].getTags().length == 3);

			GUIAttribute gatX = gdocs[0].getAttribute("attr1");
			assertNotNull(gatX);
		} catch (ServerException e) {
			fail("Unexpected exception was thrown");
		}

		// Test with a doc locked

		Document doc = docDao.findDocument(5);
		docDao.initialize(doc);
		doc.setStatus(AbstractDocument.DOC_CHECKED_OUT);
		docDao.store(doc);

		ids = new long[] { 5, 6 };
		vo = new GUIDocument();
		vo.setPublished(0);

		try {
			GUIDocument[] gdocs = service.bulkUpdate(ids, vo, true);
			assertNotNull(gdocs);
			assertTrue(gdocs.length > 0);

			// only one document updated because 1 was locked (checked-out)
			assertEquals(1, gdocs.length);
		} catch (ServerException e) {
			fail("Unexpected exception was thrown");
		}
	}

	@Test
	public void testExtractEmail() {

		// test with document that is not an email (wrong or no extension)
		try {
			service.extractEmail(4, null);
			fail("Expected exception was not thrown");
		} catch (ServerException e) {
			// nothing to do
		}

		// test with document that is a .msg email file
		try {
			service.extractEmail(6, null);
		} catch (ServerException e) {
			fail("Unexpected exception was thrown");
		}
	}

	@Test
	public void testSaveEmailAttachment() {

		// try to extract an attachment from a non email document
		try {
			service.saveEmailAttachment(4, "testDocVer", "data.sql");
			fail("Expected exception was not thrown");
		} catch (ServerException e) {
			// nothing to do
		}

		// try to save an attachment that is not present in the document
		try {
			service.saveEmailAttachment(5, "1.0", "data.sql");
			fail("Expected exception was not thrown");
		} catch (ServerException e) {
			// nothing to do
		}

		// try to save an attachment that is not present in the document
		try {
			service.saveEmailAttachment(7, "1.0", "2022-01-04_15h54_11.png");
		} catch (ServerException e) {
			fail("Unexpected exception was thrown");
		}
	}

}