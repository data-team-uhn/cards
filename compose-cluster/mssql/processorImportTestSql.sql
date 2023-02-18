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
);

-- Insert test data
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000001, 1, 'A', 'Smith', 'testA@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000002, 2, 'B', 'Smith', 'testB@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'No', CAST(GETDATE()-1 AS DATE), CAST(GETDATE()-1 AS DATE), 'Deceased', 'regular', 'No', 12);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000003, 3, 'C', 'Smith', 'testC@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Deceased', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000004, 4, 'D', 'Smith', 'testD@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), CAST(GETDATE()-1 AS DATE), 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000005, 5, 'E', 'Smith', 'testE@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'ALC-AB', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000006, 6, 'F', 'Smith', 'testF@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'ALC 123', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000007, 7, 'G', 'Smith', 'testG@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto Rehab - Bickle Centre', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000008, 8, 'H', 'Smith', 'testH@test.com', '2022-09-21T11:16:20.136Z', 'TG-EMERGENCY', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000009, 9, 'I', 'Smith', 'testI@test.com', '2022-09-21T11:16:20.136Z', 'TW-EMERGENCY', 'Toronto Western Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000010, 10, 'J', 'Smith', 'testJ@test.com', '2022-09-21T11:16:20.136Z', 'TG-EMERGENCY', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'Yes', 3);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000011, 11, 'K', 'Smith', 'testK@test.com', '2022-09-21T11:16:20.136Z', 'TG-EMERGENCY', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'Yes', 5);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000012, 12, 'L', 'Smith', 'testL@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 3);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000013, 13, 'M', 'Smith', 'testM@test.com', '2022-09-21T11:16:20.136Z', 'TG-8ES PSYCHIATRY', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000014, 14, 'N', 'Smith', 'testN@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'Yes', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000015, 15, 'O', 'Smith', 'testO@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000016, 16, 'P', 'Smith', 'testP@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'No', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000017, 17, 'Q', 'Smith', '', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS, [MYCHART STATUS])
    VALUES
    (0000018, 18, 'R', 'Smith', 'testR@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'No', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8, 'Activated');

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS, [MYCHART STATUS])
    VALUES
    (0000019, 19, 'S', 'Smith', '', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'No', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8, 'Activated');

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, HOSP_ADMISSION_DTTM)
    VALUES
    (0000020, 20, 'T', 'Smith', 'testT@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'Yes', '2022-09-20T11:16:20.136Z');

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, HOSP_ADMISSION_DTTM)
    VALUES
    (0000021, 21,'U', 'Smith', 'testU@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'Yes', '2022-09-10T11:16:20.136Z');

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000022, 22, 'V', 'Smith', 'testV@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Not Arrived', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000023, 23, 'W', 'Smith', 'testW@test.com', '2022-09-21T11:16:20.136Z', 'UC-5 SOUTH IP', 'Toronto Rehab - Bickle Centre', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000024, 24, 'X', 'Smith', 'testX@test.com', '2022-09-21T11:16:20.136Z', 'BC-5B NORTH IP', 'Toronto Rehab - Bickle Centre', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS, DX_NAME)
    VALUES
    (0000025, 25, 'Y', 'Smith', 'testY@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8, 'Adjustment disorders, unspecified');

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000026, 26, 'Z', 'Smith', 'testZ@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'IP Transfer', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000027, 27, 'AA', 'Smith', 'testAA@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (0000028, 28, 'AB', 'Smith', 'testAB@test.com', '2022-09-21T11:16:20.136Z', 'PM-PALLIATIVE CARE ONCOLOGY CLINIC', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (00000029, 29, 'AC', 'Smith', 'testAC@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Incorrect hospital', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (00000030, 30, 'AD', 'Smith', 'testAD@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto Rehab - Lyndhurst Centre Parent Location', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);

INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS]
    (PAT_MRN, ID, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DEATH_DATE, DISCH_DISPOSITION, LEVEL_OF_CARE, ED_IP_TRANSFER_YN, LENGTH_OF_STAY_DAYS)
    VALUES
    (00000031, 31, 'AE', 'Smith', 'testAE@test.com', '2022-09-21T11:16:20.136Z', 'TG-GENERAL MEDICINE', 'Toronto Rehab - University Centre', 'Yes', CAST(GETDATE()-1 AS DATE), NULL, 'Home', 'regular', 'No', 8);