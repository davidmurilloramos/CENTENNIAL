/*
* Copyright (c) 2017 highstreet technologies GmbH and others. All rights reserved.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v1.0 which accompanies this distribution,
* and is available at http://www.eclipse.org/legal/epl-v10.html
*/

package org.opendaylight.mwtn.base.netconf;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPoint;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.mwtn.base.internalTypes.InternalDateAndTime;
import org.opendaylight.mwtn.base.internalTypes.InternalSeverity;
import org.opendaylight.mwtn.devicemanager.impl.database.service.HtDatabaseEventsService;
import org.opendaylight.mwtn.devicemanager.impl.listener.MicrowaveEventListener;
import org.opendaylight.mwtn.devicemanager.impl.xml.ProblemNotificationXml;
import org.opendaylight.mwtn.devicemanager.impl.xml.WebSocketServiceClient;
import org.opendaylight.mwtn.ecompConnector.impl.EventProviderClient;
import org.opendaylight.mwtn.performancemanager.impl.database.types.EsHistoricalPerformance15Minutes;
import org.opendaylight.mwtn.performancemanager.impl.database.types.EsHistoricalPerformance24Hours;
import org.opendaylight.yang.gen.v1.uri.onf.coremodel.corefoundationmodule.superclassesandcommonpackages.rev160710.UniversalId;
import org.opendaylight.yang.gen.v1.uri.onf.coremodel.corenetworkmodule.objectclasses.rev160811.NetworkElement;
import org.opendaylight.yang.gen.v1.uri.onf.coremodel.corenetworkmodule.objectclasses.rev160811.logicalterminationpoint.LpList;
import org.opendaylight.yang.gen.v1.uri.onf.coremodel.corenetworkmodule.objectclasses.rev160811.networkelement.LtpRefList;
import org.opendaylight.yang.gen.v1.uri.onf.g_874_1_model.object_classes.rev160710.OTNHistoryData;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.networkelement.currentproblemlist.rev161120.GenericCurrentProblemType;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.networkelement.currentproblemlist.rev161120.NetworkElementCurrentProblems;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.networkelement.currentproblemlist.rev161120.networkelementcurrentproblems.CurrentProblemList;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.airinterface.rev160901.MWAirInterfacePac;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.airinterface.rev160901.MWAirInterfacePacKey;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.airinterface.rev160901.mw_airinterface_pac.AirInterfaceConfiguration;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.airinterface.rev160901.mw_airinterface_pac.AirInterfaceCurrentProblems;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.airinterface.rev160901.mw_airinterface_pac.AirInterfaceHistoricalPerformances;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.ethernetcontainer.rev160902.MWEthernetContainerPac;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.ethernetcontainer.rev160902.MWEthernetContainerPacKey;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.ethernetcontainer.rev160902.mw_ethernetcontainer_pac.EthernetContainerCurrentProblems;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.ethernetcontainer.rev160902.mw_ethernetcontainer_pac.EthernetContainerHistoricalPerformances;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.typedefinitions.rev160902.AirInterfaceCurrentProblemType;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.typedefinitions.rev160902.ContainerCurrentProblemType;
import org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.typedefinitions.rev160902.ContainerHistoricalPerformanceType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Get information over NETCONF device according to ONF Coremodel. Read networkelement and conditional packages.
 *
 * Get conditional packages from Networkelement
 * Possible interfaces are:
 *   MWPS, LTP(MWPS-TTP), MWAirInterfacePac, MicrowaveModel-ObjectClasses-AirInterface
 *   ETH-CTP,LTP(Client), MW_EthernetContainer_Pac
 *   MWS, LTP(MWS-CTP-xD), MWAirInterfaceDiversityPac, MicrowaveModel-ObjectClasses-AirInterfaceDiversity
 *   MWS, LTP(MWS-TTP), ,MicrowaveModel-ObjectClasses-HybridMwStructure
 *   MWS, LTP(MWS-TTP), ,MicrowaveModel-ObjectClasses-PureEthernetStructure
 *
 * @author herbert
 *
 */
public class ONFCoreNetworkElement10 extends ONFCoreNetworkElementBase {

