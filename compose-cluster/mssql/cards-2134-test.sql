
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
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (0, 'A', 'Smith', '0@mail.com', CAST(GETDATE()-7 AS DATE), 'TG-ES14 General Medicine', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-7 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 0, 200000000);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (1, 'B', 'Smith', '1@mail.com', CAST(GETDATE()-7 AS DATE), 'TG-ES14 General Medicine', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-7 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 1, 200000001);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (2, 'C', 'Smith', '2@mail.com', CAST(GETDATE()-7 AS DATE), 'TW-3B Fell Pavilion', 'Toronto Western Hospital', 'Yes', CAST(GETDATE()-7 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 2, 200000002);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (3, 'D', 'Smith', '3@mail.com', CAST(GETDATE()-7 AS DATE), 'TW-3B Fell Pavilion', 'Toronto Western Hospital', 'Yes', CAST(GETDATE()-7 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 3, 200000003);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (4, 'E', 'Smith', '4@mail.com', CAST(GETDATE()-7 AS DATE), 'TRI Brain Injury Rehab Inpatient Service', 'Toronto Rehab - University Centre', 'Yes', CAST(GETDATE()-7 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 4, 200000004);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (5, 'F', 'Smith', '5@mail.com', CAST(GETDATE()-7 AS DATE), 'TRI Brain Injury Rehab Inpatient Service', 'Toronto Rehab - University Centre', 'Yes', CAST(GETDATE()-7 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 5, 200000005);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (6, 'G', 'Smith', '6@mail.com', CAST(GETDATE()-7 AS DATE), 'TG-EMERGENCY', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-7 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 6, 200000006);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (7, 'H', 'Smith', '7@mail.com', CAST(GETDATE()-9 AS DATE), 'TG-ES14 General Medicine', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-9 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 7, 200000007);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (8, 'I', 'Smith', '8@mail.com', CAST(GETDATE()-9 AS DATE), 'TG-ES14 General Medicine', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-9 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 8, 200000008);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (9, 'J', 'Smith', '9@mail.com', CAST(GETDATE()-9 AS DATE), 'TW-3B Fell Pavilion', 'Toronto Western Hospital', 'Yes', CAST(GETDATE()-9 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 9, 200000009);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (10, 'K', 'Smith', '10@mail.com', CAST(GETDATE()-9 AS DATE), 'TW-3B Fell Pavilion', 'Toronto Western Hospital', 'Yes', CAST(GETDATE()-9 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 10, 200000010);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (11, 'L', 'Smith', '11@mail.com', CAST(GETDATE()-9 AS DATE), 'TRI Brain Injury Rehab Inpatient Service', 'Toronto Rehab - University Centre', 'Yes', CAST(GETDATE()-9 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 11, 200000011);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (12, 'M', 'Smith', '12@mail.com', CAST(GETDATE()-9 AS DATE), 'TRI Brain Injury Rehab Inpatient Service', 'Toronto Rehab - University Centre', 'Yes', CAST(GETDATE()-9 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 12, 200000012);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (13, 'N', 'Smith', '13@mail.com', CAST(GETDATE()-9 AS DATE), 'TG-EMERGENCY', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-9 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 13, 200000013);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (14, 'O', 'Smith', '14@mail.com', CAST(GETDATE()-11 AS DATE), 'TG-ES14 General Medicine', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-11 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 14, 200000014);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (15, 'P', 'Smith', '15@mail.com', CAST(GETDATE()-11 AS DATE), 'TG-ES14 General Medicine', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-11 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 15, 200000015);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (16, 'Q', 'Smith', '16@mail.com', CAST(GETDATE()-11 AS DATE), 'TW-3B Fell Pavilion', 'Toronto Western Hospital', 'Yes', CAST(GETDATE()-11 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 16, 200000016);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (17, 'R', 'Smith', '17@mail.com', CAST(GETDATE()-11 AS DATE), 'TW-3B Fell Pavilion', 'Toronto Western Hospital', 'Yes', CAST(GETDATE()-11 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 17, 200000017);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (18, 'S', 'Smith', '18@mail.com', CAST(GETDATE()-11 AS DATE), 'TRI Brain Injury Rehab Inpatient Service', 'Toronto Rehab - University Centre', 'Yes', CAST(GETDATE()-11 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 18, 200000018);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (19, 'T', 'Smith', '19@mail.com', CAST(GETDATE()-11 AS DATE), 'TRI Brain Injury Rehab Inpatient Service', 'Toronto Rehab - University Centre', 'Yes', CAST(GETDATE()-11 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 19, 200000019);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (20, 'U', 'Smith', '20@mail.com', CAST(GETDATE()-11 AS DATE), 'TG-EMERGENCY', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-11 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 20, 200000020);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (21, 'V', 'Smith', '21@mail.com', CAST(GETDATE()-5 AS DATE), 'TG-ES14 General Medicine', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-5 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 21, 200000021);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (22, 'W', 'Smith', '22@mail.com', CAST(GETDATE()-5 AS DATE), 'TG-ES14 General Medicine', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-5 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 22, 200000022);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (23, 'X', 'Smith', '23@mail.com', CAST(GETDATE()-5 AS DATE), 'TW-3B Fell Pavilion', 'Toronto Western Hospital', 'Yes', CAST(GETDATE()-5 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 23, 200000023);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (24, 'Y', 'Smith', '24@mail.com', CAST(GETDATE()-5 AS DATE), 'TW-3B Fell Pavilion', 'Toronto Western Hospital', 'Yes', CAST(GETDATE()-5 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 24, 200000024);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (25, 'Z', 'Smith', '25@mail.com', CAST(GETDATE()-5 AS DATE), 'TRI Brain Injury Rehab Inpatient Service', 'Toronto Rehab - University Centre', 'Yes', CAST(GETDATE()-5 AS DATE), 'Home', 'Yes', 'regular', '2022-09-20T11:16:20.136Z', 25, 200000025);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (26, 'Aa', 'Smith', '26@mail.com', CAST(GETDATE()-5 AS DATE), 'TRI Brain Injury Rehab Inpatient Service', 'Toronto Rehab - University Centre', 'Yes', CAST(GETDATE()-5 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 26, 200000026);
INSERT INTO [path].[CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS] (PAT_MRN, PAT_FIRST_NAME, PAT_LAST_NAME, EMAIL_ADDRESS, HOSP_DISCHARGE_DTTM, DISCH_DEPT_NAME, DISCH_LOC_NAME, EMAIL_CONSENT_YN, LoadTime, DISCH_DISPOSITION, ED_IP_TRANSFER_YN, LEVEL_OF_CARE, HOSP_ADMISSION_DTTM, ID, PAT_ENC_CSN_ID) VALUES (27, 'Ab', 'Smith', '27@mail.com', CAST(GETDATE()-5 AS DATE), 'TG-EMERGENCY', 'Toronto General Hospital', 'Yes', CAST(GETDATE()-5 AS DATE), 'Home', 'No', 'regular', '2022-09-20T11:16:20.136Z', 27, 200000027);
