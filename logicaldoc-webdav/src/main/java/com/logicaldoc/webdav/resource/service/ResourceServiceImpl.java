package com.logicaldoc.webdav.resource.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logicaldoc.core.PersistenceException;
import com.logicaldoc.core.PersistentObject;
import com.logicaldoc.core.document.Document;
import com.logicaldoc.core.document.DocumentEvent;
import com.logicaldoc.core.document.DocumentHistory;
import com.logicaldoc.core.document.DocumentManager;
import com.logicaldoc.core.document.Version;
import com.logicaldoc.core.document.dao.DocumentDAO;
import com.logicaldoc.core.document.dao.VersionDAO;
import com.logicaldoc.core.folder.Folder;
import com.logicaldoc.core.folder.FolderDAO;
import com.logicaldoc.core.folder.FolderEvent;
import com.logicaldoc.core.folder.FolderHistory;
import com.logicaldoc.core.security.Tenant;
import com.logicaldoc.core.security.User;
import com.logicaldoc.core.security.dao.UserDAO;
import com.logicaldoc.core.store.Storer;
import com.logicaldoc.util.Context;
import com.logicaldoc.webdav.context.ImportContext;
import com.logicaldoc.webdav.exception.DavResourceIOException;
import com.logicaldoc.webdav.resource.model.Resource;
import com.logicaldoc.webdav.resource.model.ResourceImpl;
import com.logicaldoc.webdav.session.WebdavSession;

/**
 * Base implementation of a ResourceService
 * 
 * @author Sebastian Wenzky
 */
public class ResourceServiceImpl implements ResourceService {

	private static final long serialVersionUID = 1L;

	protected static Logger log = LoggerFactory.getLogger(ResourceServiceImpl.class);

	private DocumentDAO documentDAO;

	private VersionDAO versionDAO;

	private FolderDAO folderDAO;

	private DocumentManager documentManager;

	private Storer storer;

	private UserDAO userDAO;

	public void setUserDAO(UserDAO userDAO) {
		this.userDAO = userDAO;
	}

	public void setDocumentDAO(DocumentDAO documentDAO) {
		this.documentDAO = documentDAO;
	}

	public void setDocumentManager(DocumentManager documentManager) {
		this.documentManager = documentManager;
	}

	public ResourceServiceImpl() {
	}

	private Resource marshallFolder(Folder folder, long userId, WebdavSession session) throws PersistenceException {
		Resource resource = new ResourceImpl();
		resource.setID(String.valueOf(folder.getId()));
		resource.setContentLength(0L);
		resource.setName(folder.getName());
		resource.setLastModified(folder.getLastModified());

		resource.setETag(String.format("f-%d_%d", folder.getId(), folder.getRecordVersion()));

		resource.setSession(session);
		resource.isFolder(true);
		if (folder.getType() == Folder.TYPE_WORKSPACE)
			resource.isWorkspace(true);

		if (session != null && (Long) session.getObject("id") != null) {
			resource.setRequestedPerson((Long) session.getObject("id"));
		}

		return resource;
	}

	private Resource marshallDocument(Document document, WebdavSession session) {

		Resource resource = new ResourceImpl();
		resource.setID(String.valueOf(document.getId()));
		resource.setName(document.getFileName());
		resource.setContentLength(document.getFileSize());
		resource.setCreationDate(document.getCreation());
		resource.setLastModified(document.getDate());
		resource.isFolder(false);
		resource.setCheckedOut(
				document.getStatus() == Document.DOC_CHECKED_OUT || document.getStatus() == Document.DOC_LOCKED);
		resource.setVersionLabel(document.getVersion());
		resource.setAuthor(document.getPublisher());
		resource.setDocRef(document.getDocRef());
		resource.setFolderID(String.valueOf(document.getFolder().getId()));

		// this is for getetag property
		resource.setETag(String.format("d-%d_%s", document.getId(), document.getVersion()));

		resource.setSession(session);
		resource.setLocked(
				document.getStatus() == Document.DOC_LOCKED || document.getStatus() == Document.DOC_CHECKED_OUT);
		resource.setLockUser(document.getLockUser());

		if (session != null && (Long) session.getObject("id") != null) {
			resource.setRequestedPerson((Long) session.getObject("id"));
		}

		return resource;
	}

