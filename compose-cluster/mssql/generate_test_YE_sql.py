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
IF OBJECT_ID('path.PatientActivity_data_for_PtExpSurveyApp', 'U') IS NOT NULL
    DROP TABLE [path].[PatientActivity_data_for_PtExpSurveyApp];

CREATE TABLE [path].[PatientActivity_data_for_PtExpSurveyApp] (
    ADT_PAT_CLASS_C varchar(66) NULL,
    CITY varchar(50) NULL,
    DEATH_DATE datetime2 NULL,
    DEPARTMENT_ID decimal(18,0) NULL,
    DISCH_DEPT_NAME varchar(254) NULL,
    DISCH_DISPOSITION varchar(254) NULL,
    DISCH_LOC_NAME varchar(200) NULL,
    ED_IP_TRANSFER_YN varchar(3),
    ED_VISIT_YN varchar(3),
    EMAIL_ADDRESS varchar(255) NULL,
    EMAIL_CONSENT_YN varchar(3),
    ENTRY_TIME datetime2 NULL,
    HOSP_ADMISSION_DTTM datetime2 NULL,
    HOSP_DISCHARGE_DTTM datetime2 NULL,
    HSP_DIS_EVENT_ID decimal(18,0) NULL,
    LENGTH_OF_STAY_DAYS int NULL,
    LEVEL_OF_CARE varchar(254) NULL,
    MYCHART_STATUS varchar(254) NULL,
    OP_IP_TRANSFER_YN varchar(3),
    PATIENT_CLASS varchar(254) NULL,
    PAT_ENC_CSN_ID decimal(18,0),
    PAT_FIRST_NAME varchar(200) NULL,
    PAT_LAST_NAME varchar(200) NULL,
    PAT_MRN varchar(102) NULL,
    PRIMARY_DX_ID decimal(18,0) NULL,
    PRIMARY_DX_NAME varchar(200) NULL,
    PRIMARY_ER_COMPLAINT varchar(500) NULL,
    PRINCIPAL_HOSP_PROB_NAME varchar(200) NULL,
    PROVINCE varchar(254) NULL,
    UHN_ICC_PATIENT_ELIGIBILITY varchar(2500) NULL,
    UHN_ICC_STATUS varchar(2500) NULL,
    ZIP varchar(60) NULL
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
args.file.write("INSERT INTO [path].[PatientActivity_data_for_PtExpSurveyApp]")
args.file.write("\t(PAT_ENC_CSN_ID, PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, [MYCHART_STATUS], DEATH_DATE, PRIMARY_DX_NAME, DISCH_DISPOSITION, ED_VISIT_YN, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, OP_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS, UHN_ICC_STATUS, UHN_ICC_PATIENT_ELIGIBILITY, PATIENT_CLASS)\n")
args.file.write("\tVALUES\n")
for i in range(args.n):
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

    # HOSP_DISCHARGE_DTTM
    discharge_time = args.basedate - datetime.timedelta(seconds=random.randint(0, args.time_spread_seconds))
    discharge_time_str = discharge_time.strftime('%Y-%m-%d %H:%M:%S')
    insertion_values['HOSP_DISCHARGE_DTTM'] = discharge_time_str
    potential_death_time = discharge_time + datetime.timedelta(seconds=random.randint(0, 5*24*60*60))
    potential_death_time_str = potential_death_time.strftime('%Y-%m-%d %H:%M:%S')

    # DISCH_LOC_NAME
    disch_dept_location = random.choice(list(HOSPITALS_TO_DEPARTMENTS.keys()))
    insertion_values['DISCH_LOC_NAME'] = disch_dept_location

    # DISCH_DEPT_NAME
    disch_dept_name = random.choice(HOSPITALS_TO_DEPARTMENTS[disch_dept_location])
    insertion_values['DISCH_DEPT_NAME'] = disch_dept_name

    # EMAIL_CONSENT_YN
    email_consent_yn = random.choices(['Yes', 'No'], [10, 1])[0]
    insertion_values['EMAIL_CONSENT_YN'] = email_consent_yn

    # MYCHART STATUS
    insertion_values['MYCHART_STATUS'] = random.choices([None, 'Activating', 'Activated'], [2, 1, 3])[0]

    # DISCH_DISPOSITION
    insertion_values['DISCH_DISPOSITION'] = random.choices(['Home', 'Deceased'], [20, 1])[0]

    # DEATH_DATE
    insertion_values['DEATH_DATE'] = random.choices([None, potential_death_time_str], [20, 1])[0]

    # DX
    insertion_values['PRIMARY_DX_NAME'] = random.choices([None, "Agitation", "Alcohol withdrawal", "Pain", "Injury"], [1, 1, 1, 1, 1])[0]

    # LEVEL_OF_CARE
    insertion_values['LEVEL_OF_CARE'] = random.choices(['regular', 'ALC-AB', 'ALC 123'], [10, 1, 1])[0]

    # Transfers
    insertion_values['ED_VISIT_YN'] = "Yes" if disch_dept_name in ["TW-EMERGENCY", "TG-EMERGENCY"] else "No"
    insertion_values['ED_IP_TRANSFER_YN'] = random.choices(['Yes', 'No'], [1, 5])[0]
    insertion_values['OP_IP_TRANSFER_YN'] = random.choices(['Yes', 'No'], [1, 5])[0]


    # LENGTH_OF_STAY_DAYS
    insertion_values['LENGTH_OF_STAY_DAYS'] = random.randint(1, 15)

    # Integrated Care eligibility
    insertion_values['UHN_ICC_STATUS'] = random.choices([None, 'Enrolled'], [1, 2])[0]
    insertion_values['UHN_ICC_PATIENT_ELIGIBILITY'] = random.choices([None, 'New Patient'], [1, 2])[0]

    # Identifier columns
    insertion_values['PAT_ENC_CSN_ID'] = i

    args.file.write("\t({PAT_ENC_CSN_ID:07d}, {PAT_MRN:07d}, {PAT_FIRST_NAME}, {PAT_LAST_NAME}, {EMAIL_ADDRESS}, {HOSP_DISCHARGE_DTTM}, {DISCH_DEPT_NAME}, {DISCH_LOC_NAME}, {EMAIL_CONSENT_YN}, {MYCHART_STATUS}, {DEATH_DATE}, {PRIMARY_DX_NAME}, {DISCH_DISPOSITION}, {ED_VISIT_YN}, {LEVEL_OF_CARE}, {ED_IP_TRANSFER_YN}, {OP_IP_TRANSFER_YN}, {LENGTH_OF_STAY_DAYS}, {UHN_ICC_STATUS}, {UHN_ICC_PATIENT_ELIGIBILITY}, 'Inpatient')".format(**convertToSqlType(insertion_values)))
    if i < args.n - 1:
        args.file.write(",")
    args.file.write("\n")

args.file.close()
