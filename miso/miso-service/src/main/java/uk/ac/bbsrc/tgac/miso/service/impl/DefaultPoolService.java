package uk.ac.bbsrc.tgac.miso.service.impl;

import static uk.ac.bbsrc.tgac.miso.core.util.LimsUtils.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eaglegenomics.simlims.core.Note;
import com.eaglegenomics.simlims.core.User;
import com.eaglegenomics.simlims.core.manager.SecurityManager;

import uk.ac.bbsrc.tgac.miso.core.data.Pool;
import uk.ac.bbsrc.tgac.miso.core.data.Sample;
import uk.ac.bbsrc.tgac.miso.core.data.impl.PoolImpl;
import uk.ac.bbsrc.tgac.miso.core.data.impl.changelog.PoolChangeLog;
import uk.ac.bbsrc.tgac.miso.core.data.impl.view.PoolableElementView;
import uk.ac.bbsrc.tgac.miso.core.data.type.PlatformType;
import uk.ac.bbsrc.tgac.miso.core.exception.AuthorizationIOException;
import uk.ac.bbsrc.tgac.miso.core.exception.MisoNamingException;
import uk.ac.bbsrc.tgac.miso.core.service.naming.NamingScheme;
import uk.ac.bbsrc.tgac.miso.core.store.PoolStore;
import uk.ac.bbsrc.tgac.miso.core.util.LimsUtils;
import uk.ac.bbsrc.tgac.miso.core.util.PaginatedDataSource;
import uk.ac.bbsrc.tgac.miso.service.ChangeLogService;
import uk.ac.bbsrc.tgac.miso.service.PoolService;
import uk.ac.bbsrc.tgac.miso.service.PoolableElementViewService;
import uk.ac.bbsrc.tgac.miso.service.security.AuthorizationManager;
import uk.ac.bbsrc.tgac.miso.service.security.AuthorizedPaginatedDataSource;

@Transactional(rollbackFor = Exception.class)
@Service
public class DefaultPoolService implements PoolService, AuthorizedPaginatedDataSource<Pool> {

  @Value("${miso.autoGenerateIdentificationBarcodes}")
  private Boolean autoGenerateIdBarcodes;

  @Autowired
  private AuthorizationManager authorizationManager;
  @Autowired
  private PoolStore poolStore;
  @Autowired
  private NamingScheme namingScheme;
  @Autowired
  private ChangeLogService changeLogService;
  @Autowired
  private SecurityManager securityManager;
  @Autowired
  private PoolableElementViewService poolableElementViewService;

  public void setAutoGenerateIdBarcodes(boolean autoGenerateIdBarcodes) {
    this.autoGenerateIdBarcodes = autoGenerateIdBarcodes;
  }

  public void setAuthorizationManager(AuthorizationManager authorizationManager) {
    this.authorizationManager = authorizationManager;
  }

  public void setPoolStore(PoolStore poolStore) {
    this.poolStore = poolStore;
  }

  public void setNamingScheme(NamingScheme namingScheme) {
    this.namingScheme = namingScheme;
  }

  public void setChangeLogService(ChangeLogService changeLogService) {
    this.changeLogService = changeLogService;
  }

  public void setPoolableElementViewService(PoolableElementViewService poolableElementViewService) {
    this.poolableElementViewService = poolableElementViewService;
  }

  @Override
  public AuthorizationManager getAuthorizationManager() {
    return authorizationManager;
  }

  @Override
  public PaginatedDataSource<Pool> getBackingPaginationSource() {
    return poolStore;
  }

  @Override
  public Collection<Pool> listBySearch(String query) throws IOException {
    List<Pool> pools = poolStore.listAllByCriteria(null, query, null, false);
    return authorizationManager.filterUnreadable(pools);
  }

  @Override
  public Collection<Pool> listWithLimit(int limit) throws IOException {
    List<Pool> pools = poolStore.listAllByCriteria(null, null, limit, false);
    return authorizationManager.filterUnreadable(pools);
  }

  @Override
  public Collection<Pool> list() throws IOException {
    Collection<Pool> pools = poolStore.listAll();
    return authorizationManager.filterUnreadable(pools);
  }

