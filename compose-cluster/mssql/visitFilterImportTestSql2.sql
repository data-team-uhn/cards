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
    LoadTime datetime2 NULL,
    DEATH_DATE datetime2 NULL,
    DISCH_DISPOSITION varchar(255) NULL,
    LEVEL_OF_CARE varchar(255) NULL,
    ED_IP_TRANSFER_YN varchar(3) NULL,
    LENGTH_OF_STAY_DAYS varchar(102) NULL,
    HOSP_ADMISSION_DTTM datetime2 NULL,
    [MYCHART STATUS] varchar(255) NULL,
    DX_NAME varchar(1024) NULL,
    ID varchar(255) NULL,
    PAT_ENC_CSN_ID varchar(255) NULL,
);

-- Insert test data
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_ENC_CSN_ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000001, 1, 3001, 'A', 'Smith', 'testA@test.com', CAST(GETDATE()-1 AS DATE), 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_ENC_CSN_ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000003, 3, 3003, 'C', 'Smith', 'testC@test.com', CAST(GETDATE()-1 AS DATE), 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);