    private static final Logger LOG = LoggerFactory.getLogger(ONFCoreNetworkElementRepresentation.class);

    private static final InstanceIdentifier<NetworkElement> NETWORKELEMENT_IID = InstanceIdentifier
            .builder(NetworkElement.class)
            .build();

    /*-----------------------------------------------------------------------------
     * Class members
     */

    // Non specific part. Used by all functions.
    /** interfaceList is used by performance monitoring (pm) task and should be synchronized */
    private final @Nonnull List<LpList> interfaceList = Collections.synchronizedList(new CopyOnWriteArrayList<>());
    private final @Nonnull MicrowaveEventListener microwaveEventListener;
    private @Nullable NetworkElement optionalNe = null;

    // Performance monitoring specific part
    /** Lock for the PM access specific elements that could be null */
    private final @Nonnull Object pmLock = new Object();
    private @Nullable Iterator<LpList> interfaceListIterator = null;
    /** Actual pmLp used during iteration over interfaces */
    private @Nullable LpList pmLp = null;

    // Device monitoring specific part
    /** Lock for the DM access specific elements that could be null */
    private final @Nonnull Object dmLock = new Object();
    /** Interface used for device monitoring (dm). If not null it contains the interface that is used for monitoring calls */
    private @Nullable InstanceIdentifier<AirInterfaceCurrentProblems> dmAirIfCurrentProblemsIID = null;

    /*------------------------------------------------------------------------
     * Constructing
     */

    public ONFCoreNetworkElement10(String mountPointNodeName, Capabilities capabilities,
            DataBroker netconfNodeDataBroker, WebSocketServiceClient webSocketService,
            HtDatabaseEventsService databaseService, EventProviderClient ecompProvider ) {

        super(mountPointNodeName, netconfNodeDataBroker, capabilities );

        //Create MicrowaveService here
        this.microwaveEventListener = new MicrowaveEventListener(mountPointNodeName, webSocketService, databaseService, ecompProvider);

        LOG.info("Create NE instance {}", ONFCoreNetworkElement10.class.getSimpleName());
    }

    public static boolean checkType(Capabilities capabilities) {
        return capabilities.isSupportingNamespace(NetworkElement.QNAME);
    }

    public static ONFCoreNetworkElement10 build(String mountPointNodeName, Capabilities capabilities,
            DataBroker netconfNodeDataBroker, WebSocketServiceClient webSocketService,
            HtDatabaseEventsService databaseService, EventProviderClient ecompProvider ) {

        return checkType(capabilities) ? new ONFCoreNetworkElement10(mountPointNodeName, capabilities, netconfNodeDataBroker, webSocketService, databaseService, ecompProvider ) : null;

    }

    /*------------------------------------------------------------------------
     * Functions
     */




    /**
     * Have this as a separate function to avoid that reperesentatinon is not created because of exceptions are thrown.
     */
    @Override
    public void initialReadFromNetworkElement() {
        LOG.debug("Get info about {}", mountPointNodeName);

        int problems = microwaveEventListener.removeAllCurrentProblemsOfNode();
        LOG.debug("Removed all {} problems from database at registration",problems);


        //Step 2.1: access data broker within this mount point
        LOG.debug("DBRead start");
        //Step 2.2: read ne from data store
        optionalNe = readNetworkElement();
        LOG.debug("Name: ", optionalNe == null ? "no NE" : optionalNe.getNameList().toString());
        synchronized (pmLock) {
            interfaceList.addAll( getLtpList(optionalNe) );
        }

        //Step 2.3: read the existing faults and add to DB
        int problemsFound = 0;

        synchronized (pmLock) {
            for (LpList ltp : interfaceList) {
                if (ONFLayerProtocolName.MWAirInterface.is(ltp.getLayerProtocolName())) {
                    //if (ltp.getLayerProtocolName().getValue().contains("MWPS")) {
                    problemsFound += readTheFaultsOfMWAirInterfacePac(ltp.getUuid());
                    synchronized (dmLock) {
                        if (dmAirIfCurrentProblemsIID == null) {
                            dmAirIfCurrentProblemsIID = getMWAirInterfacePacIId(ltp.getUuid());
                        }
                    }
                }
                if (ONFLayerProtocolName.EthernetContainer10.is(ltp.getLayerProtocolName())) {
                    //if (ltp.getLayerProtocolName().getValue().contains("ETH")) {
                    problemsFound += readTheFaultsOfMWEthernetContainerPac(ltp.getUuid());
                }
            }
        }

        //Step 2.4: Read other problems from Mountpoint
        if (isNetworkElementCurrentProblemsSupporting) {
            problemsFound += readNetworkElementCurrentProblems();
        }

        LOG.info("Found info at {} for device {} number of problems: {}", mountPointNodeName, getUuId(), problemsFound);

    }