	public List<Resource> getChildResources(Resource parentResource) {
		FolderDAO folderDAO = (FolderDAO) Context.get().getBean(FolderDAO.class);
		List<Resource> resourceList = new LinkedList<Resource>();
		final Long folderID = Long.parseLong(parentResource.getID());
		boolean hasAccess = folderDAO.isReadEnabled(folderID, parentResource.getRequestedPerson());

		if (hasAccess == false) {
			// Check if the folder is a root and in that case we mark it as
			// readable.
			Folder folder = null;
			try {
				folder = folderDAO.findFolder(folderID);
			} catch (PersistenceException e) {
				log.error(e.getMessage(), e);
			}
			if (folder != null && folder.equals(folderDAO.findRoot(folder.getTenantId())))
				hasAccess = true;
		}

		if (hasAccess == false)
			return resourceList;

		User user = null;
		try {
			user = userDAO.findById(parentResource.getRequestedPerson());
			userDAO.initialize(user);
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
		}

		// Find children visible by the current user
		Collection<Folder> folders = folderDAO.findChildren(folderID, parentResource.getRequestedPerson());
		if (folders != null) {
			for (Folder currentFolder : folders) {
				if (currentFolder.getHidden() == 0)
					try {
						resourceList.add(marshallFolder(currentFolder, parentResource.getRequestedPerson(),
								parentResource.getSession()));
					} catch (PersistenceException e) {
						log.error(e.getMessage(), e);
					}
			}
		}

		Collection<Document> documents = documentDAO.findByFolder(folderID, null);
		for (Iterator<Document> iterator = documents.iterator(); iterator.hasNext();) {
			Document document = iterator.next();
			try {
				checkPublished(user, document);
			} catch (Throwable t) {
				continue;
			}
			resourceList.add(marshallDocument(document, parentResource.getSession()));
		}

		return resourceList;
	}

