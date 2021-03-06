/*
 * Copyright (C) 2011 Citrix Systems, Inc.  All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloud.bridge.service.core.ec2;

import java.util.ArrayList;
import java.util.List;

public class EC2DescribeSnapshots {

	private List<String> snapshotSet = new ArrayList<String>();    // a list of strings identifying snapshots
	private EC2SnapshotFilterSet sfs = null;

	public EC2DescribeSnapshots() {
	}

	public void addSnapshotId( String param ) {
		snapshotSet.add( param );
	}
	
	public String[] getSnapshotSet() {
		return snapshotSet.toArray(new String[0]);
	}
	
	public EC2SnapshotFilterSet getFilterSet() {
		return sfs;
	}
	
	public void setFilterSet( EC2SnapshotFilterSet param ) {
		sfs = param;
	}
}
