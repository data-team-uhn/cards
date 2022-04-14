//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//

const DICOM_REDACTED_TAGS = [
  '(0008,0020)', //StudyDate
  '(0008,0021)', //SeriesDate
  '(0008,0022)', //AcquisitionDate
  '(0008,0023)', //ContentDate
  '(0008,0024)', //OverlayDate
  '(0008,0025)', //CurveDate
  '(0008,002A)', //AcquisitionDatetime
  '(0008,0030)', //StudyTime
  '(0008,0031)', //SeriesTime
  '(0008,0032)', //AcquisitionTime
  '(0008,0033)', //ContentTime
  '(0008,0034)', //OverlayTime
  '(0008,0035)', //CurveTime
  '(0008,0050)', //AccessionNumber
  '(0008,0080)', //InstitutionName
  '(0008,0081)', //InstitutionAddress
  '(0008,0090)', //ReferringPhysiciansName
  '(0008,0092)', //ReferringPhysiciansAddress
  '(0008,0094)', //ReferringPhysiciansTelephoneNumber
  '(0008,0096)', //ReferringPhysicianIDSequence
  '(0008,1040)', //InstitutionalDepartmentName
  '(0008,1048)', //PhysicianOfRecord
  '(0008,1049)', //PhysicianOfRecordIDSequence
  '(0008,1050)', //PerformingPhysiciansName
  '(0008,1052)', //PerformingPhysicianIDSequence
  '(0008,1060)', //NameOfPhysicianReadingStudy
  '(0008,1062)', //PhysicianReadingStudyIDSequence
  '(0008,1070)', //OperatorsName
  '(0010,0010)', //PatientsName
  '(0010,0020)', //PatientID
  '(0010,0021)', //IssuerOfPatientID
  '(0010,0030)', //PatientsBirthDate
  '(0010,0032)', //PatientsBirthTime
  '(0010,0040)', //PatientsSex
  '(0010,1000)', //OtherPatientIDs
  '(0010,1001)', //OtherPatientNames
  '(0010,1005)', //PatientsBirthName
  '(0010,1010)', //PatientsAge
  '(0010,1040)', //PatientsAddress
  '(0010,1060)', //PatientsMothersBirthName
  '(0010,2150)', //CountryOfResidence
  '(0010,2152)', //RegionOfResidence
  '(0010,2154)', //PatientsTelephoneNumbers
  '(0020,0010)', //StudyID
  '(0038,0300)', //CurrentPatientLocation
  '(0038,0400)', //PatientsInstitutionResidence
  '(0040,A120)', //DateTime
  '(0040,A121)', //Date
  '(0040,A122)', //Time
  '(0040,A123)', //PersonName
];

export default DICOM_REDACTED_TAGS;