  @Override
  public Collection<Pool> listByPlatform(PlatformType platformType) throws IOException {
    Collection<Pool> pools = poolStore.listAllByCriteria(platformType, null, null, false);
    return authorizationManager.filterUnreadable(pools);
  }

  @Override
  public Collection<Pool> listByPlatformAndSearch(PlatformType platformType, String query) throws IOException {
    List<Pool> pools = poolStore.listAllByCriteria(platformType, query, null, false);
    return authorizationManager.filterUnreadable(pools);
  }

  @Override
  public Collection<Pool> listReadyPoolsByPlatform(PlatformType platformType) throws IOException {
    List<Pool> pools = poolStore.listAllByCriteria(platformType, null, null, true);
    return authorizationManager.filterUnreadable(pools);
  }

  @Override
  public Collection<Pool> listReadyPoolsByPlatformAndSearch(PlatformType platformType, String query) throws IOException {
    List<Pool> pools = poolStore.listAllByCriteria(platformType, query, null, true);
    return authorizationManager.filterUnreadable(pools);
  }

  @Override
  public Collection<Pool> listByProjectId(long projectId) throws IOException {
    Collection<Pool> pools = poolStore.listByProjectId(projectId);
    return authorizationManager.filterUnreadable(pools);
  }

  @Override
  public Collection<Pool> listByLibraryId(long libraryId) throws IOException {
    Collection<Pool> pools = poolStore.listByLibraryId(libraryId);
    return authorizationManager.filterUnreadable(pools);
  }

  @Override
  public void delete(Pool pool) throws IOException {
    authorizationManager.throwIfNonAdmin();
    if (!poolStore.remove(pool)) {
      throw new IOException("Unable to delete Pool.");
    }
  }

  @Override
  public void deleteNote(Pool pool, Long noteId) throws IOException {
    if (noteId == null || noteId.equals(Note.UNSAVED_ID)) {
      throw new IllegalArgumentException("Cannot delete an unsaved Note");
    }
    Pool managed = poolStore.get(pool.getId());
    Note deleteNote = null;
    for (Note note : managed.getNotes()) {
      if (note.getNoteId().equals(noteId)) {
        deleteNote = note;
        break;
      }
    }
    if (deleteNote == null) {
      throw new IOException("Note " + noteId + " not found for Pool " + pool.getId());
    }
    authorizationManager.throwIfNonAdminOrMatchingOwner(deleteNote.getOwner());
    managed.getNotes().remove(deleteNote);
    poolStore.save(managed);
  }

  @Override
  public long save(Pool pool) throws IOException {
    if (pool.isDiscarded()) {
      pool.setVolume(0.0);
    }

    if (pool.getId() == PoolImpl.UNSAVED_ID) {
      pool.setName(generateTemporaryName());
      loadPooledElements(pool.getPoolableElementViews(), pool);
      setChangeDetails(pool);
      poolStore.save(pool);

      if (autoGenerateIdBarcodes) {
        LimsUtils.generateAndSetIdBarcode(pool);
      }
      try {
        pool.setName(namingScheme.generateNameFor(pool));
        validateNameOrThrow(pool, namingScheme);
      } catch (MisoNamingException e) {
        throw new IOException("Invalid name for pool", e);
      }
    } else {
      Pool original = poolStore.get(pool.getId());
      authorizationManager.throwIfNotWritable(original);
      original.setAlias(pool.getAlias());
      original.setConcentration(pool.getConcentration());
      original.setDescription(pool.getDescription());
      original.setIdentificationBarcode(LimsUtils.nullifyStringIfBlank(pool.getIdentificationBarcode()));
      original.setPlatformType(pool.getPlatformType());
      original.setQcPassed(pool.getQcPassed());
      original.setReadyToRun(pool.getReadyToRun());
      original.setVolume(pool.getVolume());
      original.setDiscarded(pool.isDiscarded());
      original.setCreationDate(pool.getCreationDate());

      Set<String> originalItems = extractDilutionNames(original.getPoolableElementViews());
      loadPooledElements(pool, original);
      Set<String> updatedItems = extractDilutionNames(original.getPoolableElementViews());

      Set<String> added = new TreeSet<>(updatedItems);
      added.removeAll(originalItems);
      Set<String> removed = new TreeSet<>(originalItems);
      removed.removeAll(updatedItems);

      if (!added.isEmpty() || !removed.isEmpty()) {
        StringBuilder message = new StringBuilder();
        message.append("Items");
        LimsUtils.appendSet(message, added, "added");
        LimsUtils.appendSet(message, removed, "removed");

        PoolChangeLog changeLog = new PoolChangeLog();
        changeLog.setPool(pool);
        changeLog.setColumnsChanged("contents");
        changeLog.setSummary(message.toString());
        changeLog.setTime(new Date());
        changeLog.setUser(pool.getLastModifier());
        changeLogService.create(changeLog);
      }
      pool = original;
      setChangeDetails(pool);
    }
    long id = poolStore.save(pool);
    return id;
  }