    private String getUuId() {
        String uuid = EMPTY;

        try {
            uuid = optionalNe != null ? optionalNe.getUuid() != null ? optionalNe.getUuid().getValue() : EMPTY : EMPTY;
        } catch (NullPointerException  e) {
            //Unfortunately throws null pointer if not definied
        }

        return uuid;
    }


    /*------------------------------------------------------------
     * private function to access database
     */

    /*karaf.log:2016-12-05 16:36:15,764 | INFO  | dispatcher-25390 | EventManagerImpl                 | 346 - org.opendaylight.mwtn.eventmanager-impl - 0.3.0.SNAPSHOT | DBRead
     * Currentproblems: NetworkElementCurrentProblems{getCurrentProblemList=[
     *   CurrentProblemList{
     *      getProblemName=powerLoss, getProblemSeverity=Critical, getSequenceNumber=1,
     *      getTimeStamp=DateAndTime [_value=20160822133005.0Z], augmentations={}},
     *      CurrentProblemList{
     *          getProblemName=powerLoss,
     *          getProblemSeverity=Minor,
     *          getSequenceNumber=2,
     *          getTimeStamp=DateAndTime [_value=20160822143005.0Z], augmentations={}}],  augmentations={}}
     *
     *          cpl(2),
     *             powerLoss[CurrentProblemList{getProblemName=powerLoss, getProblemSeverity=Critical, getSequenceNumber=1, getTimeStamp=DateAndTime [_value=20160822133005.0Z], augmentations={}}],
     *             powerLoss[CurrentProblemList{getProblemName=powerLoss, getProblemSeverity=Minor, getSequenceNumber=2, getTimeStamp=DateAndTime [_value=20160822143005.0Z], augmentations={}}]*/

    int readNetworkElementCurrentProblems() {

        int problemsFound = 0;
        LOG.info("DBRead Get {} NetworkElementCurrentProblems", mountPointNodeName);

        InstanceIdentifier<NetworkElementCurrentProblems> networkElementCurrentProblemsIID = InstanceIdentifier
                .builder(NetworkElementCurrentProblems.class)
                .build();

        //Step 2.3: read to the config data store
        NetworkElementCurrentProblems problems;
        try {
            problems = GenericTransactionUtils.readData(netconfNodeDataBroker, LogicalDatastoreType.OPERATIONAL, networkElementCurrentProblemsIID);
            if (problems == null) {
                LOG.debug("DBRead NetworkElementCurrentProblems no CurrentProblemsList");
            } else {
                List<CurrentProblemList> cpl = problems.getCurrentProblemList();
                LOG.debug("DBRead NetworkElementCurrentProblems input: {}", cpl);

                if (cpl != null) {
                    List<ProblemNotificationXml> resultList = new ArrayList<>();
                    for(GenericCurrentProblemType problem : cpl) {
                        resultList.add(new ProblemNotificationXml(mountPointNodeName, problem.getObjectIdRef(),
                                problem.getProblemName(), InternalSeverity.valueOf(problem.getProblemSeverity()),
                                problem.getSequenceNumber().toString(), InternalDateAndTime.valueOf(problem.getTimeStamp())));

                    }
                    microwaveEventListener.initCurrentProblem(resultList);
                    LOG.debug("DBRead NetworkElementCurrentProblems result: {}",resultList);
                    problemsFound = resultList.size();
                }
            }
        } catch (Exception e) {
            LOG.warn( "DBRead {} NetworkElementCurrentProblems not supported. Message '{}' ", mountPointNodeName, e.getMessage() );
        }
        return problemsFound;
    }


