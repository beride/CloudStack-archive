#!/usr/bin/env bash
# Copyright 2012 Citrix Systems, Inc. Licensed under the
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



 

# $Id: checkchildren.sh 9132 2010-06-04 20:17:43Z manuel $ $HeadURL: svn://svn.lab.vmops.com/repos/vmdev/java/scripts/storage/checkchildren.sh $
# checkchdilren.sh -- Does this path has children?

usage() {
  printf "Usage:  %s path \n" $(basename $0) >&2
}

if [ $# -ne 1 ]
then
  usage
  exit 1
fi

#set -x

fs=$1
if [ "${fs:0:1}" != "/" ]
then
  fs="/"$fs
fi

if [ -d $fs ]
then
  if [ `ls -l $fs | grep -v total | wc -l | awk '{print $1}'` -eq 0 ]
  then
    exit 0
  else
    exit 1
  fi
fi

exit 0
