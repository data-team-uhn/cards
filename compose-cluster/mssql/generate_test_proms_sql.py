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
argparser.add_argument('-n', help='Number of patients discharges to generate [default: 10]', default=10, type=int)
argparser.add_argument('--basedate', help='Date to generate patient discharge entries from [default: today at 8 AM] [format: YYYY-MM-dd]', default=datetime.datetime.today().replace(hour=8, minute=0, second=0, microsecond=0), type=lambda s: datetime.datetime.strptime(s, '%Y-%m-%d'))
argparser.add_argument('--time_spread_minutes', help='Number of minutes after the base date to generate patient discharge entries from [default: 10 hours]', default=600, type=int)
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
IF OBJECT_ID('path.PatientVisitActivity_for_DATA-PRO', 'U') IS NOT NULL
    DROP TABLE [path].[PatientVisitActivity_for_DATA-PRO];

CREATE TABLE [path].[PatientVisitActivity_for_DATA-PRO] (
    PATIENT_ID varchar(18),
    MRN varchar(102) NULL,
    OHIP_NBR varchar(50) NULL,
    FIRST_NAME varchar(200) NULL,
    LAST_NAME varchar(200) NULL,
    DATE_OF_BIRTH datetime2 NULL,
    SEX varchar(50) NULL,
    EMAIL varchar(254) NULL,
    EMAIL_CONSENT varchar(18) NULL,
    MYCHART_STATUS varchar(254) NULL,
    ENCOUNTER_ID decimal(18,0),
    ENCOUNTER_CLINIC_ID decimal(18,0) NULL,
    ENCOUNTER_CLINIC varchar(254) NULL,
    ENCOUNTER_DATE datetime2 NULL,
    ENCOUNTER_STATUS varchar(254) NULL,
    ATTENDING_PROV_ID varchar(18) NULL,
    ATTENDING_PROV_NAME varchar(200) NULL
);

-- Insert test data
'''
)

CLINICS = {
  101001415: "TG-PMCC CARDIAC CLINICS",
  101001291: "TG-PMCC CARDIAC CLINICS DIAGNOSTIC"
}
PROVIDERS = {
  "101": "SMITH, JOHN",
  "102": "DOE, JANE",
  "103": "ADAMS, PATCH"
}

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
    insertion_values = {}

    # Patient details
    mrn = random.randint(1000000, 9999999)
    insertion_values['MRN'] = mrn

    insertion_values['OHIP_NBR'] = random.randint(1000000000, 9999999999)

    first_name = "".join([random.choice(string.ascii_lowercase) for n in range(random.randint(5, 10))])
    insertion_values['FIRST_NAME'] = first_name

    last_name = "".join([random.choice(string.ascii_lowercase) for n in range(random.randint(5, 10))])
    insertion_values['LAST_NAME'] = last_name

    birth_date = datetime.datetime.now() - datetime.timedelta(days=random.randint(1000, 10000))
    birth_date_str = birth_date.strftime('%Y-%m-%d %H:%M:%S')
    insertion_values['DATE_OF_BIRTH'] = birth_date_str

    insertion_values['SEX'] = random.choice(["Male", "Female"])

    # Email
    insertion_values['EMAIL'] = 'test' + str(mrn) + '@test.com'
    insertion_values['EMAIL_CONSENT'] = random.choices(['UHN_EXTERNAL_EMAIL', None], [10, 1])[0]
    insertion_values['MYCHART_STATUS'] = random.choices([None, 'Activating', 'Activated'], [2, 1, 3])[0]

    # Encounter details
    encounter_date = args.basedate + datetime.timedelta(minutes=random.randint(0, args.time_spread_minutes))
    encounter_date_str = encounter_date.strftime('%Y-%m-%d %H:%M:%S')
    insertion_values['ENCOUNTER_DATE'] = encounter_date_str

    insertion_values['ENCOUNTER_STATUS'] = random.choices(['Scheduled', 'Canceled'], [10, 1])[0]

    clinic_id = random.choice(list(CLINICS.keys()))
    insertion_values['ENCOUNTER_CLINIC_ID'] = clinic_id
    insertion_values['ENCOUNTER_CLINIC'] = CLINICS[clinic_id]

    # Provider
    provider_id = random.choice(list(PROVIDERS.keys()))
    insertion_values['ATTENDING_PROV_ID'] = provider_id
    insertion_values['ATTENDING_PROV_NAME'] = PROVIDERS[provider_id]

    # Identifier columns
    insertion_values['PATIENT_ID'] = i
    insertion_values['ENCOUNTER_ID'] = 1000000 + i

    args.file.write("INSERT INTO [path].[PatientVisitActivity_for_DATA-PRO]")
    args.file.write("\t(PATIENT_ID, MRN, OHIP_NBR, FIRST_NAME, LAST_NAME, DATE_OF_BIRTH, SEX, EMAIL, EMAIL_CONSENT, MYCHART_STATUS, ENCOUNTER_ID, ENCOUNTER_CLINIC_ID, ENCOUNTER_CLINIC, ENCOUNTER_DATE, ENCOUNTER_STATUS, ATTENDING_PROV_ID, ATTENDING_PROV_NAME)\n")
    args.file.write("\tVALUES\n")
    args.file.write("\t({PATIENT_ID:07d}, {MRN}, {OHIP_NBR}, {FIRST_NAME}, {LAST_NAME}, {DATE_OF_BIRTH}, {SEX}, {EMAIL}, {EMAIL_CONSENT}, {MYCHART_STATUS}, {ENCOUNTER_ID}, {ENCOUNTER_CLINIC_ID}, {ENCOUNTER_CLINIC}, {ENCOUNTER_DATE}, {ENCOUNTER_STATUS}, {ATTENDING_PROV_ID}, {ATTENDING_PROV_NAME})\n".format(**convertToSqlType(insertion_values)))

args.file.close()
