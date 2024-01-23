
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
IF OBJECT_ID('path.PatientActivity_Outpatient_PMCC_data_for_PtExpSurvey', 'U') IS NOT NULL
    DROP TABLE [path].[PatientActivity_Outpatient_PMCC_data_for_PtExpSurvey];

CREATE TABLE [path].[PatientActivity_Outpatient_PMCC_data_for_PtExpSurvey] (
    ADT_PAT_CLASS_C varchar(66) NULL,
    DEATH_DATE datetime2 NULL,
    DEPARTMENT_ID decimal(18,0) NULL,
    DISCH_DEPT_NAME varchar(254) NULL,
    DISCH_DISPOSITION varchar(254) NULL,
    DISCH_LOC_NAME varchar(200) NULL,
    EMAIL_ADDRESS varchar(255) NULL,
    EMAIL_CONSENT_YN varchar(3),
    ENTRY_TIME datetime2 NULL,
    HOSP_ADMISSION_DTTM datetime2 NULL,
    HOSP_DISCHARGE_DTTM datetime2 NULL,
    HSP_DIS_EVENT_ID decimal(18,0) NULL,
    MYCHART_STATUS varchar(254) NULL,
    PATIENT_CLASS varchar(254) NULL,
    PAT_ENC_CSN_ID decimal(18,0),
    PAT_FIRST_NAME varchar(200) NULL,
    PAT_LAST_NAME varchar(200) NULL,
    PAT_MRN varchar(102) NULL
);

-- Insert test data
INSERT INTO [path].[PatientActivity_Outpatient_PMCC_data_for_PtExpSurvey]	(PAT_ENC_CSN_ID, PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, MYCHART_STATUS, DEATH_DATE, DISCH_DISPOSITION, PATIENT_CLASS)
	VALUES
	(2000000, 4423979,	'mhblsty',	'xmnjcsdzo',	'test4423979@test.com',	'2024-01-14 00:00:00',	'PM-15B BMT & Leukemia/Lymphoma',	'Princess Margaret Cancer Centre',	'Yes',	'Activated', NULL,	'Home',	'Outpatient'),
	(2000001, 4423979,	'mhblsty',	'xmnjcsdzo',	'test4423979@test.com',	'2024-01-20 00:00:00',	'PM-15B BMT & Leukemia/Lymphoma',	'Princess Margaret Cancer Centre',	'Yes',	'Activated', NULL,	'Home',	'Outpatient'),
	(2000002, 9711504,	'rstkjhjga',	'halzj',	'test9711504@test.com',	'2024-01-19 00:00:00',	'TG-ES14 General Medicine',	'Toronto General Hospital',	'Yes',	'Activated', NULL,	'Home',	'Inpatient'),
	(2000003, 0446849,	'unyuxorgco',	'iyojg',	'test446849@test.com',	'2024-01-19 00:00:00',	'TG-ES 6 General Medicine',	'Toronto General Hospital',	'No',	'Activated', NULL,	'Home',	'Outpatient'),
	(2000004, 9072422,	'wtzxle',	'jqhgqhcpt',	'test9072422@test.com',	'2024-01-22 00:00:00',	'PM-15C Auto Transplant Unit',	'Princess Margaret Cancer Centre',	'No', NULL, NULL,	'Home',	'Outpatient'),
	(2000005, 5028087,	'yqevgpgzbj',	'elnuf',	'test5028087@test.com',	'2024-01-20 00:00:00',	'TG-5MB Cardiology',	'Toronto General Hospital',	'Yes', NULL, NULL,	'Home', NULL),
	(2000006, 6468563,	'copqqvcont',	'ohcnbvyy',	'test6468563@test.com',	'2024-01-23 00:00:00',	'PM-14A Leukemia/Lymphoma',	'Princess Margaret Cancer Centre',	'Yes',	'Activated', NULL,	'Home',	'Outpatient')

