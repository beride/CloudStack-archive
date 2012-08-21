package com.cloud.baremetal.networkservice;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.cloud.agent.api.Answer;
import com.cloud.baremetal.database.BaremetalPxeDao;
import com.cloud.baremetal.database.BaremetalPxeVO;
import com.cloud.baremetal.networkservice.BaremetalPxeManager.BaremetalPxeType;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.PhysicalNetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkServiceProviderDao;
import com.cloud.network.dao.PhysicalNetworkServiceProviderVO;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ServerResource;
import com.cloud.uservm.UserVm;
import com.cloud.utils.component.Inject;
import com.cloud.utils.db.SearchCriteria2;
import com.cloud.utils.db.SearchCriteriaService;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachineProfile;

@Local(value = BaremetalPxeService.class)
public class BaremetalKickStartServiceImpl extends BareMetalPxeServiceBase implements BaremetalPxeService {
    private static final Logger s_logger = Logger.getLogger(BaremetalKickStartServiceImpl.class);
    @Inject
    ResourceManager _resourceMgr;
    @Inject
    PhysicalNetworkDao _physicalNetworkDao;
    @Inject
    PhysicalNetworkServiceProviderDao _physicalNetworkServiceProviderDao;
    @Inject
    HostDetailsDao _hostDetailsDao;
    @Inject
    BaremetalPxeDao _pxeDao;

    @Override
    public boolean prepare(VirtualMachineProfile<UserVmVO> profile, NicProfile nic, DeployDestination dest, ReservationContext context) {
        SearchCriteriaService<BaremetalPxeVO, BaremetalPxeVO> sc = SearchCriteria2.create(BaremetalPxeVO.class);
        sc.addAnd(sc.getEntity().getDeviceType(), Op.EQ, BaremetalPxeType.KICK_START.toString());
        sc.addAnd(sc.getEntity().getPodId(), Op.EQ, dest.getPod().getId());
        BaremetalPxeVO pxeVo = sc.find();
        if (pxeVo == null) {
            throw new CloudRuntimeException("No kickstart PXE server found in pod: " + dest.getPod().getId() + ", you need to add it before starting VM");
        }

        try {
            String tpl = profile.getTemplate().getUrl();
            assert tpl != null : "How can a null template get here!!!";
            String[] tpls = tpl.split(";");
            assert tpls.length == 2 : "Template is not correctly encoded. " + tpl;
            PrepareKickstartPxeServerCommand cmd = new PrepareKickstartPxeServerCommand();
            cmd.setKsFile(tpls[0]);
            cmd.setRepo(tpls[1]);
            Answer aws = _agentMgr.send(pxeVo.getId(), cmd);
            if (!aws.getResult()) {
                if (!aws.getResult()) {
                    s_logger.warn("Unable to set host: " + dest.getHost().getId() + " to PXE boot because " + aws.getDetails());
                }
            }
            return aws.getResult();
        } catch (Exception e) {
            s_logger.warn("Cannot prepare PXE server", e);
            return false;
        }
    }

