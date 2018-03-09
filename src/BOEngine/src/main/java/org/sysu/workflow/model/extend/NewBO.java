/*
 * Project Ren @ 2018
 * Rinkako, Ariana, Gordan. SYSU SDCS.
 */
package org.sysu.workflow.model.extend;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.sysu.renCommon.enums.LogLevelType;
import org.sysu.workflow.env.MultiStateMachineDispatcher;
import org.sysu.workflow.instanceTree.InstanceManager;
import org.sysu.workflow.instanceTree.RTreeNode;
import org.sysu.workflow.instanceTree.RInstanceTree;
import org.sysu.workflow.env.SimpleErrorReporter;
import org.sysu.workflow.io.SCXMLReader;
import org.sysu.workflow.*;
import org.sysu.workflow.model.*;
import org.sysu.workflow.restful.entity.RenBoEntity;
import org.sysu.workflow.restful.service.LaunchProcessService;
import org.sysu.workflow.utility.HibernateUtil;
import org.sysu.workflow.utility.LogUtil;
import org.sysu.workflow.utility.SerializationUtil;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: Rinkako
 * Date  : 2017/3/7
 * Usage : Label context of NewBO.
 */
public class NewBO extends NamelistHolder implements PathResolverHolder {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The file source of this state machine
     */
    private String src;

    /**
     * How many sub state machine instance ought to be create
     */
    private int instances = 1;

    /**
     * Notifiable id.
     */
    private String idExpr;

    /**
     * Path Resolver for the file src
     * {@link PathResolver} for resolving the "src" or "srcexpr" result.
     */
    private PathResolver pathResolver;

    /**
     * Get the value of src
     *
     * @return value of src property
     */
    public String getSrc() {
        return src;
    }

    /**
     * Set the value of src
     *
     * @param src the src value to set
     */
    public void setSrc(String src) {
        this.src = src;
    }

    /**
     * Get the value of instance
     *
     * @return value of instance property
     */
    public int getInstances() {
        return instances;
    }

    /**
     * Set the value of instance
     *
     * @param instances the instance value to set, represent how many sub instance ought to be created
     */
    public void setInstances(int instances) {
        this.instances = instances;
    }

    /**
     * Get the expr of notifiable id.
     *
     * @return id string
     */
    public String getIdExpr() {
        return idExpr;
    }

    /**
     * Set the expr of notifiable id.
     *
     * @param idExpr id string
     */
    public void setIdExpr(String idExpr) {
        this.idExpr = idExpr;
    }

    /**
     * Get the value of pathResolver
     *
     * @return value of pathResolver property
     */
    public PathResolver getPathResolver() {
        return pathResolver;
    }

    /**
     * Set the value of pathResolver
     *
     * @param pathResolver The path resolver to use.
     */
    public void setPathResolver(PathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    /**
     * Execution of encountering this label
     *
     * @param exctx The ActionExecutionContext for this execution instance
     */
    @Override
    public void execute(ActionExecutionContext exctx) {
        try {
            EnterableState parentState = getParentEnterableState();
            Context ctx = exctx.getContext(parentState);
            ctx.setLocal(getNamespacesKey(), getNamespaces());
            Map<String, Object> payloadDataMap = new LinkedHashMap<String, Object>();
            addParamsToPayload(exctx, payloadDataMap);

            SCXML scxml = null;

            SCXMLExecutionContext currentExecutionContext = (SCXMLExecutionContext) exctx.getInternalIOProcessor();
            String boName = getSrc().split("\\.")[0];

            //read BO from database
            if (!GlobalContext.IsLocalDebug) {
                //Ariana:get the serialized BO from the database and deserialize it into SCXML object
                Session session = HibernateUtil.GetLocalSession();
                Transaction transaction = session.beginTransaction();
                boolean cmtFlag = false;
                try {
                    byte[] serializedBO = null;
                    List boList = session.createQuery(String.format("FROM RenBoEntity WHERE pid = '%s'", currentExecutionContext.Pid)).list();
                    for (Object bo : boList) {
                        RenBoEntity boEntity = (RenBoEntity) bo;
                        if (boEntity.getBoName().equals(boName)) {
                            serializedBO = boEntity.getSerialized();
                            break;
                        }
                    }
                    transaction.commit();
                    cmtFlag = true;
                    scxml = SerializationUtil.DeserializationSCXMLByByteArray(serializedBO);
                } catch (Exception e) {
                    if (!cmtFlag) {
                        transaction.rollback();
                    }
                    LogUtil.Log("When read bo by rtid, exception occurred, " + e.toString() + ", service rollback",
                            LaunchProcessService.class.getName(), LogLevelType.ERROR, currentExecutionContext.Rtid);
                } finally {
                    HibernateUtil.CloseLocalSession();
                }
            }
            else {
                //read local BO
                // get resource file url
                //final URL url = this.getClass().getClassLoader().getResource(getSrc());

                // RINKAKO: get file by passing URL
                URL url = new URL("file", "", getSrc());
                try {
                    InputStream in = url.openStream();
                } catch (Exception e1) {
                    System.out.println("load file directly failed, try get resource");
                    url = this.getClass().getClassLoader().getResource(getSrc());
                }
                try {
                    scxml = SCXMLReader.read(url);
                } catch (Exception e) {
                    System.out.println("couldn't find :" + getSrc());
                    e.printStackTrace();
                }
            }

            // launch sub state machine of the number of instances
            RInstanceTree iTree = InstanceManager.GetInstanceTree(currentExecutionContext.Rtid);
            RTreeNode curNode = iTree.GetNodeById(currentExecutionContext.NodeId);
            Evaluator evaluator = EvaluatorFactory.getEvaluator(scxml);
            Context tmpCtx = evaluator.newContext(ctx);
            for (int i = 0; i < getInstances(); i++) {
                SCXMLExecutor executor = new SCXMLExecutor(evaluator, new MultiStateMachineDispatcher(), new SimpleErrorReporter(), null, currentExecutionContext.RootNodeId);
                executor.setRtid(currentExecutionContext.Rtid);
                executor.setPid(currentExecutionContext.Pid);
                executor.setStateMachine(scxml);
                Context rootContext = evaluator.newContext(null);
                for (Map.Entry<String, Object> entry : payloadDataMap.entrySet()) {
                    rootContext.set(entry.getKey(), entry.getValue());
                }
                // handle initialization parameter
                Datamodel dm = scxml.getDatamodel();
                ArrayList<Data> dataToAdd = new ArrayList<>();
                for (Param argument : this.getParams()) {
                    boolean existFlag = false;
                    for (Data parameter : dm.getData()) {
                        if (parameter.getId().equals(argument.getName())) {
                           parameter.setExpr(argument.getExpr());
                            existFlag = true;
                            break;
                        }
                    }
                    if (!existFlag) {
                        Data d = new Data();
                        d.setId(argument.getName());
                        d.setExpr(argument.getExpr());
                        dataToAdd.add(d);
                    }
                }
                for (Data d : dataToAdd) {
                    dm.addData(d);
                }
                // handle notifiable id of new state machine
                if (this.idExpr != null) {
                    tmpCtx.setLocal("_instanceIndex", i);
                    executor.setNotifiableId(evaluator.eval(tmpCtx, this.idExpr).toString());
                }
                // start sub state machine
                executor.setRootContext(rootContext);
                executor.setExecutorIndex(iTree.Root.getExect().getSCXMLExecutor().getExecutorIndex());
                executor.go();
                // maintain the relation of this sub state machine on the instance tree
                RTreeNode subNode = new RTreeNode(boName, executor.NodeId, executor.getExctx(), curNode);
                curNode.AddChild(subNode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}