#!/bin/bash
curl -u admin:admin http://localhost:8080/Forms/ -F ":data=@SampleDemographics.csv" -F ":questionnaire=/Questionnaires/Demographics"
