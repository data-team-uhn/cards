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
import string
import datetime

argparser = argparse.ArgumentParser()
argparser.add_argument('file', help='Output file name', type=argparse.FileType('w'))
argparser.add_argument('-n', help='Number of patients discharges to generate [default: 3]', default=3, type=int)
argparser.add_argument('--basedate', help='Date to generate patient discharge entries from [default: today] [format: YYYY-MM-dd]', default=datetime.date.today(), type=lambda s: datetime.datetime.strptime(s, '%Y-%m-%d'))
argparser.add_argument('--time_spread_seconds', help='Number of seconds before the base date to generate patient discharge entries from [default: one week]', default=7*24*60*60, type=int)
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
    PAT_FIRST_NAME varchar(255) NULL,
    PAT_LAST_NAME varchar(255) NULL,
    EMAIL_ADDRESS varchar(255) NULL,
    HOSP_DISCHARGE_DTTM datetime2 NULL,
    DISCH_DEPT_NAME varchar(254) NULL,
    DISCH_LOC_NAME varchar(254) NULL,
    EMAIL_CONSENT_YN varchar(3),
    LoadTime datetime2 NULL
);

-- Insert test data
'''
)

HOSPITALS_TO_DEPARTMENTS = {}
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'] = []
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-4MA Cardiovascular Surgery")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-4MB Cardiovascular Surgery")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-5MB Cardiology")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-6MA MOT/Nephrology")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-6MB Thoracic Surgery/Respirology")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-7MA Multi Organ Transplant Unit TG-7MB Multi Organ Transplant Unit")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-ES 10 Surgical Oncology")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-ES 6 General Medicine")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-ES 9 General Surgery")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-ES13 General Medicine")
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-ES14 General Medicine")

HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'] = []
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-3B Fell Pavilion")
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-4B Fell Pavilion")
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-5A Fell Pavilion")
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-5B Fell Pavilion")
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-6A Fell Pavilion")
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-8A Fell Pavilion")
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-8B Fell Pavilion")
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-9A Fell Pavilion")
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-9B Fell Pavilion")

HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'] = []
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-14A Leukemia/Lymphoma")
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-14B Bone Marrow Transplant")
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-15A Leukemia & Lymphoma Unit")
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-15B BMT & Leukemia/Lymphoma")
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-15C Auto Transplant Unit")
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-17A Breast, Gyn, GI & GU")
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-17B Head & Neck, Sarc & Lung")
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-18B Short Term Care")

# Insert test data
for i in range(args.n):
    mrn = random.randint(0, 9999999)
    first_name = "".join([random.choice(string.ascii_lowercase) for n in range(random.randint(5, 10))])
    last_name = "".join([random.choice(string.ascii_lowercase) for n in range(random.randint(5, 10))])
    email = 'test' + str(mrn) + '@test.com'
    entry_time = args.basedate - datetime.timedelta(seconds=random.randint(0, args.time_spread_seconds))
    entry_time_str = entry_time.strftime('%Y-%m-%d %H:%M:%S')
    disch_dept_location = random.choice(list(HOSPITALS_TO_DEPARTMENTS.keys()))
    disch_dept_name = random.choice(HOSPITALS_TO_DEPARTMENTS[disch_dept_location])
    email_consent_yn = random.choice(['Yes', 'No'])

    args.file.write("INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]")
    args.file.write("\t(PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime)\n")
    args.file.write("\tVALUES\n")
    args.file.write("\t(%07d, '%s', '%s', '%s', '%s', '%s', '%s', '%s', CAST(GETDATE()-1 AS DATE))\n"
        % (mrn, first_name, last_name, email, entry_time_str, disch_dept_name, disch_dept_location, email_consent_yn))

args.file.close()
