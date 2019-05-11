package com.migs.zk.inspectorzk.services;

import com.migs.zk.inspectorzk.domain.*;
import com.migs.zk.inspectorzk.util.ZKUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author liuzh
 */
public class CuratorZKService implements ZKService {
  private static Logger log = LogManager.getLogger(CuratorZKService.class);

  private CuratorFramework curatorFramework;

  @Override
  public boolean connectToZK(ZKConnInfo connInfo) {
    curatorFramework = CuratorFrameworkFactory
        .newClient(connInfo.getFullHostString(), 60 * 1000,
            15 * 1000, new RetryOneTime(1000));
    curatorFramework.start();
    return curatorFramework.isStarted();
  }

  @Override
  public ZKDataResult getZNodeData(String path) throws ZKDataException {
    log.debug(String.format("Getting znode data for: %s\n", path));

    ZKDataResult dataResult = null;
    try {
      Stat nodeStat = new Stat();
      byte[] data = curatorFramework.getData().storingStatIn(nodeStat).forPath(path);
      if (data != null) {
        dataResult = new ZKDataResult(new String(data, StandardCharsets.UTF_8).trim(), nodeStat);
      }
    } catch (Exception e) {
      String msg = "";
      if (ZKUtils.isNoAuth(e.getMessage()))
        msg = String.format("Error: trying to get data for: %s due to: %s\nMight need to authenticate first (Menu -> Connection -> Auth)", path, e.getMessage());
      else
        msg = String.format("Error: trying to get data for: %s due to: %s", path, e.getMessage());

      throw new ZKDataException(msg);
    }

    return dataResult;
  }

