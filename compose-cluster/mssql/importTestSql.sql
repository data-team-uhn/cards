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

-- Create the database + table
IF NOT EXISTS(SELECT * FROM sys.databases WHERE name = 'path')
    CREATE DATABASE path;

USE path;

-- Remove the table if it already exists
IF OBJECT_ID('CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS', 'U') IS NOT NULL 
    DROP TABLE CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS;

CREATE TABLE CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS (
    PAT_MRN varchar(102) NULL,
    EMAIL_ADDRESS varchar(255) NULL,
    ENTRY_TIME datetime2 NULL,
    DISCH_DEPT_NAME varchar(254) NULL,
    EMAIL_CONSENT_YN varchar(3),
    LoadTime datetime2 NULL
);

-- Insert test data
INSERT INTO CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS
    (PAT_MRN, EMAIL_ADDRESS, ENTRY_TIME, DISCH_DEPT_NAME, EMAIL_CONSENT_YN, LoadTime)
    VALUES
    (1234567, 'test@test.com', '2022-09-21T11:16:20.136Z', 'TG-EMERGENCY', 'Yes', CAST(GETDATE() AS DATE));

INSERT INTO CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS
    (PAT_MRN, EMAIL_ADDRESS, ENTRY_TIME, DISCH_DEPT_NAME, EMAIL_CONSENT_YN, LoadTime)
    VALUES
    (1234568, 'test2@test.com', '2022-09-21T11:16:20.136Z', 'TG-EMERGENCY', 'No', CAST(GETDATE() AS DATE))

INSERT INTO CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS
    (PAT_MRN, EMAIL_ADDRESS, ENTRY_TIME, DISCH_DEPT_NAME, EMAIL_CONSENT_YN, LoadTime)
    VALUES
    (1234568, 'test@test.com', '2022-09-21T11:16:20.136Z', 'TW-EMERGENCY', 'No', CAST(GETDATE() AS DATE))
