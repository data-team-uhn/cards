#!/usr/bin/env python
# -*- coding: utf-8 -*-

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import argparse
import random
import datetime

argparser = argparse.ArgumentParser()
argparser.add_argument('file', help='Output file name', type=argparse.FileType('w'))
argparser.add_argument('-n', help='Number of arguments [default: 3]', default=3, type=int)
args = argparser.parse_args()

# Preamble
args.file.write(
    '''
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

-- Create the schema if it doesn't already exist
-- Note about EXEC: CREATE SCHEMA must be the first statement
-- but we can't combine that with a conditional unless we use EXEC
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'path')
BEGIN
    EXEC('CREATE SCHEMA path')
END

-- Remove the table if it already exists
IF OBJECT_ID('path.CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS', 'U') IS NOT NULL 
    DROP TABLE [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS];

CREATE TABLE [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (
    PAT_MRN varchar(102) NULL,
    EMAIL_ADDRESS varchar(255) NULL,
    ENTRY_TIME datetime2 NULL,
    DISCH_DEPT_NAME varchar(254) NULL,
    EMAIL_CONSENT_YN varchar(3),
    LoadTime datetime2 NULL
);

-- Insert test data
'''
)

# Insert test data
for i in range(args.n):
    mrn = random.randint(0, 9999999)
    email = 'test' + str(mrn) + '@test.com'
    entry_time = datetime.date.today() - datetime.timedelta(seconds=random.randint(0, 7 * 24 * 60 * 60))
    entry_time_str = entry_time.strftime('%Y-%m-%d %H:%M:%S')
    disch_dept_name = ('TG-EMERGENCY', 'TW-EMERGENCY')[random.randint(0, 1)]
    email_consent_yn = ('Yes', 'No')[random.randint(0, 1)]

    args.file.write("INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]")
    args.file.write("\t(PAT_MRN, EMAIL_ADDRESS, ENTRY_TIME, DISCH_DEPT_NAME, EMAIL_CONSENT_YN, LoadTime)\n")
    args.file.write("\tVALUES\n")
    args.file.write("\t(%07d, '%s', '%s', '%s', '%s', CAST(GETDATE() AS DATE))\n"
        % (mrn, email, entry_time_str, disch_dept_name, email_consent_yn))

args.file.close()