    /**
     * Read the NetworkElement part from database.
     * @return Optional with NetworkElement or empty
     */
    private NetworkElement readNetworkElement() {
        //Step 2.2: construct data and the relative iid
        // The schema path to identify an instance is
        // <i>CoreModel-CoreNetworkModule-ObjectClasses/NetworkElement</i>

        InstanceIdentifier<NetworkElement> networkElementIID = InstanceIdentifier
                .builder(NetworkElement.class)
                .build();

        //Step 2.3: read to the config data store
        return GenericTransactionUtils.readData(netconfNodeDataBroker, LogicalDatastoreType.OPERATIONAL, networkElementIID);
    }

    @Override
    public boolean checkAndConnectionToMediatorIsOk() {

        //Read to the config data store
        AtomicBoolean noErrorIndicator = new AtomicBoolean();
        AtomicReference<String> status = new AtomicReference<>();

        GenericTransactionUtils.readDataOptionalWithStatus(netconfNodeDataBroker, LogicalDatastoreType.OPERATIONAL, NETWORKELEMENT_IID, noErrorIndicator, status);
        LOG.debug("Status noErrorIndicator: {} statusTxt:{}",noErrorIndicator.get(), status.get());
        return noErrorIndicator.get();
    }

    @Override
    public boolean checkAndConnectionToNeIsOk() {

        synchronized (dmLock) {

            if (dmAirIfCurrentProblemsIID != null) {
                //Read to the config data store
                AtomicBoolean noErrorIndicator = new AtomicBoolean();
                AtomicReference<String> status = new AtomicReference<>();

                GenericTransactionUtils.readDataOptionalWithStatus(netconfNodeDataBroker, LogicalDatastoreType.OPERATIONAL, dmAirIfCurrentProblemsIID, noErrorIndicator, status);
                LOG.debug("Status noErrorIndicator: {} statusTxt:{}",noErrorIndicator.get(), status.get());
                return noErrorIndicator.get();
            }


        }

        return true;
    }



    /**
     * Get List of UUIDs for conditional packages from Networkelement<br>
     * Possible interfaces are:<br>
     *   MWPS, LTP(MWPS-TTP), MWAirInterfacePac, MicrowaveModel-ObjectClasses-AirInterface<br>
     *   ETH-CTP,LTP(Client), MW_EthernetContainer_Pac<br>
     *   MWS, LTP(MWS-CTP-xD), MWAirInterfaceDiversityPac, MicrowaveModel-ObjectClasses-AirInterfaceDiversity<br>
     *   MWS, LTP(MWS-TTP), ,MicrowaveModel-ObjectClasses-HybridMwStructure<br>
     *   MWS, LTP(MWS-TTP), ,MicrowaveModel-ObjectClasses-PureEthernetStructure<br>
     * @param ne Networkelement
     * @return Id List, never null.
     */
    private List<LpList> getLtpList( NetworkElement ne ) {

        List<LpList> res = new ArrayList<>();

        if (ne != null) {
            List<LtpRefList> ltpRefList = ne.getLtpRefList();
            if (ltpRefList == null) {
                LOG.debug("DBRead NE-Interfaces: null");
            } else {
                for (LtpRefList ltRefListE : ltpRefList ) {
                    List<LpList> lpList = ltRefListE.getLpList();
                    if (lpList == null) {
                        LOG.debug("DBRead NE-Interfaces Reference List: null");
                    } else {
                        for (LpList lp : lpList) {
                            ////LayerProtocolName layerProtocolName = lpListE.getLayerProtocolName();
                            //UniversalId uuId = lpListE.getUuid();
                            res.add(lp);
                        }
                    }
                }
            }
        } else {
            LOG.debug("DBRead NE: null");
        }

        //---- Debug
        if (LOG.isDebugEnabled()) {
            StringBuffer strBuf = new StringBuffer();
            for (LpList ltp : res) {
                if (strBuf.length() > 0) {
                    strBuf.append(", ");
                }
                strBuf.append(ltp.getLayerProtocolName().getValue());
                strBuf.append(':');
                strBuf.append(ltp.getUuid().getValue());
            }
            LOG.debug("DBRead NE-Interfaces: {}", strBuf.toString());
        }
        //---- Debug end

        return res;
    }

