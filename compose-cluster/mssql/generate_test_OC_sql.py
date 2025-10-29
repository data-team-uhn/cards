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
IF OBJECT_ID('path.V_PatientExperienceSurveySamplingTest', 'U') IS NOT NULL
    DROP TABLE [path].[V_PatientExperienceSurveySamplingTest];

CREATE TABLE [path].[V_PatientExperienceSurveySamplingTest] (
    PAT_MRN varchar(102) NULL,
    PAT_FIRST_NAME varchar(200) NULL,
    PAT_LAST_NAME varchar(200) NULL,
    EMAIL_ADDRESS varchar(255) NULL,
    EMAIL_CONSENT_YN varchar(3),
    MYCHART_STATUS varchar(254) NULL,
    DEATH_DATE datetime2 NULL,
    PAT_ENC_CSN_ID decimal(18,0),
    DEPARTMENT_NAME varchar(254) NULL,
    APPT_TIME datetime2 NULL,
    LOCATION_NAME varchar(200) NULL,
    APPT_STATUS varchar(200) NULL,
);

-- Insert test data
'''
)

HOSPITALS_TO_DEPARTMENTS = {}
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'] = []
HOSPITALS_TO_DEPARTMENTS['Toronto General Hospital'].append("TG-EMERGENCY")
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
HOSPITALS_TO_DEPARTMENTS['Toronto Western Hospital'].append("TW-EMERGENCY")
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
# PMH-Acute Care
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-DAY ONCOLOGY/AWA")
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-URGENT CARE")
HOSPITALS_TO_DEPARTMENTS['Princess Margaret Cancer Centre'].append("PM-RADIATION NURSING CLINIC")

def convertToSqlType(insertion_values):
    converted_values = {}
    for key in insertion_values:
        if type(insertion_values[key]) == str:
            converted_values[key] = "'" + insertion_values[key] + "'"
        elif insertion_values[key] == None:
            converted_values[key] = "NULL"
        else:
            converted_values[key] = insertion_values[key]
    return converted_values

# Insert test data
for i in range(args.n):
    if i % 100 == 0:
        args.file.write("INSERT INTO [path].[V_PatientExperienceSurveySamplingTest]")
        args.file.write("\t(PAT_ENC_CSN_ID, PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, APPT_TIME, DEPARTMENT_NAME, LOCATION_NAME, EMAIL_CONSENT_YN, [MYCHART_STATUS], DEATH_DATE, APPT_STATUS)\n")
        args.file.write("\tVALUES\n")
    insertion_values = {}

    #PAT_MRN
    mrn = random.randint(0, 9999999)
    insertion_values['PAT_MRN'] = mrn

    # PAT_FIRST_NAME
    first_name = "".join([random.choice(string.ascii_lowercase) for n in range(random.randint(5, 10))])
    insertion_values['PAT_FIRST_NAME'] = first_name

    # PAT_LAST_NAME
    last_name = "".join([random.choice(string.ascii_lowercase) for n in range(random.randint(5, 10))])
    insertion_values['PAT_LAST_NAME'] = last_name

    # EMAIL_ADDRESS
    email = 'test' + str(mrn) + '@test.com'
    insertion_values['EMAIL_ADDRESS'] = email

    # APPT_TIME
    appt_time = args.basedate - datetime.timedelta(seconds=random.randint(0, args.time_spread_seconds))
    appt_time_str = appt_time.strftime('%Y-%m-%d %H:%M:%S')
    insertion_values['APPT_TIME'] = appt_time_str
    potential_death_time = appt_time + datetime.timedelta(seconds=random.randint(0, 5*24*60*60))
    potential_death_time_str = potential_death_time.strftime('%Y-%m-%d %H:%M:%S')

    # LOCATION_NAME
    location = random.choice(list(HOSPITALS_TO_DEPARTMENTS.keys()))
    insertion_values['LOCATION_NAME'] = location

    # DEPARTMENT_NAME
    dept_name = random.choice(HOSPITALS_TO_DEPARTMENTS[location])
    insertion_values['DEPARTMENT_NAME'] = dept_name

    # EMAIL_CONSENT_YN
    email_consent_yn = random.choices(['Yes', 'No'], [10, 1])[0]
    insertion_values['EMAIL_CONSENT_YN'] = email_consent_yn

    # MYCHART STATUS
    insertion_values['MYCHART_STATUS'] = random.choices([None, 'Activating', 'Activated'], [2, 1, 3])[0]

    # DEATH_DATE
    insertion_values['DEATH_DATE'] = random.choices([None, potential_death_time_str], [20, 1])[0]

    # APPT_STATUS
    insertion_values['APPT_STATUS'] = random.choices(['Arrived', 'Completed', 'Left without seen', 'Scheduled', 'No Show', 'Canceled'], [1, 5, 1, 1, 1, 1])[0]

    # Identifier columns
    insertion_values['PAT_ENC_CSN_ID'] = i

    args.file.write("\t({PAT_ENC_CSN_ID:07d}, {PAT_MRN:07d}, {PAT_FIRST_NAME}, {PAT_LAST_NAME}, {EMAIL_ADDRESS}, {APPT_TIME}, {DEPARTMENT_NAME}, {LOCATION_NAME}, {EMAIL_CONSENT_YN}, {MYCHART_STATUS}, {DEATH_DATE}, {APPT_STATUS})".format(**convertToSqlType(insertion_values)))
    if (i != args.n - 1) and (i % 100 != 99):
        args.file.write(",")
    args.file.write("\n")

args.file.close()