  /**
   * Updates all user data and timestamps associated with the change. Existing timestamps will be preserved
   * if the Pool is unsaved, and they are already set
   * 
   * @param pool the Pool to update
   * @param preserveTimestamps if true, the creationTime and lastModified date are not updated
   * @throws IOException
   */
  private void setChangeDetails(Pool pool) throws IOException {
    User user = authorizationManager.getCurrentUser();
    Date now = new Date();
    pool.setLastModifier(user);

    if (pool.getId() == Sample.UNSAVED_ID) {
      pool.setCreator(user);
      if (pool.getCreationTime() == null) {
        pool.setCreationTime(now);
      }
      if (pool.getLastModified() == null) {
        pool.setLastModified(now);
      }
    } else {
      pool.setLastModified(now);
    }
  }

  private void loadPooledElements(Collection<PoolableElementView> source, Pool target) throws IOException {
    Set<PoolableElementView> pooledElements = new HashSet<>();
    for (PoolableElementView dilution : source) {
      PoolableElementView v = poolableElementViewService.get(dilution.getDilutionId());
      if (v == null) {
        throw new IllegalStateException("Pool contains an unsaved dilution");
      }
      pooledElements.add(v);
    }
    target.setPoolableElementViews(pooledElements);
  }

  private void loadPooledElements(Pool source, Pool target) throws IOException {
    loadPooledElements(source.getPoolableElementViews(), target);
  }

  private Set<String> extractDilutionNames(Set<PoolableElementView> dilutions) {
    Set<String> original = new HashSet<>();
    for (PoolableElementView dilution : dilutions) {
      original.add(dilution.getDilutionName());
    }
    return original;
  }

  @Override
  public void saveNote(Pool pool, Note note) throws IOException {
    Pool managed = poolStore.get(pool.getId());
    authorizationManager.throwIfNotWritable(managed);
    note.setCreationDate(new Date());
    note.setOwner(authorizationManager.getCurrentUser());
    managed.addNote(note);
    poolStore.save(managed);
  }

  @Override
  public Pool get(long poolId) throws IOException {
    Pool pool = poolStore.get(poolId);
    authorizationManager.throwIfNotReadable(pool);
    return pool;
  }

  @Override
  public Pool getByBarcode(String barcode) throws IOException {
    Pool pool = poolStore.getByBarcode(barcode);
    authorizationManager.throwIfNotReadable(pool);
    return pool;
  }

  @Override
  public Map<String, Integer> getPoolColumnSizes() throws IOException {
    return poolStore.getPoolColumnSizes();
  }

  @Override
  public void addPoolWatcher(Pool pool, User watcher) throws IOException {
    User managedWatcher = securityManager.getUserById(watcher.getUserId());
    Pool managedPool = poolStore.get(pool.getId());
    authorizationManager.throwIfNotReadable(managedPool);
    if (!managedPool.userCanRead(managedWatcher)) {
      throw new AuthorizationIOException("User " + watcher.getLoginName() + " cannot see this pool.");
    }
    poolStore.addWatcher(pool, watcher);
  }

  @Override
  public void removePoolWatcher(Pool pool, User watcher) throws IOException {
    User managedWatcher = securityManager.getUserById(watcher.getUserId());
    authorizationManager.throwIfNonAdminOrMatchingOwner(managedWatcher);
    poolStore.removeWatcher(pool, managedWatcher);
  }

  @Override
  public Collection<Pool> listByIdList(List<Long> poolIds) throws IOException {
    return authorizationManager.filterUnreadable(poolStore.listPoolsById(poolIds));
  }

}