  @Override
  public boolean setZNodeData(String path, String data) throws ZKDataException {
    try {
      Stat currNodeStat = curatorFramework.checkExists().forPath(path);
      if (currNodeStat != null) {
        int currVer = currNodeStat.getVersion();
        Stat newStat = curatorFramework.setData()
            .withVersion(currVer).forPath(path, data.getBytes());
        log.debug("updated znode: " + path);
        return newStat.getVersion() >= currVer;
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new ZKDataException(
          String.format("Error setting data for: %s", path),
          e
      );
    }
    return false;
  }

  @Override
  public List<String> getZnodeChildren(String path) throws ZKDataException {
    try {
      List<String> sortedChildren = curatorFramework.getChildren().forPath(path);
      Collections.sort(sortedChildren);
      return sortedChildren;
    } catch (Exception e) {
      String msg = "";
      if (ZKUtils.isNoAuth(e.getMessage()))
        msg = String.format("Error: trying to get children for: %s due to: %s\nMight need to authenticate first (Menu -> Connection -> Auth)", path, e.getMessage());
      else
        msg = String.format("Error: trying to get children for: %s due to: %s", path, e.getMessage());

      throw new ZKDataException(msg);
    }
  }

  @Override
  public boolean authDigestUser(String user, String pw, ZKSchemeDefs schemeDef) throws ZKAuthException {
    try {
      curatorFramework.getZookeeperClient().getZooKeeper().addAuthInfo(schemeDef.getSchemeValue(), String.format("%s:%s", user, pw).getBytes());
    } catch (Exception e) {
      throw new ZKAuthException(String.format("Error authDigestUser for: %s:%s", user, pw));
    }
    return true;
  }

  @Override
  public void disconnectFromZK() {
    if (curatorFramework != null) {
      curatorFramework.close();
    }
  }

  @Override
  public Map<String, ACL> getAclMap(String path) throws ZKDataException {
    Map<String, ACL> aclMap = new TreeMap<>();
    try {
      List<ACL> aclList = curatorFramework.getACL().forPath(path);
      if(aclList != null)
        aclList.forEach( acl -> aclMap.put(ZKUtils.parseAclId(acl.getId().getId()), acl) );
    } catch (Exception e) {
      e.printStackTrace();
      throw new ZKDataException(String.format("Error getting ACL for: %s", path));
    }

    return aclMap;
  }

  @Override
  public boolean setAclForZnode(String path, ZKSchemeDefs zkSchemeDef, String usrId, String rolesStr) throws ZKDataException {
    try {
      int translatedPerms = ZKPerms.translatePerms(rolesStr.split(","));
      log.debug(String.format("\nrolesStr: %s | translatedPerms = %d\n", rolesStr, translatedPerms));

      Id id = new Id(zkSchemeDef.getSchemeValue(), usrId);
      ACL acl = new ACL(translatedPerms, id);

      Map<String, ACL> currentAcls = getAclMap(path);
      currentAcls.put(id.getId(), acl);
      if(curatorFramework != null){
        curatorFramework.setACL().withVersion(-1).withACL(new ArrayList<>(currentAcls.values())).forPath(path);
        log.debug("Perms Set");
        return true;
      }
    } catch (Exception e) {
      throw new ZKDataException(
          String.format("Error trying to set ACL for: %s | caused by: %s", path, e.getMessage()),
          e
      );
    }

    return false;
  }

  @Override
  public boolean setAclForZnode(String path, ZKSchemeDefs zkSchemeDef, String usrId, String pw, List<ZKPerms> perms) throws ZKDataException {
    try {
      int translatedPerms = ZKPerms.translatePerms(perms);
      log.debug(String.format("\nrolesStr: %s | translatedPerms = %d\n", perms.size(), translatedPerms));

      if(zkSchemeDef == ZKSchemeDefs.DIGEST)
        usrId +=":"+pw;

      Id id = new Id(zkSchemeDef.getSchemeValue(), usrId);
      ACL acl = new ACL(translatedPerms, id);

      Map<String, ACL> currentAcls = getAclMap(path);
      currentAcls.put(id.getId(), acl);

      if(curatorFramework != null){
        curatorFramework.setACL().withVersion(-1)
            .withACL(new ArrayList<>(currentAcls.values())).forPath(path);
        log.debug("Perms Set");
        return true;
      }
    } catch (Exception e) {
      throw new ZKDataException(e.getMessage(),e);
    }

    return false;
  }

  @Override
  public boolean removeAclForZnode(String path, String usrId) throws ZKDataException {
    boolean removed = false;

    Map<String, ACL> currentAcls = getAclMap(path);

    if(currentAcls != null && !currentAcls.isEmpty()){

      Collection<ACL> aclList = currentAcls.values();
      Iterator<ACL> aclIterator = aclList.iterator();

      while(aclIterator.hasNext()){
        ZKAclData zkAclData = new ZKAclData(aclIterator.next());
        if(zkAclData.getId().equals(usrId)){
          aclIterator.remove();
        }
      }

      try {
        if(curatorFramework != null){
          curatorFramework.setACL().withVersion(-1)
              .withACL(new ArrayList<>(currentAcls.values())).forPath(path);
          removed = true;
        }
      } catch (Exception e) {
        log.error("Error removing ACL: "+ e.getMessage());
        throw new ZKDataException("Error removing ACL", e);
      }
    }

    return removed;
  }

  @Override
  public boolean createZnode(String path, String data, CreateMode createMode) throws ZKDataException {
    try {
      if(curatorFramework != null){
        String res = curatorFramework.create()
            .withMode(createMode)
            .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
            .forPath(path, data.getBytes());
        log.debug("create res: "+ res);
        return true;
      }
    } catch (Exception e) {
      if(e.getMessage().contains("NodeExists"))
        log.error("ERROR: Node already exists\n");
      else
        log.error(String.format("ERROR: %s", e.getMessage()));
    }

    return false;
  }

  @Override
  public boolean deleteZnode(String path) throws ZKDataException {
    try {
      if(curatorFramework != null){
        curatorFramework.delete().deletingChildrenIfNeeded().withVersion(-1).forPath(path);
        log.debug(String.format("Znode deleted: %s\n", path));
      }
      return true;
    } catch (Exception e) {
      throw new ZKDataException(String.format("Error deleting node: %s | cause: %s", path, e.getMessage()));
    }
  }
}