     private int readTheFaultsOfMWEthernetContainerPac(UniversalId interfacePacUuid) {

        int problemsFound = 0;
        LOG.info("DBRead Get {} MWEthernetContainerPac: {}", mountPointNodeName, interfacePacUuid.getValue());

        //Step 2.2: construct data and the relative iid
        InstanceIdentifier<EthernetContainerCurrentProblems> mwEthInterfaceIID = InstanceIdentifier
                .builder(MWEthernetContainerPac.class, new MWEthernetContainerPacKey(interfacePacUuid))
                .child(EthernetContainerCurrentProblems.class)
                .build();

        //Step 2.3: read to the config data store
        EthernetContainerCurrentProblems ethProblems = GenericTransactionUtils.readData(netconfNodeDataBroker, LogicalDatastoreType.OPERATIONAL, mwEthInterfaceIID);
        if (ethProblems == null) {
            LOG.debug("DBRead EthProblems Id {} no CurrentProblemsList", interfacePacUuid.getValue());
        } else {
            List<org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.ethernetcontainer.rev160902.ethernetcontainercurrentproblems.CurrentProblemList> ethProblemList = ethProblems.getCurrentProblemList();
            LOG.debug("DBRead EthProblems input: {}",ethProblemList);
            List<ProblemNotificationXml> resultList = new ArrayList<>();
            if (ethProblemList != null) {
                for ( ContainerCurrentProblemType problem : ethProblemList) {
                    resultList.add(new ProblemNotificationXml(mountPointNodeName, interfacePacUuid.getValue(),
                            problem.getProblemName(), InternalSeverity.valueOf(problem.getProblemSeverity()),
                            problem.getSequenceNumber().toString(), InternalDateAndTime.valueOf(problem.getTimeStamp())));

                }
                microwaveEventListener.initCurrentProblem(resultList);
            }
            LOG.debug("DBRead EthProblems result: {}",resultList);
            problemsFound = resultList.size();
        }
        return problemsFound;
     }

     private InstanceIdentifier<AirInterfaceCurrentProblems> getMWAirInterfacePacIId(UniversalId interfacePacUuid) {
         InstanceIdentifier<AirInterfaceCurrentProblems> mwAirInterfaceIID = InstanceIdentifier
                 .builder(MWAirInterfacePac.class, new MWAirInterfacePacKey(interfacePacUuid))
                 .child(AirInterfaceCurrentProblems.class)
                 .build();
         return mwAirInterfaceIID;
     }

     private int readTheFaultsOfMWAirInterfacePac(UniversalId interfacePacUuid) {

         int problemsFound = 0;
         LOG.info("DBRead Get {} MWAirInterfacePac: {}", mountPointNodeName, interfacePacUuid.getValue());
         //----
         //Step 2.2: construct data and the relative iid

         InstanceIdentifier<AirInterfaceCurrentProblems> mwAirInterfaceIID = getMWAirInterfacePacIId(interfacePacUuid);

         //Step 2.3: read to the config data store
         AirInterfaceCurrentProblems airProblems = GenericTransactionUtils.readData(netconfNodeDataBroker, LogicalDatastoreType.OPERATIONAL, mwAirInterfaceIID);

         if (airProblems == null) {
             LOG.debug("DBRead AirProblems Id {} no CurrentProblemsList", interfacePacUuid);
         } else {
             List<org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.airinterface.rev160901.airinterfacecurrentproblems.CurrentProblemList> airProblemList = airProblems.getCurrentProblemList();
             LOG.debug("DBRead AirProblems input: {}",airProblemList);
             if (airProblemList != null) {
                 List<ProblemNotificationXml> resultList = new ArrayList<>();
                 for ( AirInterfaceCurrentProblemType problem : airProblemList) {
                     resultList.add(new ProblemNotificationXml(mountPointNodeName, interfacePacUuid.getValue(),
                             problem.getProblemName(), InternalSeverity.valueOf(problem.getProblemSeverity()),
                             problem.getSequenceNumber().toString(), InternalDateAndTime.valueOf(problem.getTimeStamp())));

                 }
                 microwaveEventListener.initCurrentProblem(resultList);
                 LOG.debug("DBRead AirProblems result: {}",resultList);
                 problemsFound = resultList.size();
             }
         }
         return problemsFound;
     }

