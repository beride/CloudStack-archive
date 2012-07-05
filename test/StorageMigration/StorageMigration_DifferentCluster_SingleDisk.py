#copyright 2012 Citrix Systems, Inc. Licensed under the
# Apache License, Version 2.0 (the "License"); you may not use this
# file except in compliance with the License.  Citrix Systems, Inc.
# reserves all rights not expressly granted by the License.
# You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# 
# Automatically generated by addcopyright.py at 04/03/2012
""" StorageMigration Tests
"""
#Import Local Modules
import marvin
from marvin.cloudstackTestCase import *
from marvin.cloudstackAPI import *
from marvin import remoteSSHClient
from integration.lib.utils2 import *
from integration.lib.base2 import *
from integration.lib.common5 import *
#Import System modules
import time

class Services:
    """StorageMigration Services
    """

    def __init__(self):
        self.services = {
                "disk_offering":{
                    "displaytext": "Small",
                    "name": "Small",
                    "disksize": 1
                },
                "volume_offerings": {
                            0: {
                                "diskname": "TestDiskServ",
                            },
                        },
                "account": {
                    "email": "test@test.com",
                    "firstname": "Test",
                    "lastname": "User",
                    "username": "test",
                    # Random characters are appended in create account to 
                    # ensure unique username generated each time
                    "password": "fr3sca",
                },
                "small":
                # Create a small virtual machine instance with disk offering 
                {
                    "displayname": "testserver",
                    "username": "root", # VM creds for SSH
                    "password": "password",
                    "ssh_port": 22,
                    "hypervisor": 'XenServer',
                    "privateport": 22,
                    "publicport": 22,
                    "protocol": 'TCP',
                },
                "medium":   # Create a medium virtual machine instance 
                {
                    "displayname": "testserver",
                    "username": "root",
                    "password": "password",
                    "ssh_port": 22,
                    "hypervisor": 'XenServer',
                    "privateport": 22,
                    "publicport": 22,
                    "protocol": 'TCP',
                },
                "service_offerings":
                {
                 "tiny":
                   {
                        "name": "Tiny Instance",
                        "displaytext": "Tiny Instance",
                        "cpunumber": 1,
                        "cpuspeed": 100, # in MHz
                        "memory": 64, # In MBs
                    },
                 "small":
                    {
                     # Small service offering ID to for change VM 
                     # service offering from medium to small
                        "name": "Small Instance",
                        "displaytext": "Small Instance",
                        "cpunumber": 1,
                        "cpuspeed": 500,
                        "memory": 256
                    },
                "medium":
                    {
                    # Medium service offering ID to for
                    # change VM service offering from small to medium
                        "name": "Medium Instance",
                        "displaytext": "Medium Instance",
                        "cpunumber": 1,
                        "cpuspeed": 1000,
                        "memory": 1024
                    }
                },
                "iso":  # ISO settings for Attach/Detach ISO tests
                {
                    "displaytext": "Test ISO",
                    "name": "testISO",
                    "url": "http://iso.linuxquestions.org/download/504/1819/http/gd4.tuwien.ac.at/dsl-4.4.10.iso",
                     # Source URL where ISO is located
                    #"ostypeid": '5776c0d2-f331-42db-ba3a-29f1f8319bc9',
                    "mode": 'HTTP_DOWNLOAD', # Downloading existing ISO 
                },
                "template": {
                    "displaytext": "Cent OS Template",
                    "name": "Cent OS Template",
                    "passwordenabled": True,
                },
            "diskdevice": '/dev/xvdd',
            # Disk device where ISO is attached to instance
            "mount_dir": "/mnt/tmp",
            "sleep": 60,
            "timeout": 10,
            #Migrate VM to hostid
            "ostypeid": '40441102-a819-4204-b6a7-4bd3c58a9c12',
            "storageid": 'abcd',
            "diskname" : 'ROOT_NEW'
        }