    @Override
    public boolean prepareCreateTemplate(Long pxeServerId, UserVm vm, String templateUrl) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public BaremetalPxeVO addPxeServer(AddBaremetalPxeCmd cmd) {
        AddBaremetalKickStartPxeCmd kcmd = (AddBaremetalKickStartPxeCmd)cmd;
        PhysicalNetworkVO pNetwork = null;
        long zoneId;
        
        if (cmd.getPhysicalNetworkId() == null || cmd.getUrl() == null || cmd.getUsername() == null || cmd.getPassword() == null) {
            throw new InvalidParameterValueException("At least one of the required parameters(physical network id, url, username, password) is null");
        } 
        
        pNetwork = _physicalNetworkDao.findById(cmd.getPhysicalNetworkId());
        if (pNetwork == null) {
            throw new InvalidParameterValueException("Could not find phyical network with ID: " + cmd.getPhysicalNetworkId());
        }
        zoneId = pNetwork.getDataCenterId();
        
        PhysicalNetworkServiceProviderVO ntwkSvcProvider = _physicalNetworkServiceProviderDao.findByServiceProvider(pNetwork.getId(), BaremetalPxeManager.BAREMETAL_PXE_SERVICE_PROVIDER.getName());
        if (ntwkSvcProvider == null) {
            throw new CloudRuntimeException("Network Service Provider: " + BaremetalPxeManager.BAREMETAL_PXE_SERVICE_PROVIDER.getName() +
                    " is not enabled in the physical network: " + cmd.getPhysicalNetworkId() + "to add this device");
        } else if (ntwkSvcProvider.getState() == PhysicalNetworkServiceProvider.State.Shutdown) {
            throw new CloudRuntimeException("Network Service Provider: " + ntwkSvcProvider.getProviderName() +
                    " is in shutdown state in the physical network: " + cmd.getPhysicalNetworkId() + "to add this device");
        }
        
        List<HostVO> pxes = _resourceMgr.listAllHostsInOneZoneByType(Host.Type.BaremetalPxe, zoneId);
        if (!pxes.isEmpty()) {
            throw new InvalidParameterValueException("Already had a PXE server zone: " + zoneId);
        }
        
        String tftpDir = kcmd.getTftpDir();
        if (tftpDir == null) {
            throw new InvalidParameterValueException("No TFTP directory specified");
        }
        
        URI uri;
        try {
            uri = new URI(cmd.getUrl());
        } catch (Exception e) {
            s_logger.debug(e);
            throw new InvalidParameterValueException(e.getMessage());
        }
        String ipAddress = uri.getHost();
        
        String guid = getPxeServerGuid(Long.toString(zoneId), BaremetalPxeType.KICK_START.toString(), ipAddress);
        
        ServerResource resource = null;
        Map params = new HashMap<String, String>();
        params.put(BaremetalPxeService.PXE_PARAM_ZONE, Long.toString(zoneId));
        params.put(BaremetalPxeService.PXE_PARAM_IP, ipAddress);
        params.put(BaremetalPxeService.PXE_PARAM_USERNAME, cmd.getUsername());
        params.put(BaremetalPxeService.PXE_PARAM_PASSWORD, cmd.getPassword());
        params.put(BaremetalPxeService.PXE_PARAM_TFTP_DIR, tftpDir);
        params.put(BaremetalPxeService.PXE_PARAM_GUID, guid);
        resource = new BaremetalKickStartPxeResource();
        try {
            resource.configure("KickStart PXE resource", params);
        } catch (Exception e) {
            throw new CloudRuntimeException(e.getMessage());
        }
        
        Host pxeServer = _resourceMgr.addHost(zoneId, resource, Host.Type.BaremetalPxe, params);
        if (pxeServer == null) {
            throw new CloudRuntimeException("Cannot add PXE server as a host");
        }
        
        BaremetalPxeVO vo = new BaremetalPxeVO();
        Transaction txn = Transaction.currentTxn();
        vo.setHostId(pxeServer.getId());
        vo.setNetworkServiceProviderId(ntwkSvcProvider.getId());
        vo.setPhysicalNetworkId(kcmd.getPhysicalNetworkId());
        vo.setDeviceType(BaremetalPxeType.KICK_START.toString());
        txn.start();
        _pxeDao.persist(vo);
        txn.commit();
        return vo;
    }

    @Override
    public BaremetalPxeResponse getApiResponse(BaremetalPxeVO vo) {
        BaremetalPxeKickStartResponse response = new BaremetalPxeKickStartResponse();
        response.setId(vo.getId());
        response.setPhysicalNetworkId(vo.getPhysicalNetworkId());
        response.setPodId(vo.getPodId());
        Map<String, String> details = _hostDetailsDao.findDetails(vo.getHostId());
        response.setTftpDir(details.get(BaremetalPxeService.PXE_PARAM_TFTP_DIR));
        return response;
    }

    @Override
    public List<BaremetalPxeResponse> listPxeServers(ListBaremetalPxePingServersCmd cmd) {
        SearchCriteriaService<BaremetalPxeVO, BaremetalPxeVO> sc = SearchCriteria2.create(BaremetalPxeVO.class);
        sc.addAnd(sc.getEntity().getDeviceType(), Op.EQ, BaremetalPxeType.KICK_START.toString());
        if (cmd.getPodId() != null) {
            sc.addAnd(sc.getEntity().getPodId(), Op.EQ, cmd.getPodId());
            if (cmd.getId() != null) {
                sc.addAnd(sc.getEntity().getId(), Op.EQ, cmd.getId());
            }
        }
        List<BaremetalPxeVO> vos = sc.list();
        List<BaremetalPxeResponse> responses = new ArrayList<BaremetalPxeResponse>(vos.size());
        for (BaremetalPxeVO vo : vos) {
            responses.add(getApiResponse(vo));
        }
        return responses;
    }

}