     public List<ExtendedAirInterfaceHistoricalPerformanceType> readTheHistoricalPerformanceDataOfMWAirInterfacePac(LpList lp) {

         String uuId = lp.getUuid().getValue();

         List<ExtendedAirInterfaceHistoricalPerformanceType> resultList = new ArrayList<>();
         LOG.debug("DBRead Get {} MWAirInterfacePac: {}", mountPointNodeName, uuId);
         //----
         UniversalId mwAirInterfacePacuuId = new UniversalId(uuId);
         //Step 2.1: construct data and the relative iid
         InstanceIdentifier<AirInterfaceConfiguration> mwAirInterfaceConfigurationIID = InstanceIdentifier
                 .builder(MWAirInterfacePac.class, new MWAirInterfacePacKey(mwAirInterfacePacuuId))
                 .child(AirInterfaceConfiguration.class)
                 .build();
         AirInterfaceConfiguration airConfiguration = GenericTransactionUtils.readData(netconfNodeDataBroker, LogicalDatastoreType.OPERATIONAL, mwAirInterfaceConfigurationIID);

         //Step 2.2: construct data and the relative iid
         InstanceIdentifier<AirInterfaceHistoricalPerformances> mwAirInterfaceHistoricalPerformanceIID = InstanceIdentifier
                 .builder(MWAirInterfacePac.class, new MWAirInterfacePacKey(mwAirInterfacePacuuId))
                 .child(AirInterfaceHistoricalPerformances.class)
                 .build();

         //Step 2.3: read to the config data store
         AirInterfaceHistoricalPerformances airHistoricalPerformanceData = GenericTransactionUtils.readData(netconfNodeDataBroker, LogicalDatastoreType.OPERATIONAL, mwAirInterfaceHistoricalPerformanceIID);

         if (airHistoricalPerformanceData == null) {
             LOG.debug("DBRead MWAirInterfacePac Id {} no HistoricalPerformances", mwAirInterfacePacuuId);
         } else {
             List<org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.airinterface.rev160901.airinterfacehistoricalperformances.HistoricalPerformanceDataList> airHistPMList = airHistoricalPerformanceData.getHistoricalPerformanceDataList();
             LOG.debug("DBRead MWAirInterfacePac Id {} Records intermediate: {}",mwAirInterfacePacuuId, airHistPMList.size());
             if (airHistPMList != null) {
                 for ( org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.typedefinitions.rev160902.AirInterfaceHistoricalPerformanceType pmRecord : airHistPMList) {
                     resultList.add(new ExtendedAirInterfaceHistoricalPerformanceType(pmRecord, airConfiguration));
                 }
             }
         }
         LOG.debug("DBRead MWAirInterfacePac Id {} Records result: {}",mwAirInterfacePacuuId, resultList.size());
         return resultList;
     }