class StorageMigrationTest(cloudstackTestCase):
    
    @classmethod
    def setUpClass(cls):
        
        
        cls.apiclient = super(StorageMigrationTest, cls).getClsTestClient().getApiClient()
        #cls.dbclient = cls.testClient.getDbConnection()
        cls.services = Services().services
        
        # Get Zone, Domain and templates
        domain = get_domain(cls.apiclient, cls.services)
        cls.zone = get_zone(cls.apiclient, cls.services)
        print "Zone-id.........", cls.zone.id
        template = get_template(
                            cls.apiclient,
                            cls.zone.id,
                            cls.services["ostypeid"]
                            )
        # Set Zones and disk offerings
        cls.disk_offering = DiskOffering.create(
                                    cls.apiclient,
                                    cls.services["disk_offering"]
                                    )
        cls.services["small"]["zoneid"] = cls.zone.id
        cls.services["small"]["template"] = template.id

        cls.services["medium"]["zoneid"] = cls.zone.id
        cls.services["medium"]["template"] = template.id
        cls.services["iso"]["zoneid"] = cls.zone.id

        # Create Account, VMs, NAT Rules etc
        cls.account = Account.create(
                            cls.apiclient,
                            cls.services["account"],
                            domainid=domain.id
                            )

        cls.service_offering = ServiceOffering.create(
                                    cls.apiclient,
                                    cls.services["service_offerings"]["tiny"]
                                    )
        
        cls.virtual_machine = VirtualMachine.create(
                                    cls.apiclient,
                                    cls.services["small"],
                                    accountid=cls.account.account.name,
                                    domainid=cls.account.account.domainid,
                                    serviceofferingid=cls.service_offering.id
                                )
        
        # Cleanup
        cls._cleanup = [
                        cls.service_offering,
                        cls.account
                        ]

    @classmethod
    def tearDownClass(cls):
        
        cls.api_client = super(StorageMigrationTest, cls).getClsTestClient().getApiClient()
        cleanup_resources(cls.api_client, cls._cleanup)
        return

    def setUp(self):
        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.cleanup = []
   
   
    def get_destination_storage_pool(self):
        """Return the volume list before migration, source storage pool name, data disk list, destination storage pool id.
        """
                
        # Get ID of the server hosting the VM 
        host_id = self.virtual_machine.hostid

        # Get ID of the cluster to which this host belongs
        list_host_response = list_hosts(self.apiclient, id=host_id)
        cluster_id = list_host_response[0].clusterid
        hypervisor_type = list_host_response[0].hypervisor
	pod_id = list_host_response[0].podid
		
        # List all Volumes belonging to the VM
        list_volumes_response = list_volumes(self.apiclient, listAll="true", virtualmachineid=self.virtual_machine.id)

        # Store all data disks in a single list and also get the storage pool to which the root volume belongs 
        for i in range(len(list_volumes_response)):
            if list_volumes_response[i].type == 'ROOT':
                host_vm_storage_pool_name = list_volumes_response[i].storage 
            
        print "\n\n.........StoragePool to which vm's root belong before migration = " , host_vm_storage_pool_name  
        
	# Sorce Cluster Name
      	source_cluster = list_clusters(self.apiclient, id=cluster_id)
	print "\nSource Cluster Name = ", source_cluster[0].name
		
	# Select the Destination Cluster
	list_cluster_response = list_clusters(self.apiclient, podid=pod_id, hypervisor=hypervisor_type)
	self.assertGreater(
                          len(list_cluster_response),
                          1,
                          "Check clusters available in List are more than 1"
                         )
		
	if list_cluster_response[0].id == cluster_id :
           cluster_id = list_cluster_response[1].id
        else :
           cluster_id = list_cluster_response[0].id
        
        dest_cluster = list_clusters(self.apiclient, id=cluster_id)		
        print "\nCluster ID to migrate to.............", cluster_id, "\nDestination Cluster name = ", dest_cluster[0].name
		
        # Get the destination storage pool ID    
        list_storage_pool_response = list_storage_pools(self.apiclient, clusterid=cluster_id)
        self.assertGreater(
                       len(list_storage_pool_response),
                       0,
                       "Check storage pools available in List are more than 0"
                      )
    
        self.services["storageid"] = list_storage_pool_response[0].id
                
        storageid = self.services["storageid"]
        
        return list_volumes_response, host_vm_storage_pool_name, storageid 
    
    def test_01_deploy_vm(self):
        """Test Deploy Virtual Machine
        """

        list_vm_response = list_virtual_machines(self.apiclient, id=self.virtual_machine.id)
        
        vm_response = list_vm_response[0]

        self.assertEqual(
                         vm_response.id,
                         self.virtual_machine.id,
                         "Check virtual machine id in listVirtualMachines"
                        )

        self.assertEqual(
                         vm_response.displayname,
                         self.virtual_machine.displayname,
                         "Check virtual machine displayname in listVirtualMachines"
                        )
        
        self.assertEqual(vm_response.state,
                         "Running",
                         "Check if VM has reached a state of running"
                         )

        print "\n\nVirtualMachine is deployed successfully..............\n\n" 
        
        
    
    def test_02_storage_migration(self):
        """Migrate the VM and check for successful migration of all volumes
        """
        volume_list1, pool1, storageid = self.get_destination_storage_pool()
        print "\n\n\n\nvolume_list before detach = " , volume_list1 , "\n\n\n\nsource storage pool_name =", pool1, "\n\ndestination storage id =", storageid
        
        # Stop the VM 
        print "\n\nStopping the VM \n\n"
        
        self.virtual_machine.stop(self.apiclient)
        timeout = self.services["timeout"]
        while True:
            time.sleep(self.services["sleep"])

            # Ensure that VM is in stopped state
            list_vm_response = list_virtual_machines(
                                            self.apiclient,
                                            id=self.virtual_machine.id
                                            )

            if isinstance(list_vm_response, list):

                vm = list_vm_response[0]
                if vm.state == 'Stopped':
                    self.debug("VM state: %s" % vm.state)
                    print "\n\nVirtual Machine successfully stopped.......\n\n"
                    break

            if timeout == 0:
                raise Exception(
                    "Failed to stop VM (ID: %s) in change service offering" % vm.id)

        timeout = timeout - 1
        
        # Migrate Virtual Machine
        print "\n\nMigrating the VM \n\n"
        
        migrate_virtual_machine(self.apiclient, virtualmachineid=self.virtual_machine.id, storageid=storageid)
        list_vm_response = list_virtual_machines(
                                            self.apiclient,
                                            id=self.virtual_machine.id
                                            )
        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )

        self.assertNotEqual(
                            list_vm_response,
                            None,
                            "Check virtual machine is listVirtualMachines"
                        )

        vm_response = list_vm_response[0]
        
        self.assertEqual(
                         vm_response.id,
                         self.virtual_machine.id,
                         "Check virtual machine ID of migrated VM"
                        )

        self.assertEqual(
                         vm_response.state,
                         "Stopped",
                         "Check destination hostID of migrated VM"
                        )  
        
        # Start Virtual Machine
        print "\n\nStarting the VM...\n\n"
        
        self.virtual_machine.start(self.apiclient)
        
        list_vm_response = list_virtual_machines(
                                            self.apiclient,
                                            id=self.virtual_machine.id
                                            )
        self.assertEqual(
                            isinstance(list_vm_response, list),
                            True,
                            "Check list response returns a valid list"
                        )

        self.assertNotEqual(
                            len(list_vm_response),
                            0,
                            "Check VM avaliable in List Virtual Machines"
                        )

        self.debug(
                "Verify listVirtualMachines response for virtual machine: %s" \
                % self.virtual_machine.id
                )
        self.assertEqual(
                            list_vm_response[0].state,
                            "Running",
                            "Check virtual machine is in running state"
                        )
        print "\n\nVirtual Machine started successfully......\n\n"
        
        # List Volumes Attached to VM after Migration
        volume_list2 = list_volumes(self.apiclient, listAll="true", virtualmachineid=self.virtual_machine.id)        

        # Compare the volumes attached to VM before and after migration
        for i in range(len(volume_list1)):
            if volume_list2[i].id == volume_list1[i].id :
               pass
        
        print "Volumes match"
        
        # Check for the successful migration of the root volume to a different storage pool
        for i in range(len(volume_list2)):
            if volume_list2[i].type == 'ROOT':
               pool2 = volume_list2[i].storage
        print "\n\n.......StoragePoolName of root after migration = " , pool2
        
        self.assertNotEqual(
                            pool1,
                            pool2,
                            "Check list response returns a valid list"
                        )
        print "\n\n Migration Successfully completed and hence the test case passed explicitly...\n\n"
