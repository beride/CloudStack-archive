// Copyright 2012 Citrix Systems, Inc. Licensed under the
// Apache License, Version 2.0 (the "License"); you may not use this
// file except in compliance with the License.  Citrix Systems, Inc.
// reserves all rights not expressly granted by the License.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// 
// Automatically generated by addcopyright.py at 04/03/2012
package com.cloud.cluster.agentlb.dao;

import java.util.Date;
import java.util.List;

import com.cloud.cluster.agentlb.HostTransferMapVO;
import com.cloud.cluster.agentlb.HostTransferMapVO.HostTransferState;
import com.cloud.utils.db.GenericDao;

public interface HostTransferMapDao extends GenericDao<HostTransferMapVO, Long> {

    List<HostTransferMapVO> listHostsLeavingCluster(long currentOwnerId);

    List<HostTransferMapVO> listHostsJoiningCluster(long futureOwnerId);

    HostTransferMapVO startAgentTransfering(long hostId, long currentOwner, long futureOwner);

    boolean completeAgentTransfer(long hostId);
    
    List<HostTransferMapVO> listBy(long futureOwnerId, HostTransferState state);
    
    HostTransferMapVO findActiveHostTransferMapByHostId(long hostId, Date cutTime);
    
    boolean startAgentTransfer(long hostId);
    
    HostTransferMapVO findByIdAndFutureOwnerId(long id, long futureOwnerId);
    
    HostTransferMapVO findByIdAndCurrentOwnerId(long id, long currentOwnerId);
}