     public List<ContainerHistoricalPerformanceType> readTheHistoricalPerformanceDataOfEthernetContainer(LpList lp) {

         final String myName = "MWEthernetContainerPac";
         String uuId = lp.getUuid().getValue();

         List<ContainerHistoricalPerformanceType> resultList = new ArrayList<>();
         LOG.debug("DBRead Get {} : {}", mountPointNodeName, myName,uuId);
         //----
         UniversalId ethContainerPacuuId = new UniversalId(uuId);
         //Step 2.2: construct data and the relative iid
         InstanceIdentifier<EthernetContainerHistoricalPerformances> ethContainerIID = InstanceIdentifier
                 .builder(MWEthernetContainerPac.class, new MWEthernetContainerPacKey(ethContainerPacuuId))
                 .child(EthernetContainerHistoricalPerformances.class)
                 .build();

         //Step 2.3: read to the config data store
         EthernetContainerHistoricalPerformances ethContainerHistoricalPerformanceData = GenericTransactionUtils.readData(netconfNodeDataBroker, LogicalDatastoreType.OPERATIONAL, ethContainerIID);

         if (ethContainerHistoricalPerformanceData == null) {
             LOG.debug("DBRead {} Id {} no HistoricalPerformances", myName, ethContainerPacuuId);
         } else {
             List<org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.objectclasses.ethernetcontainer.rev160902.ethernetcontainerhistoricalperformances.HistoricalPerformanceDataList> airHistPMList = ethContainerHistoricalPerformanceData.getHistoricalPerformanceDataList();
             LOG.debug("DBRead {} Id {} Records intermediate: {}", myName, ethContainerPacuuId, airHistPMList.size());
             if (airHistPMList != null) {
                 for ( org.opendaylight.yang.gen.v1.uri.onf.microwavemodel.typedefinitions.rev160902.ContainerHistoricalPerformanceType pmRecord : airHistPMList) {
                     resultList.add(pmRecord);
                 }
             }
         }
         LOG.debug("DBRead {} Id {} Records result: {}", myName, ethContainerPacuuId, resultList.size());
         return resultList;
     }

    private List<? extends OTNHistoryData> readTheHistoricalPerformanceData(LpList lp) {
         ONFLayerProtocolName lpName = ONFLayerProtocolName.valueOf(lp.getLayerProtocolName());

         switch( lpName ) {
             case MWAirInterface:
                 return readTheHistoricalPerformanceDataOfMWAirInterfacePac(lp);

             case EthernetContainer10:
                 return readTheHistoricalPerformanceDataOfEthernetContainer(lp);

             case EthernetContainer12:
             case EthernetPhysical:
             case TDMContainer:
             case Ethernet:
             case Structure:
             case Unknown:
                 LOG.debug("Do not read HistoricalPM data for", lpName);
                 break;
         }
         return new ArrayList<>();
     }

    @Override
    public AllPm getHistoricalPM() {
        synchronized ( pmLock ) {
            if (pmLp != null) {
                AllPm allPm = new AllPm();
                LpList lp = pmLp;

                List<? extends OTNHistoryData> resultList = readTheHistoricalPerformanceData(lp);

                for (OTNHistoryData perf : resultList) {

                    switch(perf.getGranularityPeriod()) {
                    case PERIOD15MIN: {
                        EsHistoricalPerformance15Minutes pm = new EsHistoricalPerformance15Minutes(mountPointNodeName, lp)
                                .setHistoricalRecord15Minutes(perf);
                        allPm.add(pm);
                    }
                    break;

                    case PERIOD24HOURS: {
                        LOG.debug("Write 24h create");
                        EsHistoricalPerformance24Hours pm = new EsHistoricalPerformance24Hours(mountPointNodeName, lp)
                                .setHistoricalRecord24Hours(perf);
                        LOG.debug("Write 24h write to DB");
                        allPm.add(pm);
                    }

                    break;
                    default:
                        LOG.warn("Unknown granularity {}",perf.getGranularityPeriod());
                        break;
                    }
                }
                return allPm;
            } else {
                return AllPm.EMPTY;
            }
        }

    }

    @Override
    public void resetPMIterator() {
        synchronized ( pmLock ) {
            interfaceListIterator = interfaceList.iterator();
        }
    }

    @Override
    public boolean hasNext() {
        synchronized ( pmLock ) {
            return interfaceListIterator.hasNext();
        }
    }

    @Override
    public void next() {
        synchronized ( pmLock ) {
            pmLp = interfaceListIterator.next();
        }
    }

    @Override
    public String pmStatusToString() {
        synchronized ( pmLock ) {
            return pmLp == null ? "no interface" : pmLp.getLayerProtocolName().getValue();
        }
    }

    @Override
    public int removeAllCurrentProblemsOfNode() {
        return microwaveEventListener.removeAllCurrentProblemsOfNode();
    }

    @Override
    public void doRegisterMicrowaveEventListener(MountPoint mountPoint) {
        final Optional<NotificationService> optionalNotificationService = mountPoint.getService(NotificationService.class);
        final NotificationService notificationService = optionalNotificationService.get();
        notificationService.registerNotificationListener(microwaveEventListener);
    }


}
