
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
INSERT INTO [path].[PatientActivity_data_for_PtExpSurveyApp]	(PAT_ENC_CSN_ID, PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, [MYCHART_STATUS], DEATH_DATE, PRIMARY_DX_NAME, DISCH_DISPOSITION, ED_VISIT_YN, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, OP_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS, UHN_ICC_STATUS, UHN_ICC_PATIENT_ELIGIBILITY, PATIENT_CLASS)
	VALUES
	(1000000, 4423979,	'mhblsty',	'xmnjcsdzo',	'test4423979@test.com',	'2024-01-12 00:00:00',	'TG-ES14 General Medicine',	'Toronto General Hospital',	'Yes',	'Activated',	'2024-01-18 00:00:00',	'Pain',	'Home',	'No',	'regular',	'No',	'No', 3, NULL, NULL,	'Inpatient'),
	(1000001, 4423979,	'mhblsty',	'xmnjcsdzo',	'test4423979@test.com',	'2024-01-22 00:00:00',	'TW-5A Fell Pavilion',	'Toronto Western Hospital',	'Yes',	'Activated', NULL,	'Pain',	'Home',	'No',	'regular',	'No',	'No', 5,	'Enrolled',	'New Patient',	'Inpatient'),
	(1000002, 7752445,	'coctyca',	'efiljxdb',	'test7752445@test.com',	'2024-01-18 00:00:00',	'PM-17A Breast, Gyn, GI & GU',	'Princess Margaret Cancer Centre',	'Yes',	'Activated', NULL, NULL,	'Home',	'No',	'regular',	'No',	'No', 14,	'Enrolled', NULL,	'Inpatient'),
	(1000003, 2534735,	'nkmvjvi',	'gsflctam',	'test2534735@test.com',	'2024-01-18 00:00:00',	'PM-15B BMT & Leukemia/Lymphoma',	'Princess Margaret Cancer Centre',	'Yes',	'Activated', NULL,	'Agitation',	'Home',	'No',	'regular',	'No',	'No', 1,	'Enrolled', NULL,	'Inpatient'),
	(1000004, 0148338,	'gqznzifsxq',	'jxtigyvcj',	'test148338@test.com',	'2024-01-18 00:00:00',	'PM-15A Leukemia & Lymphoma Unit',	'Princess Margaret Cancer Centre',	'No',	'Activating', NULL,	'Injury',	'Home',	'No',	'regular',	'Yes',	'No', 5,	'Enrolled',	'New Patient',	'Inpatient'),
	(1000005, 2216377,	'nwlthgospa',	'wjpbcl',	'test2216377@test.com',	'2024-01-18 00:00:00',	'TW-6A Fell Pavilion',	'Toronto Western Hospital',	'Yes',	'Activated', NULL,	'Alcohol withdrawal',	'Home',	'No',	'regular',	'No',	'No', 2, NULL, NULL,	'Inpatient')
