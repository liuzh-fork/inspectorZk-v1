package com.migs.zk.inspectorzk.services;

import com.migs.zk.inspectorzk.domain.ZKConnInfo;
import com.migs.zk.inspectorzk.domain.ZKDataResult;
import com.migs.zk.inspectorzk.domain.ZKPerms;
import com.migs.zk.inspectorzk.domain.ZKSchemeDefs;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.ACL;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public interface ZKService {

    static ZKService getInstance(){
        return Singleton.getInstance();
    }

    static void init(String type){
        Singleton.init(type);
    }

    class Singleton {
        static volatile ZKService instance;

        static ZKService getInstance(){
            if(instance == null){
                synchronized (Singleton.class) {
                    if(instance == null){
                        ServiceLoader<ZKService> zkServices = ServiceLoader.load(ZKService.class);
                        Iterator<ZKService> iterator = zkServices.iterator();
                        if (iterator.hasNext()) {
                            instance = iterator.next();
                        }
                        if(instance == null){
                            instance = new ZKServiceImpl();
                        }
                    }
                }
            }
            return instance;
        }

        static void init(String type) {
            if ("default".equals(type)) {
                instance = new ZKServiceImpl();
            } else {
                instance = new CuratorZKService();
            }
        }
    }

    boolean connectToZK(ZKConnInfo connInfo);

    ZKDataResult getZNodeData(String path) throws ZKDataException;

    boolean setZNodeData(String path, String data) throws ZKDataException;

    List<String> getZnodeChildren(String path) throws ZKDataException;

    boolean authDigestUser(String user, String pw, ZKSchemeDefs schemeDef) throws ZKAuthException;

    void disconnectFromZK();

    Map<String, ACL> getAclMap(String path) throws ZKDataException;

    boolean setAclForZnode(String path, ZKSchemeDefs zkSchemeDef, String usrId, String rolesStr) throws ZKDataException;

    boolean setAclForZnode(String path, ZKSchemeDefs zkSchemeDef, String usrId, String pw, List<ZKPerms> perms) throws ZKDataException;

    boolean removeAclForZnode(String path, String usrId) throws ZKDataException;

    boolean createZnode(String path, String data, CreateMode createMode) throws ZKDataException;

    boolean deleteZnode(String path) throws ZKDataException;
}