	public Resource getResource(String requestPath, WebdavSession session) throws DavException {
		log.trace("Find DAV resource: {}", requestPath);

		if (session == null)
			throw new DavException(DavServletResponse.SC_FORBIDDEN, "No WebDAV session");

		long userId = 0;
		String currentStablePath = "";
		String name = "";
		try {
			userId = (Long) session.getObject("id");
			if (requestPath == null)
				requestPath = "/";

			requestPath = requestPath.replace("/store", "");

			if (requestPath.length() > 0 && requestPath.substring(0, 1).equals("/"))
				requestPath = requestPath.substring(1);

			String path = "/" + requestPath;
			currentStablePath = path;
			name = null;
			int lastidx = path.lastIndexOf("/");
			if (lastidx > -1) {
				name = path.substring(lastidx + 1, path.length());
				path = path.substring(0, lastidx + 1);
			}

			Folder folder = null;

			if (path.equals("/") && StringUtils.isEmpty(name)) {
				folder = folderDAO.findRoot(session.getTenantId());
			} else
				folder = folderDAO.findByPathExtended(path + "/" + name, session.getTenantId());

			// if this resource request is a folder
			if (folder != null)
				return marshallFolder(folder, userId, session);
		} catch (Exception e) {
		}

		Resource parentFolder = this.getParentResource(currentStablePath, userId, session);

		Collection<Document> docs = documentDAO.findByFileNameAndParentFolderId(Long.parseLong(parentFolder.getID()),
				name, null, session.getTenantId(), null);

		if (docs.isEmpty())
			return null;

		Document document = docs.iterator().next();
		boolean hasAccess = folderDAO.isReadEnabled(document.getFolder().getId(), userId);

		if (hasAccess == false)
			throw new DavException(DavServletResponse.SC_FORBIDDEN,
					"You have no appropriated rights to read this document");

		User user;
		try {
			user = userDAO.findById(userId);
		} catch (PersistenceException e) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, e.getMessage());
		}
		userDAO.initialize(user);
		checkPublished(user, document);

		return marshallDocument(document, session);
	}

	/**
	 * @see ResourceService#getChildByName(Resource, String)
	 */
	public Resource getParentResource(String resourcePath, long userId, WebdavSession session) {

		log.debug("Find parent DAV resource: {}", resourcePath);

		long tenantId = Tenant.DEFAULT_ID;
		if (session != null) {
			tenantId = session.getTenantId();
		} else {
			try {
				User user = userDAO.findById(userId);
				tenantId = user.getTenantId();
			} catch (PersistenceException e) {
				log.error(e.getMessage(), e);
				return null;
			}
		}

		resourcePath = resourcePath.replaceFirst("/store", "").replaceFirst("/vstore", "");
		if (!resourcePath.startsWith("/"))
			resourcePath = "/" + resourcePath;

		String name = "";
		resourcePath = resourcePath.substring(0, resourcePath.lastIndexOf('/'));
		if (!resourcePath.isEmpty()) {
			name = resourcePath.substring(resourcePath.lastIndexOf('/'));
			resourcePath = resourcePath.substring(0, resourcePath.lastIndexOf('/'));
		}

		resourcePath = resourcePath + "/";
		if (name.startsWith("/"))
			name = name.substring(1);

		log.debug("Find DMS resource {} in path {}", name, resourcePath);

		Folder folder = null;
		if ("/".equals(resourcePath.trim()) && "".equals(name))
			folder = folderDAO.findRoot(tenantId);
		else
			folder = folderDAO.findByPathExtended(resourcePath + "/" + name, tenantId);

		try {
			return marshallFolder(folder, userId, session);
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	public Resource createResource(Resource parentResource, String name, boolean isCollection, ImportContext context,
			WebdavSession session) throws DavException {

		if(session==null)
			throw new DavException(DavServletResponse.SC_FORBIDDEN, "No WebDAV session");
		
		Folder parentFolder;
		try {
			parentFolder = folderDAO.findById(Long.parseLong(parentResource.getID()));
		} catch (PersistenceException e1) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, e1);
		}
		String sid = null;
		if (session != null)
			sid = (String) session.getObject("sid");

		long rootId = folderDAO.findRoot(parentFolder.getTenantId()).getId();

		if (parentFolder.getId() == rootId)
			throw new DavException(DavServletResponse.SC_FORBIDDEN, "Cannot write in the root.");

		if (isCollection) {
			// check permission to add folder
			boolean addChildEnabled = parentResource.isAddChildEnabled();
			if (!addChildEnabled) {
				throw new DavException(DavServletResponse.SC_FORBIDDEN, "Add Folder not granted to this user");
			}

			User user = (User) session.getObject("user");
			// Add a folder history entry
			FolderHistory transaction = new FolderHistory();
			transaction.setUser(user);
			transaction.setSessionId(sid);

			Folder newFolder = new Folder(name);
			newFolder.setTenantId(session.getTenantId());
			Folder createdFolder;
			try {
				createdFolder = folderDAO.create(parentFolder, newFolder, true, transaction);
			} catch (PersistenceException e) {
				log.error(e.getMessage(), e);
				throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			}

			try {
				return this.marshallFolder(createdFolder, parentResource.getRequestedPerson(), session);
			} catch (PersistenceException e1) {
				throw new DavException(DavServletResponse.SC_FORBIDDEN, e1);
			}
		}

		// check permission to add document
		boolean writeEnabled = parentResource.isWriteEnabled();
		if (!writeEnabled) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, "Write Access not granted to this user");
		}

		User user = null;
		try {
			user = userDAO.findById(parentResource.getRequestedPerson());
		} catch (PersistenceException e1) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, e1);
		}

		InputStream is = context.getInputStream();
		try {
			try {
				// Create the document history event
				DocumentHistory transaction = new DocumentHistory();
				transaction.setSessionId(sid);
				transaction.setEvent(DocumentEvent.STORED.toString());
				transaction.setComment("");
				transaction.setUser(user);

				Document doc = new Document();
				doc.setFileName(name);
				doc.setFolder(parentFolder);
				doc.setLocale(user.getLocale());
				doc.setTenantId(session.getTenantId());

				documentManager.create(is, doc, transaction);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			} finally {
				is.close();
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}

		return null;
	}

	public void updateResource(Resource resource, ImportContext context, WebdavSession session) throws DavException {
		try {
			User user = userDAO.findById(resource.getRequestedPerson());
			Document document = documentDAO.findById(Long.parseLong(resource.getID()));
			String sid = (String) session.getObject("sid");
			// verify the write permission on the parent folder
			Resource parent = getParentResource(resource);
			if (!parent.isWriteEnabled())
				throw new DavException(DavServletResponse.SC_FORBIDDEN, "No rights to write resource.");

			if ((document.getStatus() == Document.DOC_CHECKED_OUT || document.getStatus() == Document.DOC_LOCKED)
					&& (user.getId() != document.getLockUserId() && !"admin".equals(user.getUsername()))) {
				throw new DavException(DavServletResponse.SC_FORBIDDEN, "Current user didn't locked the document");
			}

			if (document.getImmutable() == 1 && !user.isMemberOf("admin"))
				throw new DavException(DavServletResponse.SC_FORBIDDEN, "The document is immutable");

			// Create the document history event
			DocumentHistory transaction = new DocumentHistory();
			transaction.setSessionId(sid);
			transaction.setUser(user);
			transaction.setComment("");

			documentManager.checkin(Long.parseLong(resource.getID()), context.getInputStream(), resource.getName(),
					false, null, transaction);
		} catch (DavException de) {
			throw de;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	public Resource getChildByName(Resource parentResource, String name) {
		try {
			Folder parentFolder = folderDAO.findById(Long.parseLong(parentResource.getID()));
			Collection<Document> docs = documentDAO.findByFileNameAndParentFolderId(parentFolder.getId(), name, null,
					parentFolder.getTenantId(), null);
			User user = userDAO.findById(parentResource.getRequestedPerson());
			userDAO.initialize(user);

			if (!docs.isEmpty()) {
				for (Document document : docs) {
					if (name.equals(document.getFileName())) {
						return marshallDocument(document, parentResource.getSession());
					}
				}
			}
			return null;
		} catch (Throwable e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	public boolean resourceExists(Resource resource) {
		return true;
	}

	public Resource updateResource(Resource resource) {
		return null;
	}

	public Resource move(Resource source, Resource destination, WebdavSession session) throws DavException {
		String sid = (String) session.getObject("sid");
		if (source.isWorkspace()) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, "Cannot move a workspace");
		} else if (source.isFolder()) {
			return folderRenameOrMove(source, destination, session, sid);
		} else {
			return fileRenameOrMove(source, destination, session, sid);
		}
	}

	private Resource fileRenameOrMove(Resource source, Resource destination, WebdavSession session, String sid)
			throws DavException {
		// The source is a file so the requested operation can be: file
		// rename/file move

		// if the destination is null we can't do anything
		if (destination == null)
			throw new UnsupportedOperationException();

		// verify the write permission on source folders
		Resource parentFolder = getParentResource(source);
		Document document = null;
		User user = null;
		try {
			document = documentDAO.findById(Long.parseLong(source.getID()));
			user = userDAO.findById(source.getRequestedPerson());
		} catch (Throwable e) {
			log.error(e.getMessage(), e);
			throw new DavException(DavServletResponse.SC_FORBIDDEN, e);
		}

		if (document.getImmutable() == 1 && !user.isMemberOf("admin"))
			throw new DavException(DavServletResponse.SC_FORBIDDEN, "The document is immutable");

		documentDAO.initialize(document);

		// Create the document history event
		DocumentHistory transaction = new DocumentHistory();
		transaction.setSessionId(sid);
		transaction.setUser(user);

		if (!source.getName().equals(document.getFileName())) {
			boolean renameEnabled = parentFolder.isRenameEnabled();
			if (!renameEnabled)
				throw new DavException(DavServletResponse.SC_FORBIDDEN, "Rename Rights not granted to this user");

			// we are doing a file rename
			try {
				documentManager.rename(document.getId(), source.getName(), transaction);
			} catch (Throwable e) {
				log.warn(e.getMessage(), e);
			}
		} else {
			// moving the document to another folder
			// verify the addchild permission on destination folder
			boolean addchildEnabled = destination.isAddChildEnabled();
			if (!addchildEnabled)
				throw new DavException(DavServletResponse.SC_FORBIDDEN, "AddChild Rights not granted to this user");

			// verify the MOVE permission on parent folder
			boolean moveEnabled = parentFolder.isMoveEnabled();
			if (!moveEnabled)
				throw new DavException(DavServletResponse.SC_FORBIDDEN, "Delete Rights not granted to this user");

			try {
				Folder folder = folderDAO.findById(Long.parseLong(destination.getID()));
				if (document.getDocRef() != null)
					transaction.setEvent(DocumentEvent.SHORTCUT_MOVED.toString());
				documentManager.moveToFolder(document, folder, transaction);
			} catch (Throwable e) {
				throw new DavException(DavServletResponse.SC_FORBIDDEN, e);
			}
		}

		return this.marshallDocument(document, session);
	}

	private Resource folderRenameOrMove(Resource source, Resource destination, WebdavSession session, String sid)
			throws DavException {

//		log.info("Rename or Move folder {} to {}", source.getPath(), destination.getPath());

		Folder currentFolder;
		try {
			currentFolder = folderDAO.findById(Long.parseLong(source.getID()));
		} catch (PersistenceException e) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, e);
		}

		if (currentFolder.getType() == Folder.TYPE_WORKSPACE)
			throw new DavException(DavServletResponse.SC_FORBIDDEN, "Cannot move nor rename a workspace");

		folderDAO.initialize(currentFolder);

		long currentParentFolder = currentFolder.getParentId();
		long destinationParentFolder = Long.parseLong(destination.getID());

		try {
			// distinction between folder move and folder rename
			if (currentParentFolder != destinationParentFolder) {
				// Folder Move, verify the addchild permission on destination
				// folders
				boolean addchildEnabled = destination.isAddChildEnabled();
				if (!addchildEnabled)
					throw new DavException(DavServletResponse.SC_FORBIDDEN, "AddChild Rights not granted to this user");

				// check the delete permission on the parent of the source to
				// move
				if (!source.isDeleteEnabled())
					throw new DavException(DavServletResponse.SC_FORBIDDEN, "No rights to delete resource.");

				User user = (User) session.getObject("user");
				// Add a folder history entry
				FolderHistory transaction = new FolderHistory();
				transaction.setSessionId(sid);
				transaction.setUser(user);

				// we are doing a folder move
				try {
					Folder destParentFolder = folderDAO.findById(destinationParentFolder);
					folderDAO.initialize(destParentFolder);
					folderDAO.move(currentFolder, destParentFolder, transaction);
				} catch (Throwable e) {
					log.warn(e.getMessage(), e);
					throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, "Error during Folder Move");
				}

				return this.marshallFolder(currentFolder, source.getRequestedPerson(), session);
			} else {
				if (!source.isRenameEnabled())
					throw new DavException(DavServletResponse.SC_FORBIDDEN, "Rename Rights not granted to this user");

				// Folder Rename
				currentFolder.setName(source.getName());

				User user = (User) session.getObject("user");

				// Add a folder history entry
				FolderHistory transaction = new FolderHistory();
				transaction.setUser(user);
				transaction.setEvent(FolderEvent.RENAMED.toString());
				transaction.setSessionId(sid);
				try {
					folderDAO.store(currentFolder, transaction);
				} catch (PersistenceException e) {
					log.warn(e.getMessage(), e);
				}

				if (destination != null)
					currentFolder.setParentId(Long.parseLong(destination.getID()));
				try {
					folderDAO.store(currentFolder);
				} catch (PersistenceException e) {
					log.error(e.getMessage(), e);
					throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
				}
				return this.marshallFolder(currentFolder, source.getRequestedPerson(), session);
			}
		} catch (PersistenceException e) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, e);
		}
	}

	public void deleteResource(Resource resource, WebdavSession session) throws DavException {
		String sid = (String) session.getObject("sid");
		Folder folder = null;
		User user = null;

		try {
			folder = folderDAO.findById(Long.parseLong(resource.getID()));
			user = userDAO.findById(resource.getRequestedPerson());

			if (resource.isFolder() && folder != null && (folder.getType() == Folder.TYPE_WORKSPACE)) {
				throw new DavException(DavServletResponse.SC_FORBIDDEN, "Cannot delete a workspace.");
			} else if (resource.isFolder()) {
				if (!resource.isDeleteEnabled())
					throw new DavException(DavServletResponse.SC_FORBIDDEN, "No rights to delete resource.");

				FolderHistory transaction = new FolderHistory();
				transaction.setUser(user);
				transaction.setSessionId(sid);
				transaction.setUser(user);
				transaction.setEvent(FolderEvent.DELETED.toString());
				List<Folder> notDeletableFolders = folderDAO.deleteTree(folder, PersistentObject.DELETED_CODE_DEFAULT,
						transaction);

				if (notDeletableFolders.size() > 0) {
					throw new RuntimeException("Unable to delete some subfolders.");
				}
			} else if (!resource.isFolder()) {
				// verify the write permission on the parent folder
				Resource parent = getParentResource(resource);
				if (!parent.isDeleteEnabled())
					throw new DavException(DavServletResponse.SC_FORBIDDEN, "No rights to delete on parent resource.");

				DocumentHistory transaction = new DocumentHistory();
				transaction.setUser(user);
				transaction.setSessionId(sid);
				transaction.setUser(user);
				transaction.setEvent(DocumentEvent.DELETED.toString());

				if (documentDAO.findById(Long.parseLong(resource.getID())).getImmutable() == 1
						&& !user.isMemberOf("admin"))
					throw new DavException(DavServletResponse.SC_FORBIDDEN, "The document is immutable");

				// Check if there are some shortcuts associated to the
				// deleting document. All the shortcuts must be deleted.
				if (documentDAO.findAliasIds(Long.parseLong(resource.getID())).size() > 0)
					for (Long shortcutId : documentDAO.findAliasIds(Long.parseLong(resource.getID()))) {
						documentDAO.delete(shortcutId, transaction);
					}
				documentDAO.delete(Long.parseLong(resource.getID()), transaction);
			}
		} catch (DavException de) {
			throw de;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void copyResource(Resource destinationResource, Resource resource, WebdavSession session) throws DavException {
		String sid = (String) session.getObject("sid");
		User user = null;
		try {
			user = userDAO.findById(resource.getRequestedPerson());
		} catch (PersistenceException e) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, e);
		}

		long rootId = folderDAO.findRoot(user.getTenantId()).getId();

		/*
		 * Cannot write in the root
		 */
		if (destinationResource.isFolder() && Long.parseLong(destinationResource.getID()) == rootId)
			throw new DavException(DavServletResponse.SC_FORBIDDEN, "Cannot write in the root");

		if (resource.isFolder() == true) {
			throw new RuntimeException("FolderCopy not supported");
		} else {
			try {
				boolean writeEnabled = destinationResource.isWriteEnabled();
				if (!writeEnabled)
					throw new DavException(DavServletResponse.SC_FORBIDDEN, "No rights to write resource.");

				Document document = documentDAO.findById(Long.parseLong(resource.getID()));
				Folder folder = folderDAO.findById(Long.parseLong(destinationResource.getID()));

				if (document.getImmutable() == 1 && !user.isMemberOf("admin"))
					throw new DavException(DavServletResponse.SC_FORBIDDEN, "The document is immutable");

				// Create the document history event
				DocumentHistory transaction = new DocumentHistory();
				transaction.setSessionId(sid);
				transaction.setEvent(DocumentEvent.STORED.toString());
				transaction.setComment("");
				transaction.setUser(user);

				if (document.getDocRef() != null) {
					document = documentDAO.findById(document.getDocRef());
					documentManager.createAlias(document, folder, document.getDocRefType(), transaction);
				} else {
					documentManager.copyToFolder(document, folder, transaction);
				}
			} catch (DavException de) {
				log.info(de.getMessage(), de);
				throw de;
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public Resource getParentResource(Resource resource) {
		try {
			Document document = documentDAO.findById(Long.parseLong(resource.getID()));
			return this.marshallFolder(document.getFolder(), resource.getRequestedPerson(), resource.getSession());
		} catch (PersistenceException e) {
			log.error(e.getMessage(), e);
			return null;
		}
	}

	@Override
	public void streamIn(Resource resource, InputStream is) {
		throw new AbstractMethodError();
	}

	@Override
	public InputStream streamOut(Resource resource) {

		if (!resource.isDownloadEnabled())
			throw new DavResourceIOException("The user doesn't have the download permission");

		String version = resource.getVersionLabel();
		Document document = null;
		try {
			document = documentDAO.findById(Long.parseLong(resource.getID()));
		} catch (PersistenceException e1) {
			log.error(e1.getMessage(), e1);
			return null;
		}
		if (document == null) {
			// Document not found
			return new ByteArrayInputStream(new String("not found").getBytes());
		}

		if (document.getVersion() != null && document.getVersion().equals(resource.getVersionLabel()))
			version = null;

		InputStream is = null;

		try {
			if (version == null || version.equals("")) {
				String res = storer.getResourceName(document, null, null);
				is = storer.getStream(document.getId(), res);
			} else {
				String res = storer.getResourceName(document, resource.getVersionLabel(), null);
				is = storer.getStream(document.getId(), res);
			}
		} catch (IOException e) {
			throw new DavResourceIOException(e.getMessage());
		}
		return is;
	}

	@Override
	public void checkout(Resource resource, WebdavSession session) throws DavException {
		String sid = (String) session.getObject("sid");

		User user = null;
		try {
			user = userDAO.findById(resource.getRequestedPerson());
		} catch (PersistenceException e1) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, e1);
		}
		userDAO.initialize(user);

		try {
			checkPublished(user, Long.parseLong(resource.getID()));
		} catch (PersistenceException e1) {
			throw new DavException(DavServletResponse.SC_FORBIDDEN, e1);
		}

		// verify the write permission on the parent folder
		Resource parent = getParentResource(resource);
		if (!parent.isWriteEnabled())
			throw new DavException(DavServletResponse.SC_FORBIDDEN, "No rights to checkout resource.");

		try {
			// Create the document history event
			DocumentHistory transaction = new DocumentHistory();
			transaction.setSessionId(sid);
			transaction.setEvent(DocumentEvent.CHECKEDOUT.toString());
			transaction.setComment("");
			transaction.setUser(user);

			documentManager.checkout(Long.parseLong(resource.getID()), transaction);
		} catch (NumberFormatException e) {
			throw new RuntimeException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public boolean isCheckedOut(Resource resource) throws NumberFormatException, PersistenceException {
		Document document = documentDAO.findById(Long.parseLong(resource.getID()));
		return document.getStatus() == Document.DOC_CHECKED_OUT;
	}

	protected void checkPublished(User user, Document doc) throws DavException {
		if (!user.isMemberOf("admin") && !user.isMemberOf("publisher") && !doc.isPublishing())
			throw new DavException(1, "Document not published");
	}

	protected void checkPublished(User user, long docId) throws DavException, PersistenceException {
		Document document = documentDAO.findById(docId);
		checkPublished(user, document);
	}

	public List<Resource> getHistory(Resource resource) {
		List<Resource> resourceHistory = new LinkedList<Resource>();

		Document document = null;
		try {
			document = documentDAO.findById(Long.parseLong(resource.getID()));
		} catch (PersistenceException e) {
			return resourceHistory;
		}
		documentDAO.initialize(document);

		Collection<Version> tmp = versionDAO.findByDocId(document.getId());
		Version[] sortIt = (Version[]) tmp.toArray(new Version[0]);

		// clear collection and add sorted elements
		Arrays.sort(sortIt);

		for (Version version : sortIt) {
			Resource res = marshallDocument(document, resource.getSession());
			res.setVersionLabel(version.getVersion());
			res.setVersionDate(version.getDate());
			res.setAuthor(version.getUsername());
			res.setCheckedOut(true);
			resourceHistory.add(res);
		}

		return resourceHistory;
	}

	public void uncheckout(Resource resource, WebdavSession session) {
		String sid = (String) session.getObject("sid");
		try {
			User user = userDAO.findById(resource.getRequestedPerson());
			// Create the document history event
			DocumentHistory transaction = new DocumentHistory();
			transaction.setSessionId(sid);
			transaction.setUser(user);

			documentManager.unlock(Long.parseLong(resource.getID()), transaction);

			resource.setCheckedOut(false);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}

	public void setVersionDAO(VersionDAO versionDAO) {
		this.versionDAO = versionDAO;
	}

	public void setFolderDAO(FolderDAO folderDAO) {
		this.folderDAO = folderDAO;
	}

	public void setStorer(Storer storer) {
		this.storer = storer;
	}
}
